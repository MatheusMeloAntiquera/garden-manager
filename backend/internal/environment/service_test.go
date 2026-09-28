package environment

import (
	"context"
	"errors"
	"slices"
	"strings"
	"sync"
	"testing"
	"time"

	validatorpkg "github.com/go-playground/validator/v10"
	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// fakeRepository é uma implementação em memória de Repository, usada para
// testar o service sem depender de um Postgres real.
type fakeRepository struct {
	mu   sync.Mutex
	byID map[uuid.UUID]domain.Environment
}

func newFakeRepository() *fakeRepository {
	return &fakeRepository{byID: make(map[uuid.UUID]domain.Environment)}
}

func (f *fakeRepository) Create(_ context.Context, env domain.Environment) (domain.Environment, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	env.ID = uuid.New()
	now := time.Now()
	env.CreatedAt = now
	env.UpdatedAt = now
	f.byID[env.ID] = env

	return env, nil
}

func (f *fakeRepository) FindByID(_ context.Context, userID, id uuid.UUID) (domain.Environment, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	env, ok := f.byID[id]
	if !ok || env.UserID != userID {
		return domain.Environment{}, ErrEnvironmentNotFound
	}
	return env, nil
}

func (f *fakeRepository) List(_ context.Context, userID uuid.UUID, active *bool, limit, offset int) ([]domain.Environment, int, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	var matched []domain.Environment
	for _, env := range f.byID {
		if env.UserID != userID {
			continue
		}
		if active != nil && env.Active != *active {
			continue
		}
		matched = append(matched, env)
	}

	slices.SortFunc(matched, func(a, b domain.Environment) int {
		return strings.Compare(a.Name, b.Name)
	})

	total := len(matched)
	start := min(offset, total)
	end := min(offset+limit, total)

	return matched[start:end], total, nil
}

func (f *fakeRepository) Update(_ context.Context, env domain.Environment) (domain.Environment, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	existing, ok := f.byID[env.ID]
	if !ok || existing.UserID != env.UserID {
		return domain.Environment{}, ErrEnvironmentNotFound
	}

	existing.Name = env.Name
	existing.Notes = env.Notes
	existing.Active = env.Active
	existing.UpdatedAt = time.Now()
	f.byID[env.ID] = existing

	return existing, nil
}

func (f *fakeRepository) Delete(_ context.Context, userID, id uuid.UUID) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	env, ok := f.byID[id]
	if !ok || env.UserID != userID {
		return ErrEnvironmentNotFound
	}
	delete(f.byID, id)

	return nil
}

func newTestService(t *testing.T) Service {
	t.Helper()

	v, err := validator.New()
	if err != nil {
		t.Fatalf("validator.New retornou erro: %v", err)
	}

	return NewService(newFakeRepository(), v)
}

func createTestEnvironment(t *testing.T, svc Service, userID uuid.UUID, name string, active bool) EnvironmentResponse {
	t.Helper()

	env, err := svc.Create(context.Background(), userID, CreateInput{Name: name, Active: &active})
	if err != nil {
		t.Fatalf("Create retornou erro: %v", err)
	}
	return env
}

func ptr[T any](v T) *T {
	return &v
}

func assertValidationError(t *testing.T, err error) {
	t.Helper()

	var validationErrs validatorpkg.ValidationErrors
	if !errors.As(err, &validationErrs) {
		t.Fatalf("esperava erro de validação, obteve: %v", err)
	}
}

func TestCreateDefaultsAndNormalization(t *testing.T) {
	svc := newTestService(t)

	env, err := svc.Create(context.Background(), uuid.New(), CreateInput{
		Name:  "  Cozinha  ",
		Notes: ptr("   "),
	})
	if err != nil {
		t.Fatalf("Create retornou erro: %v", err)
	}

	if env.Name != "Cozinha" {
		t.Errorf("esperava nome sem espaços nas pontas, obteve %q", env.Name)
	}
	if !env.Active {
		t.Error("esperava ambiente ativo por padrão")
	}
	if env.Notes != nil {
		t.Errorf("esperava notes nulo para observação em branco, obteve %q", *env.Notes)
	}
}

func TestCreateKeepsNotesAndInactiveStatus(t *testing.T) {
	svc := newTestService(t)

	env, err := svc.Create(context.Background(), uuid.New(), CreateInput{
		Name:   "Área externa",
		Notes:  ptr(" Sol pela manhã "),
		Active: ptr(false),
	})
	if err != nil {
		t.Fatalf("Create retornou erro: %v", err)
	}

	if env.Notes == nil || *env.Notes != "Sol pela manhã" {
		t.Errorf("esperava notes \"Sol pela manhã\", obteve %v", env.Notes)
	}
	if env.Active {
		t.Error("esperava ambiente inativo")
	}
}

func TestCreateValidation(t *testing.T) {
	svc := newTestService(t)
	userID := uuid.New()

	tests := map[string]CreateInput{
		"nome vazio":          {Name: ""},
		"nome só com espaços": {Name: "   "},
		"nome muito longo":    {Name: strings.Repeat("a", 101)},
		"notes muito longo":   {Name: "Sala", Notes: ptr(strings.Repeat("a", 2001))},
	}

	for name, input := range tests {
		t.Run(name, func(t *testing.T) {
			_, err := svc.Create(context.Background(), userID, input)
			assertValidationError(t, err)
		})
	}
}

func TestListOnlyReturnsOwnEnvironments(t *testing.T) {
	svc := newTestService(t)
	owner, other := uuid.New(), uuid.New()

	createTestEnvironment(t, svc, owner, "Sala", true)
	createTestEnvironment(t, svc, owner, "Cozinha", true)
	createTestEnvironment(t, svc, other, "Varanda", true)

	list, err := svc.List(context.Background(), owner, ListInput{})
	if err != nil {
		t.Fatalf("List retornou erro: %v", err)
	}

	if list.Total != 2 || len(list.Data) != 2 {
		t.Fatalf("esperava 2 ambientes, obteve total=%d len=%d", list.Total, len(list.Data))
	}
	if list.Data[0].Name != "Cozinha" || list.Data[1].Name != "Sala" {
		t.Errorf("esperava ordenação por nome, obteve %q, %q", list.Data[0].Name, list.Data[1].Name)
	}
	if list.Page != 1 || list.PageSize != DefaultPageSize {
		t.Errorf("esperava page=1 page_size=%d, obteve page=%d page_size=%d", DefaultPageSize, list.Page, list.PageSize)
	}
}

func TestListFiltersByActive(t *testing.T) {
	svc := newTestService(t)
	userID := uuid.New()

	createTestEnvironment(t, svc, userID, "Sala", true)
	createTestEnvironment(t, svc, userID, "Garagem", false)

	list, err := svc.List(context.Background(), userID, ListInput{Active: ptr(false)})
	if err != nil {
		t.Fatalf("List retornou erro: %v", err)
	}

	if list.Total != 1 || list.Data[0].Name != "Garagem" {
		t.Fatalf("esperava apenas o ambiente inativo, obteve %+v", list.Data)
	}
}

func TestListPagination(t *testing.T) {
	svc := newTestService(t)
	userID := uuid.New()

	for _, name := range []string{"A", "B", "C"} {
		createTestEnvironment(t, svc, userID, name, true)
	}

	list, err := svc.List(context.Background(), userID, ListInput{Page: 2, PageSize: 2})
	if err != nil {
		t.Fatalf("List retornou erro: %v", err)
	}

	if list.Total != 3 {
		t.Errorf("esperava total=3, obteve %d", list.Total)
	}
	if len(list.Data) != 1 || list.Data[0].Name != "C" {
		t.Errorf("esperava apenas \"C\" na página 2, obteve %+v", list.Data)
	}
}

func TestOtherUserCannotAccessEnvironment(t *testing.T) {
	svc := newTestService(t)
	ctx := context.Background()
	owner, intruder := uuid.New(), uuid.New()

	env := createTestEnvironment(t, svc, owner, "Sala", true)

	if _, err := svc.Get(ctx, intruder, env.ID); !errors.Is(err, ErrEnvironmentNotFound) {
		t.Errorf("Get: esperava ErrEnvironmentNotFound, obteve: %v", err)
	}

	_, err := svc.Update(ctx, intruder, env.ID, UpdateInput{Name: "Invadido", Active: ptr(true)})
	if !errors.Is(err, ErrEnvironmentNotFound) {
		t.Errorf("Update: esperava ErrEnvironmentNotFound, obteve: %v", err)
	}

	if err := svc.Delete(ctx, intruder, env.ID); !errors.Is(err, ErrEnvironmentNotFound) {
		t.Errorf("Delete: esperava ErrEnvironmentNotFound, obteve: %v", err)
	}

	// O ambiente continua intacto para o dono.
	got, err := svc.Get(ctx, owner, env.ID)
	if err != nil {
		t.Fatalf("Get do dono retornou erro: %v", err)
	}
	if got.Name != "Sala" {
		t.Errorf("esperava nome \"Sala\", obteve %q", got.Name)
	}
}

func TestUpdateReplacesFields(t *testing.T) {
	svc := newTestService(t)
	ctx := context.Background()
	userID := uuid.New()

	env := createTestEnvironment(t, svc, userID, "Sala", true)

	updated, err := svc.Update(ctx, userID, env.ID, UpdateInput{
		Name:   " Sala de estar ",
		Notes:  ptr("Janela voltada ao norte"),
		Active: ptr(false),
	})
	if err != nil {
		t.Fatalf("Update retornou erro: %v", err)
	}

	if updated.Name != "Sala de estar" || updated.Active {
		t.Errorf("campos não atualizados: %+v", updated)
	}
	if updated.Notes == nil || *updated.Notes != "Janela voltada ao norte" {
		t.Errorf("esperava notes atualizado, obteve %v", updated.Notes)
	}
}

func TestUpdateRequiresActive(t *testing.T) {
	svc := newTestService(t)
	userID := uuid.New()

	env := createTestEnvironment(t, svc, userID, "Sala", true)

	_, err := svc.Update(context.Background(), userID, env.ID, UpdateInput{Name: "Sala"})
	assertValidationError(t, err)
}

func TestDeleteRemovesEnvironment(t *testing.T) {
	svc := newTestService(t)
	ctx := context.Background()
	userID := uuid.New()

	env := createTestEnvironment(t, svc, userID, "Sala", true)

	if err := svc.Delete(ctx, userID, env.ID); err != nil {
		t.Fatalf("Delete retornou erro: %v", err)
	}

	if _, err := svc.Get(ctx, userID, env.ID); !errors.Is(err, ErrEnvironmentNotFound) {
		t.Fatalf("esperava ErrEnvironmentNotFound após exclusão, obteve: %v", err)
	}

	if err := svc.Delete(ctx, userID, env.ID); !errors.Is(err, ErrEnvironmentNotFound) {
		t.Fatalf("esperava ErrEnvironmentNotFound ao excluir de novo, obteve: %v", err)
	}
}
