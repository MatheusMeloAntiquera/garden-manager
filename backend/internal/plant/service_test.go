package plant

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
	"github.com/matheusantiquera/garden-manager/backend/internal/environment"
	"github.com/matheusantiquera/garden-manager/backend/internal/species"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// fakeEnvironments é um EnvironmentFinder em memória.
type fakeEnvironments struct {
	byID map[uuid.UUID]domain.Environment
}

func (f *fakeEnvironments) FindByID(_ context.Context, userID, id uuid.UUID) (domain.Environment, error) {
	env, ok := f.byID[id]
	if !ok || env.UserID != userID {
		return domain.Environment{}, environment.ErrEnvironmentNotFound
	}
	return env, nil
}

// fakeSpecies é um SpeciesFinder em memória.
type fakeSpecies struct {
	byID map[uuid.UUID]domain.Species
}

func (f *fakeSpecies) FindByID(_ context.Context, id uuid.UUID) (domain.Species, error) {
	s, ok := f.byID[id]
	if !ok {
		return domain.Species{}, species.ErrSpeciesNotFound
	}
	return s, nil
}

// fakeRepository é uma implementação em memória de Repository, usada para
// testar o service sem depender de um Postgres real. Monta o resumo da espécie
// e do ambiente como o join do repositório real faz.
type fakeRepository struct {
	mu           sync.Mutex
	byID         map[uuid.UUID]domain.Plant
	environments *fakeEnvironments
	species      *fakeSpecies
}

func (f *fakeRepository) hydrate(p domain.Plant) domain.Plant {
	p.Species, p.Environment = nil, nil

	if p.SpeciesID != nil {
		s := f.species.byID[*p.SpeciesID]
		p.Species = &domain.PlantSpecies{ID: s.ID, ScientificName: s.ScientificName, CommonName: s.PrimaryName()}
	}
	if p.EnvironmentID != nil {
		if e, ok := f.environments.byID[*p.EnvironmentID]; ok {
			p.Environment = &domain.PlantEnvironment{ID: e.ID, Name: e.Name}
		}
	}

	return p
}

func (f *fakeRepository) Create(_ context.Context, plant domain.Plant) (domain.Plant, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	plant.ID = uuid.New()
	now := time.Now()
	plant.CreatedAt, plant.UpdatedAt = now, now
	f.byID[plant.ID] = plant

	return f.hydrate(plant), nil
}

func (f *fakeRepository) FindByID(_ context.Context, userID, id uuid.UUID) (domain.Plant, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	p, ok := f.byID[id]
	if !ok || p.UserID != userID {
		return domain.Plant{}, ErrPlantNotFound
	}
	return f.hydrate(p), nil
}

func (f *fakeRepository) List(_ context.Context, userID uuid.UUID, environmentID, speciesID *uuid.UUID, active *bool, limit, offset int) ([]domain.Plant, int, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	var matched []domain.Plant
	for _, p := range f.byID {
		switch {
		case p.UserID != userID:
		case environmentID != nil && (p.EnvironmentID == nil || *p.EnvironmentID != *environmentID):
		case speciesID != nil && (p.SpeciesID == nil || *p.SpeciesID != *speciesID):
		case active != nil && p.Active != *active:
		default:
			matched = append(matched, f.hydrate(p))
		}
	}

	slices.SortFunc(matched, func(a, b domain.Plant) int {
		return strings.Compare(strings.ToLower(DisplayName(a)), strings.ToLower(DisplayName(b)))
	})

	total := len(matched)
	start := min(offset, total)
	end := min(offset+limit, total)

	return matched[start:end], total, nil
}

func (f *fakeRepository) Update(_ context.Context, plant domain.Plant) (domain.Plant, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	existing, ok := f.byID[plant.ID]
	if !ok || existing.UserID != plant.UserID {
		return domain.Plant{}, ErrPlantNotFound
	}

	existing.SpeciesID = plant.SpeciesID
	existing.EnvironmentID = plant.EnvironmentID
	existing.Nickname = plant.Nickname
	existing.Notes = plant.Notes
	existing.Active = plant.Active
	existing.UpdatedAt = time.Now()
	f.byID[plant.ID] = existing

	return f.hydrate(existing), nil
}

func (f *fakeRepository) Delete(_ context.Context, userID, id uuid.UUID) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	p, ok := f.byID[id]
	if !ok || p.UserID != userID {
		return ErrPlantNotFound
	}
	delete(f.byID, id)

	return nil
}

// testEnv reúne o service e os dados de apoio usados pelos testes.
type testEnv struct {
	svc          Service
	environments *fakeEnvironments
	species      *fakeSpecies
}

func newTestEnv(t *testing.T) *testEnv {
	t.Helper()

	envs := &fakeEnvironments{byID: make(map[uuid.UUID]domain.Environment)}
	specs := &fakeSpecies{byID: make(map[uuid.UUID]domain.Species)}
	repo := &fakeRepository{byID: make(map[uuid.UUID]domain.Plant), environments: envs, species: specs}

	v, err := validator.New()
	if err != nil {
		t.Fatalf("validator.New retornou erro: %v", err)
	}

	return &testEnv{svc: NewService(repo, envs, specs, v), environments: envs, species: specs}
}

func (e *testEnv) addEnvironment(userID uuid.UUID, name string) uuid.UUID {
	id := uuid.New()
	e.environments.byID[id] = domain.Environment{ID: id, UserID: userID, Name: name}
	return id
}

func (e *testEnv) addSpecies(scientificName string, commonNames ...string) uuid.UUID {
	id := uuid.New()
	s := domain.Species{ID: id, ScientificName: scientificName}
	for i, name := range commonNames {
		s.CommonNames = append(s.CommonNames, domain.CommonName{Name: name, IsPrimary: i == 0})
	}
	e.species.byID[id] = s
	return id
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

func createPlant(t *testing.T, svc Service, userID uuid.UUID, input CreateInput) PlantResponse {
	t.Helper()

	p, err := svc.Create(context.Background(), userID, input)
	if err != nil {
		t.Fatalf("Create retornou erro: %v", err)
	}
	return p
}

func TestCreateWithSpeciesOnly(t *testing.T) {
	env := newTestEnv(t)
	userID := uuid.New()
	speciesID := env.addSpecies("Ficus elastica", "falsa-seringueira", "seringueira-de-jardim")

	p := createPlant(t, env.svc, userID, CreateInput{SpeciesID: &speciesID})

	if p.DisplayName != "falsa-seringueira" {
		t.Errorf("esperava display_name com o nome popular principal, obteve %q", p.DisplayName)
	}
	if p.Nickname != nil {
		t.Errorf("esperava nickname nulo, obteve %q", *p.Nickname)
	}
	if p.Species == nil || p.Species.ScientificName != "Ficus elastica" {
		t.Errorf("esperava a espécie no resumo, obteve %+v", p.Species)
	}
	if p.Environment != nil {
		t.Errorf("esperava ambiente nulo, obteve %+v", p.Environment)
	}
	if !p.Active {
		t.Error("esperava planta ativa por padrão")
	}
}

func TestCreateWithNicknameOnlyAndNormalization(t *testing.T) {
	env := newTestEnv(t)

	p := createPlant(t, env.svc, uuid.New(), CreateInput{
		Nickname: ptr("  Planta da vovó  "),
		Notes:    ptr("   "),
		Active:   ptr(false),
	})

	if p.DisplayName != "Planta da vovó" {
		t.Errorf("esperava apelido sem espaços nas pontas, obteve %q", p.DisplayName)
	}
	if p.Species != nil {
		t.Errorf("esperava espécie nula, obteve %+v", p.Species)
	}
	if p.Notes != nil {
		t.Errorf("esperava notes nulo para observação em branco, obteve %q", *p.Notes)
	}
	if p.Active {
		t.Error("esperava planta inativa")
	}
}

func TestCreateDisplayNamePrefersNicknameOverSpecies(t *testing.T) {
	env := newTestEnv(t)
	speciesID := env.addSpecies("Monstera deliciosa", "costela-de-adão")

	p := createPlant(t, env.svc, uuid.New(), CreateInput{SpeciesID: &speciesID, Nickname: ptr("Monstera da sala")})

	if p.DisplayName != "Monstera da sala" {
		t.Errorf("esperava o apelido em display_name, obteve %q", p.DisplayName)
	}
}

func TestCreateValidation(t *testing.T) {
	env := newTestEnv(t)
	userID := uuid.New()
	speciesID := env.addSpecies("Ficus elastica", "falsa-seringueira")

	tests := map[string]CreateInput{
		"sem apelido nem espécie":     {},
		"apelido só com espaços":      {Nickname: ptr("   ")},
		"apelido muito longo":         {Nickname: ptr(strings.Repeat("a", 101))},
		"notes muito longo":           {SpeciesID: &speciesID, Notes: ptr(strings.Repeat("a", 2001))},
		"apelido vazio sem espécie":   {Nickname: ptr("")},
		"só ambiente, sem identidade": {EnvironmentID: ptr(env.addEnvironment(userID, "Sala"))},
	}

	for name, input := range tests {
		t.Run(name, func(t *testing.T) {
			_, err := env.svc.Create(context.Background(), userID, input)
			assertValidationError(t, err)
		})
	}
}

func TestCreateRejectsInvalidReferences(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner, other := uuid.New(), uuid.New()
	othersEnvironment := env.addEnvironment(other, "Varanda de outro usuário")
	missing := uuid.New()

	_, err := env.svc.Create(ctx, owner, CreateInput{Nickname: ptr("Samambaia"), EnvironmentID: &othersEnvironment})
	if !errors.Is(err, ErrInvalidEnvironment) {
		t.Errorf("ambiente de outro usuário: esperava ErrInvalidEnvironment, obteve: %v", err)
	}

	_, err = env.svc.Create(ctx, owner, CreateInput{Nickname: ptr("Samambaia"), EnvironmentID: &missing})
	if !errors.Is(err, ErrInvalidEnvironment) {
		t.Errorf("ambiente inexistente: esperava ErrInvalidEnvironment, obteve: %v", err)
	}

	_, err = env.svc.Create(ctx, owner, CreateInput{SpeciesID: &missing})
	if !errors.Is(err, ErrInvalidSpecies) {
		t.Errorf("espécie inexistente: esperava ErrInvalidSpecies, obteve: %v", err)
	}
}

func TestCreateWithOwnEnvironment(t *testing.T) {
	env := newTestEnv(t)
	userID := uuid.New()
	environmentID := env.addEnvironment(userID, "Sala")

	p := createPlant(t, env.svc, userID, CreateInput{Nickname: ptr("Samambaia"), EnvironmentID: &environmentID})

	if p.Environment == nil || p.Environment.ID != environmentID || p.Environment.Name != "Sala" {
		t.Errorf("esperava o ambiente Sala no resumo, obteve %+v", p.Environment)
	}
}

func TestListFiltersAndOwnership(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner, other := uuid.New(), uuid.New()
	sala := env.addEnvironment(owner, "Sala")
	cozinha := env.addEnvironment(owner, "Cozinha")
	ficus := env.addSpecies("Ficus elastica", "falsa-seringueira")

	createPlant(t, env.svc, owner, CreateInput{Nickname: ptr("Bromélia"), EnvironmentID: &sala})
	createPlant(t, env.svc, owner, CreateInput{Nickname: ptr("Alecrim"), EnvironmentID: &cozinha})
	createPlant(t, env.svc, owner, CreateInput{SpeciesID: &ficus, EnvironmentID: &sala, Active: ptr(false)})
	createPlant(t, env.svc, other, CreateInput{Nickname: ptr("De outro usuário")})

	tests := []struct {
		name  string
		input ListInput
		want  []string
	}{
		{"todas as do usuário, por nome", ListInput{}, []string{"Alecrim", "Bromélia", "falsa-seringueira"}},
		{"por ambiente", ListInput{EnvironmentID: &sala}, []string{"Bromélia", "falsa-seringueira"}},
		{"por espécie", ListInput{SpeciesID: &ficus}, []string{"falsa-seringueira"}},
		{"só inativas", ListInput{Active: ptr(false)}, []string{"falsa-seringueira"}},
		{"ativas na sala", ListInput{EnvironmentID: &sala, Active: ptr(true)}, []string{"Bromélia"}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			list, err := env.svc.List(ctx, owner, tt.input)
			if err != nil {
				t.Fatalf("List retornou erro: %v", err)
			}

			var got []string
			for _, p := range list.Data {
				got = append(got, p.DisplayName)
			}
			if !slices.Equal(got, tt.want) {
				t.Errorf("obteve %v, esperava %v", got, tt.want)
			}
			if list.Total != len(tt.want) {
				t.Errorf("esperava total=%d, obteve %d", len(tt.want), list.Total)
			}
		})
	}
}

func TestListPagination(t *testing.T) {
	env := newTestEnv(t)
	userID := uuid.New()

	for _, name := range []string{"A", "B", "C"} {
		createPlant(t, env.svc, userID, CreateInput{Nickname: ptr(name)})
	}

	list, err := env.svc.List(context.Background(), userID, ListInput{Params: pagination.Params{Page: 2, PageSize: 2}})
	if err != nil {
		t.Fatalf("List retornou erro: %v", err)
	}

	if list.Total != 3 || len(list.Data) != 1 || list.Data[0].DisplayName != "C" {
		t.Errorf("esperava apenas \"C\" na página 2 (total 3), obteve total=%d data=%+v", list.Total, list.Data)
	}
}

func TestOtherUserCannotAccessPlant(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner, intruder := uuid.New(), uuid.New()

	p := createPlant(t, env.svc, owner, CreateInput{Nickname: ptr("Samambaia")})

	if _, err := env.svc.Get(ctx, intruder, p.ID); !errors.Is(err, ErrPlantNotFound) {
		t.Errorf("Get: esperava ErrPlantNotFound, obteve: %v", err)
	}

	_, err := env.svc.Update(ctx, intruder, p.ID, UpdateInput{Nickname: ptr("Invadida"), Active: ptr(true)})
	if !errors.Is(err, ErrPlantNotFound) {
		t.Errorf("Update: esperava ErrPlantNotFound, obteve: %v", err)
	}

	if err := env.svc.Delete(ctx, intruder, p.ID); !errors.Is(err, ErrPlantNotFound) {
		t.Errorf("Delete: esperava ErrPlantNotFound, obteve: %v", err)
	}

	// A planta continua intacta para o dono.
	got, err := env.svc.Get(ctx, owner, p.ID)
	if err != nil {
		t.Fatalf("Get do dono retornou erro: %v", err)
	}
	if got.DisplayName != "Samambaia" {
		t.Errorf("esperava \"Samambaia\", obteve %q", got.DisplayName)
	}
}

func TestUpdateReplacesFields(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	userID := uuid.New()
	sala := env.addEnvironment(userID, "Sala")
	speciesID := env.addSpecies("Monstera deliciosa", "costela-de-adão")

	p := createPlant(t, env.svc, userID, CreateInput{Nickname: ptr("Monstera"), EnvironmentID: &sala, Notes: ptr("Perto da janela")})

	updated, err := env.svc.Update(ctx, userID, p.ID, UpdateInput{
		SpeciesID: &speciesID,
		Nickname:  ptr(" Monstera da sala "),
		Active:    ptr(false),
	})
	if err != nil {
		t.Fatalf("Update retornou erro: %v", err)
	}

	if updated.DisplayName != "Monstera da sala" || updated.Active {
		t.Errorf("campos não atualizados: %+v", updated)
	}
	if updated.Species == nil || updated.Species.ScientificName != "Monstera deliciosa" {
		t.Errorf("esperava a espécie vinculada, obteve %+v", updated.Species)
	}
	if updated.Environment != nil {
		t.Errorf("PUT sem environment_id deveria desvincular o ambiente, obteve %+v", updated.Environment)
	}
	if updated.Notes != nil {
		t.Errorf("PUT sem notes deveria limpar as observações, obteve %q", *updated.Notes)
	}
}

func TestUpdateValidation(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	userID := uuid.New()

	p := createPlant(t, env.svc, userID, CreateInput{Nickname: ptr("Samambaia")})

	_, err := env.svc.Update(ctx, userID, p.ID, UpdateInput{Nickname: ptr("Samambaia")})
	assertValidationError(t, err)

	_, err = env.svc.Update(ctx, userID, p.ID, UpdateInput{Active: ptr(true)})
	assertValidationError(t, err)

	missing := uuid.New()
	_, err = env.svc.Update(ctx, userID, p.ID, UpdateInput{Nickname: ptr("Samambaia"), EnvironmentID: &missing, Active: ptr(true)})
	if !errors.Is(err, ErrInvalidEnvironment) {
		t.Errorf("esperava ErrInvalidEnvironment, obteve: %v", err)
	}
}

func TestDeleteRemovesPlant(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	userID := uuid.New()

	p := createPlant(t, env.svc, userID, CreateInput{Nickname: ptr("Samambaia")})

	if err := env.svc.Delete(ctx, userID, p.ID); err != nil {
		t.Fatalf("Delete retornou erro: %v", err)
	}

	if _, err := env.svc.Get(ctx, userID, p.ID); !errors.Is(err, ErrPlantNotFound) {
		t.Fatalf("esperava ErrPlantNotFound após exclusão, obteve: %v", err)
	}
	if err := env.svc.Delete(ctx, userID, p.ID); !errors.Is(err, ErrPlantNotFound) {
		t.Fatalf("esperava ErrPlantNotFound ao excluir de novo, obteve: %v", err)
	}
}
