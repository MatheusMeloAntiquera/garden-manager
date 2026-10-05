package environment

import (
	"context"
	"encoding/json"
	"errors"
	"slices"
	"strings"
	"sync"
	"testing"
	"time"

	validatorpkg "github.com/go-playground/validator/v10"
	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// fakeRepository é uma implementação em memória de Repository, usada para
// testar o service sem depender de um Postgres real. plantCounts faz o papel
// da contagem de plantas ativas que o Postgres calcula na consulta.
type fakeRepository struct {
	mu          sync.Mutex
	byID        map[uuid.UUID]domain.Environment
	plantCounts map[uuid.UUID]int
}

func newFakeRepository() *fakeRepository {
	return &fakeRepository{
		byID:        make(map[uuid.UUID]domain.Environment),
		plantCounts: make(map[uuid.UUID]int),
	}
}

func (f *fakeRepository) view(env domain.Environment) EnvironmentView {
	return EnvironmentView{Environment: env, PlantCount: f.plantCounts[env.ID]}
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

func (f *fakeRepository) FindViewByID(ctx context.Context, userID, id uuid.UUID) (EnvironmentView, error) {
	env, err := f.FindByID(ctx, userID, id)
	if err != nil {
		return EnvironmentView{}, err
	}

	f.mu.Lock()
	defer f.mu.Unlock()
	return f.view(env), nil
}

func (f *fakeRepository) List(_ context.Context, userID uuid.UUID, active *bool, limit, offset int) ([]EnvironmentView, int, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	var matched []EnvironmentView
	for _, env := range f.byID {
		if env.UserID != userID {
			continue
		}
		if active != nil && env.Active != *active {
			continue
		}
		matched = append(matched, f.view(env))
	}

	slices.SortFunc(matched, func(a, b EnvironmentView) int {
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

	svc, _ := newTestServiceWithRepo(t)
	return svc
}

// newTestServiceWithRepo devolve também o fake, para os testes que precisam
// preparar dados que só o banco calcularia, como a contagem de plantas.
func newTestServiceWithRepo(t *testing.T) (Service, *fakeRepository) {
	t.Helper()

	v, err := validator.New()
	if err != nil {
		t.Fatalf("validator.New retornou erro: %v", err)
	}

	repo := newFakeRepository()
	return NewService(repo, v), repo
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

func TestCreateRespondsWithZeroPlants(t *testing.T) {
	svc := newTestService(t)

	env := createTestEnvironment(t, svc, uuid.New(), "Sala", true)

	if env.PlantCount != 0 {
		t.Errorf("esperava plant_count=0 em ambiente novo, obteve %d", env.PlantCount)
	}
}

func TestReadsExposePlantCount(t *testing.T) {
	svc, repo := newTestServiceWithRepo(t)
	ctx := context.Background()
	userID := uuid.New()

	sala := createTestEnvironment(t, svc, userID, "Sala", true)
	repo.plantCounts[sala.ID] = 3

	got, err := svc.Get(ctx, userID, sala.ID)
	if err != nil {
		t.Fatalf("Get retornou erro: %v", err)
	}
	if got.PlantCount != 3 {
		t.Errorf("Get: esperava plant_count=3, obteve %d", got.PlantCount)
	}

	list, err := svc.List(ctx, userID, ListInput{})
	if err != nil {
		t.Fatalf("List retornou erro: %v", err)
	}
	if len(list.Data) != 1 || list.Data[0].PlantCount != 3 {
		t.Errorf("List: esperava um ambiente com plant_count=3, obteve %+v", list.Data)
	}

	updated, err := svc.Update(ctx, userID, sala.ID, UpdateInput{Name: "Sala de estar", Active: ptr(true)})
	if err != nil {
		t.Fatalf("Update retornou erro: %v", err)
	}
	if updated.PlantCount != 3 || updated.Name != "Sala de estar" {
		t.Errorf("Update: esperava o nome novo e plant_count=3, obteve %+v", updated)
	}
}

func TestResponseJSONHasPlantCount(t *testing.T) {
	resp := NewEnvironmentResponse(EnvironmentView{
		Environment: domain.Environment{ID: uuid.New(), Name: "Sala", Active: true},
		PlantCount:  3,
	})

	body, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("json.Marshal retornou erro: %v", err)
	}
	if !strings.Contains(string(body), `"plant_count":3`) {
		t.Errorf("esperava o campo plant_count no JSON, obteve %s", body)
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
	if list.Page != 1 || list.PageSize != pagination.DefaultPageSize {
		t.Errorf("esperava page=1 page_size=%d, obteve page=%d page_size=%d", pagination.DefaultPageSize, list.Page, list.PageSize)
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

	list, err := svc.List(context.Background(), userID, ListInput{Params: pagination.Params{Page: 2, PageSize: 2}})
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
