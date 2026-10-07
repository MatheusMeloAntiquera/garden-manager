package maintenance

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
	"github.com/matheusantiquera/garden-manager/backend/internal/plant"
	"github.com/matheusantiquera/garden-manager/backend/pkg/datetime"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// fixedNow é o "agora" usado pelo service e pelos fakes nos testes.
var fixedNow = time.Date(2026, time.October, 1, 12, 0, 0, 0, time.UTC)

// fakePlants é um PlantFinder em memória.
type fakePlants struct {
	byID map[uuid.UUID]domain.Plant
}

func (f *fakePlants) FindByID(_ context.Context, userID, id uuid.UUID) (domain.Plant, error) {
	p, ok := f.byID[id]
	if !ok || p.UserID != userID {
		return domain.Plant{}, plant.ErrPlantNotFound
	}
	return p, nil
}

// fakeTypes é um TypeRepository em memória.
type fakeTypes struct {
	byID map[uuid.UUID]domain.MaintenanceType
}

func (f *fakeTypes) List(context.Context) ([]domain.MaintenanceType, error) {
	types := make([]domain.MaintenanceType, 0, len(f.byID))
	for _, t := range f.byID {
		types = append(types, t)
	}
	slices.SortFunc(types, func(a, b domain.MaintenanceType) int { return strings.Compare(a.Name, b.Name) })
	return types, nil
}

func (f *fakeTypes) FindByID(_ context.Context, id uuid.UUID) (domain.MaintenanceType, error) {
	t, ok := f.byID[id]
	if !ok {
		return domain.MaintenanceType{}, ErrTypeNotFound
	}
	return t, nil
}

// store guarda agendamentos e execuções em memória e implementa
// ScheduleRepository (via fakeSchedules) e LogRepository (via fakeLogs),
// reproduzindo os joins do banco real.
type store struct {
	mu        sync.Mutex
	schedules map[uuid.UUID]domain.MaintenanceSchedule
	logs      map[uuid.UUID]domain.MaintenanceLog
	plants    *fakePlants
	types     *fakeTypes
}

func (s *store) hydrateSchedule(m domain.MaintenanceSchedule) domain.MaintenanceSchedule {
	m.TypeName = s.types.byID[m.TypeID].Name
	m.PlantName = plant.DisplayName(s.plants.byID[m.PlantID])
	return m
}

func (s *store) hydrateLog(l domain.MaintenanceLog) domain.MaintenanceLog {
	l.TypeName = s.types.byID[l.TypeID].Name
	l.PlantName = plant.DisplayName(s.plants.byID[l.PlantID])
	return l
}

type fakeSchedules struct{ *store }

func (f fakeSchedules) Create(_ context.Context, m domain.MaintenanceSchedule) (domain.MaintenanceSchedule, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	m.ID = uuid.New()
	m.CreatedAt, m.UpdatedAt = fixedNow, fixedNow
	f.schedules[m.ID] = m
	return f.hydrateSchedule(m), nil
}

func (f fakeSchedules) FindByID(_ context.Context, userID, id uuid.UUID) (domain.MaintenanceSchedule, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	m, ok := f.schedules[id]
	if !ok || m.UserID != userID {
		return domain.MaintenanceSchedule{}, ErrScheduleNotFound
	}
	return f.hydrateSchedule(m), nil
}

func (f fakeSchedules) List(_ context.Context, userID uuid.UUID, filter ScheduleFilter, limit, offset int) ([]domain.MaintenanceSchedule, int, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	var matched []domain.MaintenanceSchedule
	for _, m := range f.schedules {
		m = f.hydrateSchedule(m)
		switch {
		case m.UserID != userID:
		case filter.PlantID != nil && m.PlantID != *filter.PlantID:
		case filter.TypeID != nil && m.TypeID != *filter.TypeID:
		case filter.Status != nil && m.Status(fixedNow) != *filter.Status:
		case filter.DueFrom != nil && m.DueAt.Before(*filter.DueFrom):
		case filter.DueTo != nil && m.DueAt.After(*filter.DueTo):
		case filter.PlantActive != nil && f.plants.byID[m.PlantID].Active != *filter.PlantActive:
		default:
			matched = append(matched, m)
		}
	}

	slices.SortFunc(matched, func(a, b domain.MaintenanceSchedule) int { return a.DueAt.Compare(b.DueAt) })

	total := len(matched)
	return matched[min(offset, total):min(offset+limit, total)], total, nil
}

func (f fakeSchedules) Update(_ context.Context, m domain.MaintenanceSchedule) (domain.MaintenanceSchedule, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	existing, ok := f.schedules[m.ID]
	if !ok || existing.UserID != m.UserID {
		return domain.MaintenanceSchedule{}, ErrScheduleNotFound
	}
	m.CreatedAt, m.UpdatedAt = existing.CreatedAt, fixedNow
	f.schedules[m.ID] = m
	return f.hydrateSchedule(m), nil
}

func (f fakeSchedules) Delete(_ context.Context, userID, id uuid.UUID) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	m, ok := f.schedules[id]
	if !ok || m.UserID != userID {
		return ErrScheduleNotFound
	}
	delete(f.schedules, id)
	return nil
}

type fakeLogs struct{ *store }

func (f fakeLogs) Create(_ context.Context, l domain.MaintenanceLog) (domain.MaintenanceLog, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	return f.insert(l), nil
}

func (f fakeLogs) CreateFromSchedule(_ context.Context, l domain.MaintenanceLog, scheduleID uuid.UUID) (domain.MaintenanceLog, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	m, ok := f.schedules[scheduleID]
	if !ok || m.UserID != l.UserID {
		return domain.MaintenanceLog{}, ErrInvalidSchedule
	}
	delete(f.schedules, scheduleID)

	return f.insert(l), nil
}

// insert grava a execução; exige f.mu travado.
func (f fakeLogs) insert(l domain.MaintenanceLog) domain.MaintenanceLog {
	l.ID = uuid.New()
	l.CreatedAt, l.UpdatedAt = fixedNow, fixedNow
	f.logs[l.ID] = l
	return f.hydrateLog(l)
}

func (f fakeLogs) FindByID(_ context.Context, userID, id uuid.UUID) (domain.MaintenanceLog, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	l, ok := f.logs[id]
	if !ok || l.UserID != userID {
		return domain.MaintenanceLog{}, ErrLogNotFound
	}
	return f.hydrateLog(l), nil
}

func (f fakeLogs) List(_ context.Context, userID uuid.UUID, filter LogFilter, limit, offset int) ([]domain.MaintenanceLog, int, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	var matched []domain.MaintenanceLog
	for _, l := range f.logs {
		switch {
		case l.UserID != userID:
		case filter.PlantID != nil && l.PlantID != *filter.PlantID:
		case filter.TypeID != nil && l.TypeID != *filter.TypeID:
		case filter.PerformedFrom != nil && l.PerformedAt.Before(*filter.PerformedFrom):
		case filter.PerformedTo != nil && l.PerformedAt.After(*filter.PerformedTo):
		case filter.PlantActive != nil && f.plants.byID[l.PlantID].Active != *filter.PlantActive:
		default:
			matched = append(matched, f.hydrateLog(l))
		}
	}

	slices.SortFunc(matched, func(a, b domain.MaintenanceLog) int { return b.PerformedAt.Compare(a.PerformedAt) })

	total := len(matched)
	return matched[min(offset, total):min(offset+limit, total)], total, nil
}

func (f fakeLogs) Update(_ context.Context, l domain.MaintenanceLog) (domain.MaintenanceLog, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	existing, ok := f.logs[l.ID]
	if !ok || existing.UserID != l.UserID {
		return domain.MaintenanceLog{}, ErrLogNotFound
	}
	existing.PlantID = l.PlantID
	existing.TypeID = l.TypeID
	existing.PerformedAt = l.PerformedAt
	existing.Notes = l.Notes
	existing.UpdatedAt = fixedNow
	f.logs[l.ID] = existing
	return f.hydrateLog(existing), nil
}

func (f fakeLogs) Delete(_ context.Context, userID, id uuid.UUID) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	l, ok := f.logs[id]
	if !ok || l.UserID != userID {
		return ErrLogNotFound
	}
	delete(f.logs, id)
	return nil
}

// testEnv reúne o service e os dados de apoio usados pelos testes.
type testEnv struct {
	svc    Service
	plants *fakePlants
	types  *fakeTypes

	rega, poda uuid.UUID
}

func newTestEnv(t *testing.T) *testEnv {
	t.Helper()

	plants := &fakePlants{byID: make(map[uuid.UUID]domain.Plant)}
	types := &fakeTypes{byID: make(map[uuid.UUID]domain.MaintenanceType)}
	st := &store{
		schedules: make(map[uuid.UUID]domain.MaintenanceSchedule),
		logs:      make(map[uuid.UUID]domain.MaintenanceLog),
		plants:    plants,
		types:     types,
	}

	v, err := validator.New()
	if err != nil {
		t.Fatalf("validator.New retornou erro: %v", err)
	}

	svc := NewService(types, fakeSchedules{st}, fakeLogs{st}, plants, v).(*service)
	svc.now = func() time.Time { return fixedNow }

	env := &testEnv{svc: svc, plants: plants, types: types}
	env.rega = env.addType("Rega")
	env.poda = env.addType("Poda")
	return env
}

func (e *testEnv) addType(name string) uuid.UUID {
	id := uuid.New()
	e.types.byID[id] = domain.MaintenanceType{ID: id, Name: name}
	return id
}

func (e *testEnv) addPlant(userID uuid.UUID, nickname string) uuid.UUID {
	id := uuid.New()
	e.plants.byID[id] = domain.Plant{ID: id, UserID: userID, Nickname: &nickname, Active: true}
	return id
}

func ptr[T any](v T) *T {
	return &v
}

// at retorna um LocalDateTime deslocado de fixedNow.
func at(offset time.Duration) *datetime.LocalDateTime {
	d := datetime.New(fixedNow.Add(offset))
	return &d
}

func assertValidationError(t *testing.T, err error) {
	t.Helper()

	var validationErrs validatorpkg.ValidationErrors
	if !errors.As(err, &validationErrs) {
		t.Fatalf("esperava erro de validação, obteve: %v", err)
	}
}

func createSchedule(t *testing.T, svc Service, userID uuid.UUID, input ScheduleInput) ScheduleResponse {
	t.Helper()

	s, err := svc.CreateSchedule(context.Background(), userID, input)
	if err != nil {
		t.Fatalf("CreateSchedule retornou erro: %v", err)
	}
	return s
}

func createLog(t *testing.T, svc Service, userID uuid.UUID, input CreateLogInput) LogResponse {
	t.Helper()

	l, err := svc.CreateLog(context.Background(), userID, input)
	if err != nil {
		t.Fatalf("CreateLog retornou erro: %v", err)
	}
	return l
}

func TestListTypes(t *testing.T) {
	env := newTestEnv(t)

	list, err := env.svc.ListTypes(context.Background())
	if err != nil {
		t.Fatalf("ListTypes retornou erro: %v", err)
	}

	if len(list.Data) != 2 || list.Data[0].Name != "Poda" || list.Data[1].Name != "Rega" {
		t.Errorf("esperava Poda e Rega em ordem alfabética, obteve %+v", list.Data)
	}
}

func TestCreateSchedule(t *testing.T) {
	env := newTestEnv(t)
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")

	s := createSchedule(t, env.svc, userID, ScheduleInput{
		PlantID: &plantID,
		TypeID:  &env.rega,
		DueAt:   at(24 * time.Hour),
		Notes:   ptr("  meio litro  "),
	})

	if s.Status != domain.MaintenanceStatusPending {
		t.Errorf("esperava status pending, obteve %q", s.Status)
	}
	if s.Plant.DisplayName != "Samambaia" || s.Type.Name != "Rega" {
		t.Errorf("esperava planta e tipo no resumo, obteve %+v / %+v", s.Plant, s.Type)
	}
	if s.Notes == nil || *s.Notes != "meio litro" {
		t.Errorf("esperava notes normalizado, obteve %v", s.Notes)
	}
}

func TestCreateScheduleWithPastDueIsOverdue(t *testing.T) {
	env := newTestEnv(t)
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")

	s := createSchedule(t, env.svc, userID, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(-time.Hour)})

	if s.Status != domain.MaintenanceStatusOverdue {
		t.Errorf("esperava status overdue, obteve %q", s.Status)
	}
}

func TestCreateScheduleValidation(t *testing.T) {
	env := newTestEnv(t)
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")

	tests := map[string]ScheduleInput{
		"sem planta":        {TypeID: &env.rega, DueAt: at(time.Hour)},
		"sem tipo":          {PlantID: &plantID, DueAt: at(time.Hour)},
		"sem prazo":         {PlantID: &plantID, TypeID: &env.rega},
		"notes muito longo": {PlantID: &plantID, TypeID: &env.rega, DueAt: at(time.Hour), Notes: ptr(strings.Repeat("a", 2001))},
	}

	for name, input := range tests {
		t.Run(name, func(t *testing.T) {
			_, err := env.svc.CreateSchedule(context.Background(), userID, input)
			assertValidationError(t, err)
		})
	}
}

func TestCreateScheduleRejectsInvalidReferences(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner, other := uuid.New(), uuid.New()
	ownPlant := env.addPlant(owner, "Samambaia")
	othersPlant := env.addPlant(other, "De outro usuário")
	missing := uuid.New()

	_, err := env.svc.CreateSchedule(ctx, owner, ScheduleInput{PlantID: &othersPlant, TypeID: &env.rega, DueAt: at(time.Hour)})
	if !errors.Is(err, ErrInvalidPlant) {
		t.Errorf("planta de outro usuário: esperava ErrInvalidPlant, obteve: %v", err)
	}

	_, err = env.svc.CreateSchedule(ctx, owner, ScheduleInput{PlantID: &ownPlant, TypeID: &missing, DueAt: at(time.Hour)})
	if !errors.Is(err, ErrInvalidType) {
		t.Errorf("tipo inexistente: esperava ErrInvalidType, obteve: %v", err)
	}
}

func TestCreateLogWithoutSchedule(t *testing.T) {
	env := newTestEnv(t)
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")

	l := createLog(t, env.svc, userID, CreateLogInput{PlantID: &plantID, TypeID: &env.poda, PerformedAt: at(-time.Hour)})

	if l.CreatedFromSchedule {
		t.Error("esperava created_from_schedule false para execução sem agendamento")
	}
	if l.Type.Name != "Poda" || l.Plant.DisplayName != "Samambaia" {
		t.Errorf("esperava planta e tipo no resumo, obteve %+v / %+v", l.Plant, l.Type)
	}
}

func TestCreateLogFromScheduleInheritsFieldsAndDeletesIt(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")
	s := createSchedule(t, env.svc, userID, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(-time.Hour), Notes: ptr("meio litro")})

	l := createLog(t, env.svc, userID, CreateLogInput{ScheduleID: &s.ID, PerformedAt: at(0)})

	if !l.CreatedFromSchedule {
		t.Error("esperava created_from_schedule true")
	}
	if l.Plant.ID != plantID || l.Type.ID != env.rega {
		t.Errorf("esperava planta e tipo herdados do agendamento, obteve %+v / %+v", l.Plant, l.Type)
	}
	if l.Notes == nil || *l.Notes != "meio litro" {
		t.Errorf("esperava notes herdado do agendamento, obteve %v", l.Notes)
	}

	if _, err := env.svc.GetSchedule(ctx, userID, s.ID); !errors.Is(err, ErrScheduleNotFound) {
		t.Errorf("esperava o agendamento excluído após a execução, obteve: %v", err)
	}
}

func TestCreateLogFromScheduleKeepsOwnNotes(t *testing.T) {
	env := newTestEnv(t)
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")
	s := createSchedule(t, env.svc, userID, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(time.Hour), Notes: ptr("meio litro")})

	l := createLog(t, env.svc, userID, CreateLogInput{ScheduleID: &s.ID, PerformedAt: at(0), Notes: ptr("regada com um litro")})

	if l.Notes == nil || *l.Notes != "regada com um litro" {
		t.Errorf("esperava as notes informadas na execução, obteve %v", l.Notes)
	}
}

func TestCreateLogScheduleRules(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner, other := uuid.New(), uuid.New()
	plantID := env.addPlant(owner, "Samambaia")
	anotherPlant := env.addPlant(owner, "Alecrim")
	s := createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(time.Hour)})
	othersPlant := env.addPlant(other, "De outro usuário")
	othersSchedule := createSchedule(t, env.svc, other, ScheduleInput{PlantID: &othersPlant, TypeID: &env.rega, DueAt: at(time.Hour)})

	_, err := env.svc.CreateLog(ctx, owner, CreateLogInput{ScheduleID: &othersSchedule.ID, PerformedAt: at(0)})
	if !errors.Is(err, ErrInvalidSchedule) {
		t.Errorf("agendamento de outro usuário: esperava ErrInvalidSchedule, obteve: %v", err)
	}

	_, err = env.svc.CreateLog(ctx, owner, CreateLogInput{ScheduleID: &s.ID, PlantID: &anotherPlant, PerformedAt: at(0)})
	if !errors.Is(err, ErrScheduleMismatch) {
		t.Errorf("planta diferente: esperava ErrScheduleMismatch, obteve: %v", err)
	}

	_, err = env.svc.CreateLog(ctx, owner, CreateLogInput{ScheduleID: &s.ID, TypeID: &env.poda, PerformedAt: at(0)})
	if !errors.Is(err, ErrScheduleMismatch) {
		t.Errorf("tipo diferente: esperava ErrScheduleMismatch, obteve: %v", err)
	}

	if _, err := env.svc.GetSchedule(ctx, owner, s.ID); err != nil {
		t.Errorf("tentativas rejeitadas não deveriam excluir o agendamento, obteve: %v", err)
	}

	createLog(t, env.svc, owner, CreateLogInput{ScheduleID: &s.ID, PerformedAt: at(0)})

	_, err = env.svc.CreateLog(ctx, owner, CreateLogInput{ScheduleID: &s.ID, PerformedAt: at(0)})
	if !errors.Is(err, ErrInvalidSchedule) {
		t.Errorf("agendamento já executado: esperava ErrInvalidSchedule, obteve: %v", err)
	}
}

func TestCreateLogValidation(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")

	tests := map[string]CreateLogInput{
		"sem planta nem agendamento": {TypeID: &env.rega, PerformedAt: at(0)},
		"sem tipo nem agendamento":   {PlantID: &plantID, PerformedAt: at(0)},
		"sem data":                   {PlantID: &plantID, TypeID: &env.rega},
	}
	for name, input := range tests {
		t.Run(name, func(t *testing.T) {
			_, err := env.svc.CreateLog(ctx, userID, input)
			assertValidationError(t, err)
		})
	}

	_, err := env.svc.CreateLog(ctx, userID, CreateLogInput{PlantID: &plantID, TypeID: &env.rega, PerformedAt: at(time.Minute)})
	if !errors.Is(err, ErrPerformedAtInFuture) {
		t.Errorf("data no futuro: esperava ErrPerformedAtInFuture, obteve: %v", err)
	}
}

func TestUpdateLogKeepsCreatedFromSchedule(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")
	anotherPlant := env.addPlant(userID, "Alecrim")
	s := createSchedule(t, env.svc, userID, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(time.Hour)})
	l := createLog(t, env.svc, userID, CreateLogInput{ScheduleID: &s.ID, PerformedAt: at(-time.Hour)})

	updated, err := env.svc.UpdateLog(ctx, userID, l.ID, UpdateLogInput{
		PlantID: &anotherPlant, TypeID: &env.poda, PerformedAt: at(-2 * time.Hour), Notes: ptr("regada de manhã"),
	})
	if err != nil {
		t.Fatalf("UpdateLog retornou erro: %v", err)
	}
	if !updated.CreatedFromSchedule {
		t.Error("esperava created_from_schedule preservado")
	}
	if updated.Plant.ID != anotherPlant || updated.Type.ID != env.poda || !updated.PerformedAt.Equal(fixedNow.Add(-2*time.Hour)) || updated.Notes == nil {
		t.Errorf("esperava o registro substituído, obteve %+v", updated)
	}

	_, err = env.svc.UpdateLog(ctx, userID, l.ID, UpdateLogInput{PlantID: &plantID, TypeID: &env.rega, PerformedAt: at(time.Hour)})
	if !errors.Is(err, ErrPerformedAtInFuture) {
		t.Errorf("data no futuro: esperava ErrPerformedAtInFuture, obteve: %v", err)
	}
}

func TestUpdateSchedule(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")
	anotherPlant := env.addPlant(userID, "Alecrim")
	s := createSchedule(t, env.svc, userID, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(time.Hour), Notes: ptr("antes")})

	updated, err := env.svc.UpdateSchedule(ctx, userID, s.ID, ScheduleInput{PlantID: &anotherPlant, TypeID: &env.poda, DueAt: at(-48 * time.Hour)})
	if err != nil {
		t.Fatalf("UpdateSchedule retornou erro: %v", err)
	}
	if updated.Plant.ID != anotherPlant || updated.Type.ID != env.poda || updated.Notes != nil {
		t.Errorf("esperava o registro substituído, obteve %+v", updated)
	}
	if updated.Status != domain.MaintenanceStatusOverdue {
		t.Errorf("esperava status recalculado para overdue, obteve %q", updated.Status)
	}

	missing := uuid.New()
	_, err = env.svc.UpdateSchedule(ctx, userID, s.ID, ScheduleInput{PlantID: &plantID, TypeID: &missing, DueAt: at(time.Hour)})
	if !errors.Is(err, ErrInvalidType) {
		t.Errorf("tipo inexistente: esperava ErrInvalidType, obteve: %v", err)
	}
}

func TestDeleteSchedule(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	userID := uuid.New()
	plantID := env.addPlant(userID, "Samambaia")
	s := createSchedule(t, env.svc, userID, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(time.Hour)})

	if err := env.svc.DeleteSchedule(ctx, userID, s.ID); err != nil {
		t.Fatalf("DeleteSchedule retornou erro: %v", err)
	}
	if _, err := env.svc.GetSchedule(ctx, userID, s.ID); !errors.Is(err, ErrScheduleNotFound) {
		t.Errorf("esperava ErrScheduleNotFound após excluir, obteve: %v", err)
	}
	if err := env.svc.DeleteSchedule(ctx, userID, s.ID); !errors.Is(err, ErrScheduleNotFound) {
		t.Errorf("excluir de novo: esperava ErrScheduleNotFound, obteve: %v", err)
	}
}

func TestListSchedulesFiltersAndPagination(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner, other := uuid.New(), uuid.New()
	samambaia := env.addPlant(owner, "Samambaia")
	alecrim := env.addPlant(owner, "Alecrim")

	overdue := createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &samambaia, TypeID: &env.rega, DueAt: at(-2 * time.Hour)})
	overdue2 := createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &alecrim, TypeID: &env.poda, DueAt: at(-time.Hour)})
	pending := createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &samambaia, TypeID: &env.poda, DueAt: at(time.Hour)})
	executed := createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &alecrim, TypeID: &env.rega, DueAt: at(30 * time.Minute)})
	createLog(t, env.svc, owner, CreateLogInput{ScheduleID: &executed.ID, PerformedAt: at(0)})

	othersPlant := env.addPlant(other, "De outro usuário")
	createSchedule(t, env.svc, other, ScheduleInput{PlantID: &othersPlant, TypeID: &env.rega, DueAt: at(time.Hour)})

	tests := []struct {
		name  string
		input ListSchedulesInput
		want  []uuid.UUID
	}{
		{"todos do usuário, por prazo, sem o executado", ListSchedulesInput{}, []uuid.UUID{overdue.ID, overdue2.ID, pending.ID}},
		{"por planta", ListSchedulesInput{PlantID: &samambaia}, []uuid.UUID{overdue.ID, pending.ID}},
		{"por tipo", ListSchedulesInput{TypeID: &env.poda}, []uuid.UUID{overdue2.ID, pending.ID}},
		{"pendentes", ListSchedulesInput{Status: ptr(domain.MaintenanceStatusPending)}, []uuid.UUID{pending.ID}},
		{"atrasados", ListSchedulesInput{Status: ptr(domain.MaintenanceStatusOverdue)}, []uuid.UUID{overdue.ID, overdue2.ID}},
		{"por intervalo de prazo", ListSchedulesInput{DueFrom: at(-90 * time.Minute), DueTo: at(2 * time.Hour)}, []uuid.UUID{overdue2.ID, pending.ID}},
		{"página 2", ListSchedulesInput{Params: pagination.Params{Page: 2, PageSize: 2}}, []uuid.UUID{pending.ID}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			list, err := env.svc.ListSchedules(ctx, owner, tt.input)
			if err != nil {
				t.Fatalf("ListSchedules retornou erro: %v", err)
			}

			var got []uuid.UUID
			for _, s := range list.Data {
				got = append(got, s.ID)
			}
			if !slices.Equal(got, tt.want) {
				t.Errorf("obteve %v, esperava %v", got, tt.want)
			}
		})
	}
}

func TestArchivedPlantRejectsNewMaintenances(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner := uuid.New()
	plantID := env.addPlant(owner, "Samambaia")

	schedule := createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(time.Hour)})
	log := createLog(t, env.svc, owner, CreateLogInput{PlantID: &plantID, TypeID: &env.rega, PerformedAt: at(-time.Hour)})

	// A planta é arquivada depois de ter agendamento e histórico.
	p := env.plants.byID[plantID]
	p.Active = false
	env.plants.byID[plantID] = p

	if _, err := env.svc.CreateSchedule(ctx, owner, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(time.Hour)}); !errors.Is(err, ErrArchivedPlant) {
		t.Errorf("agendar em planta arquivada: esperava ErrArchivedPlant, obteve: %v", err)
	}
	if _, err := env.svc.CreateLog(ctx, owner, CreateLogInput{PlantID: &plantID, TypeID: &env.rega, PerformedAt: at(-time.Minute)}); !errors.Is(err, ErrArchivedPlant) {
		t.Errorf("registrar em planta arquivada: esperava ErrArchivedPlant, obteve: %v", err)
	}
	if _, err := env.svc.CreateLog(ctx, owner, CreateLogInput{ScheduleID: &schedule.ID, PerformedAt: at(-time.Minute)}); !errors.Is(err, ErrArchivedPlant) {
		t.Errorf("concluir agendamento de planta arquivada: esperava ErrArchivedPlant, obteve: %v", err)
	}
	if _, err := env.svc.GetSchedule(ctx, owner, schedule.ID); err != nil {
		t.Errorf("o agendamento recusado deveria continuar existindo, obteve: %v", err)
	}

	// Editar e excluir o que a planta já tinha continua permitido.
	if _, err := env.svc.UpdateSchedule(ctx, owner, schedule.ID, ScheduleInput{PlantID: &plantID, TypeID: &env.poda, DueAt: at(2 * time.Hour)}); err != nil {
		t.Errorf("editar agendamento de planta arquivada: obteve erro %v", err)
	}
	if _, err := env.svc.UpdateLog(ctx, owner, log.ID, UpdateLogInput{PlantID: &plantID, TypeID: &env.poda, PerformedAt: at(-2 * time.Hour)}); err != nil {
		t.Errorf("editar execução de planta arquivada: obteve erro %v", err)
	}
	if err := env.svc.DeleteSchedule(ctx, owner, schedule.ID); err != nil {
		t.Errorf("excluir agendamento de planta arquivada: obteve erro %v", err)
	}
	if err := env.svc.DeleteLog(ctx, owner, log.ID); err != nil {
		t.Errorf("excluir execução de planta arquivada: obteve erro %v", err)
	}
}

func TestListFiltersByPlantActive(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner := uuid.New()
	active := env.addPlant(owner, "Ativa")
	archived := env.addPlant(owner, "Arquivada")

	activeSchedule := createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &active, TypeID: &env.rega, DueAt: at(-time.Hour)})
	archivedSchedule := createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &archived, TypeID: &env.rega, DueAt: at(-2 * time.Hour)})
	activeLog := createLog(t, env.svc, owner, CreateLogInput{PlantID: &active, TypeID: &env.rega, PerformedAt: at(-time.Hour)})
	archivedLog := createLog(t, env.svc, owner, CreateLogInput{PlantID: &archived, TypeID: &env.rega, PerformedAt: at(-2 * time.Hour)})

	// A planta é arquivada depois de ter agendamento e histórico.
	p := env.plants.byID[archived]
	p.Active = false
	env.plants.byID[archived] = p

	scheduleIDs := func(input ListSchedulesInput) []uuid.UUID {
		list, err := env.svc.ListSchedules(ctx, owner, input)
		if err != nil {
			t.Fatalf("ListSchedules retornou erro: %v", err)
		}
		var ids []uuid.UUID
		for _, s := range list.Data {
			ids = append(ids, s.ID)
		}
		return ids
	}
	logIDs := func(input ListLogsInput) []uuid.UUID {
		list, err := env.svc.ListLogs(ctx, owner, input)
		if err != nil {
			t.Fatalf("ListLogs retornou erro: %v", err)
		}
		var ids []uuid.UUID
		for _, l := range list.Data {
			ids = append(ids, l.ID)
		}
		return ids
	}

	if got := scheduleIDs(ListSchedulesInput{}); !slices.Equal(got, []uuid.UUID{archivedSchedule.ID, activeSchedule.ID}) {
		t.Errorf("sem plant_active: esperava todos, obteve %v", got)
	}
	if got := scheduleIDs(ListSchedulesInput{PlantActive: ptr(true)}); !slices.Equal(got, []uuid.UUID{activeSchedule.ID}) {
		t.Errorf("plant_active=true: esperava só a planta ativa, obteve %v", got)
	}
	if got := scheduleIDs(ListSchedulesInput{PlantActive: ptr(false)}); !slices.Equal(got, []uuid.UUID{archivedSchedule.ID}) {
		t.Errorf("plant_active=false: esperava só a planta arquivada, obteve %v", got)
	}
	if got := logIDs(ListLogsInput{}); len(got) != 2 {
		t.Errorf("sem plant_active: esperava as 2 execuções, obteve %v", got)
	}
	if got := logIDs(ListLogsInput{PlantActive: ptr(true)}); !slices.Equal(got, []uuid.UUID{activeLog.ID}) {
		t.Errorf("plant_active=true: esperava só a planta ativa, obteve %v", got)
	}
	if got := logIDs(ListLogsInput{PlantActive: ptr(false)}); !slices.Equal(got, []uuid.UUID{archivedLog.ID}) {
		t.Errorf("plant_active=false: esperava só a planta arquivada, obteve %v", got)
	}
}

func TestListPlantMaintenances(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner, other := uuid.New(), uuid.New()
	samambaia := env.addPlant(owner, "Samambaia")
	alecrim := env.addPlant(owner, "Alecrim")

	createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &samambaia, TypeID: &env.rega, DueAt: at(time.Hour)})
	createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &alecrim, TypeID: &env.rega, DueAt: at(time.Hour)})
	createLog(t, env.svc, owner, CreateLogInput{PlantID: &samambaia, TypeID: &env.poda, PerformedAt: at(-time.Hour)})
	createLog(t, env.svc, owner, CreateLogInput{PlantID: &samambaia, TypeID: &env.rega, PerformedAt: at(0)})
	createLog(t, env.svc, owner, CreateLogInput{PlantID: &alecrim, TypeID: &env.rega, PerformedAt: at(0)})

	schedules, err := env.svc.ListPlantSchedules(ctx, owner, samambaia, ListSchedulesInput{})
	if err != nil {
		t.Fatalf("ListPlantSchedules retornou erro: %v", err)
	}
	if schedules.Total != 1 || schedules.Data[0].Plant.ID != samambaia {
		t.Errorf("esperava 1 agendamento da Samambaia, obteve %+v", schedules)
	}

	logs, err := env.svc.ListPlantLogs(ctx, owner, samambaia, ListLogsInput{})
	if err != nil {
		t.Fatalf("ListPlantLogs retornou erro: %v", err)
	}
	if logs.Total != 2 || logs.Data[0].Type.Name != "Rega" || logs.Data[1].Type.Name != "Poda" {
		t.Errorf("esperava 2 execuções da Samambaia, da mais recente para a mais antiga, obteve %+v", logs.Data)
	}

	// Um plant_id vindo da query string não escapa da planta da rota.
	logs, err = env.svc.ListPlantLogs(ctx, owner, samambaia, ListLogsInput{PlantID: &alecrim})
	if err != nil || logs.Total != 2 {
		t.Errorf("esperava a planta da rota prevalecer, obteve total=%d err=%v", logs.Total, err)
	}

	if _, err := env.svc.ListPlantSchedules(ctx, other, samambaia, ListSchedulesInput{}); !errors.Is(err, plant.ErrPlantNotFound) {
		t.Errorf("planta de outro usuário: esperava plant.ErrPlantNotFound, obteve: %v", err)
	}
	if _, err := env.svc.ListPlantLogs(ctx, owner, uuid.New(), ListLogsInput{}); !errors.Is(err, plant.ErrPlantNotFound) {
		t.Errorf("planta inexistente: esperava plant.ErrPlantNotFound, obteve: %v", err)
	}
}

func TestOtherUserCannotAccessMaintenances(t *testing.T) {
	env := newTestEnv(t)
	ctx := context.Background()
	owner, intruder := uuid.New(), uuid.New()
	plantID := env.addPlant(owner, "Samambaia")
	intruderPlant := env.addPlant(intruder, "Do intruso")
	s := createSchedule(t, env.svc, owner, ScheduleInput{PlantID: &plantID, TypeID: &env.rega, DueAt: at(time.Hour)})
	l := createLog(t, env.svc, owner, CreateLogInput{PlantID: &plantID, TypeID: &env.rega, PerformedAt: at(0)})

	if _, err := env.svc.GetSchedule(ctx, intruder, s.ID); !errors.Is(err, ErrScheduleNotFound) {
		t.Errorf("GetSchedule: esperava ErrScheduleNotFound, obteve: %v", err)
	}
	if _, err := env.svc.UpdateSchedule(ctx, intruder, s.ID, ScheduleInput{PlantID: &intruderPlant, TypeID: &env.rega, DueAt: at(time.Hour)}); !errors.Is(err, ErrScheduleNotFound) {
		t.Errorf("UpdateSchedule: esperava ErrScheduleNotFound, obteve: %v", err)
	}
	if err := env.svc.DeleteSchedule(ctx, intruder, s.ID); !errors.Is(err, ErrScheduleNotFound) {
		t.Errorf("DeleteSchedule: esperava ErrScheduleNotFound, obteve: %v", err)
	}
	if _, err := env.svc.GetLog(ctx, intruder, l.ID); !errors.Is(err, ErrLogNotFound) {
		t.Errorf("GetLog: esperava ErrLogNotFound, obteve: %v", err)
	}
	if _, err := env.svc.UpdateLog(ctx, intruder, l.ID, UpdateLogInput{PlantID: &intruderPlant, TypeID: &env.rega, PerformedAt: at(0)}); !errors.Is(err, ErrLogNotFound) {
		t.Errorf("UpdateLog: esperava ErrLogNotFound, obteve: %v", err)
	}
	if err := env.svc.DeleteLog(ctx, intruder, l.ID); !errors.Is(err, ErrLogNotFound) {
		t.Errorf("DeleteLog: esperava ErrLogNotFound, obteve: %v", err)
	}

	list, err := env.svc.ListLogs(ctx, intruder, ListLogsInput{})
	if err != nil || list.Total != 0 {
		t.Errorf("ListLogs: esperava lista vazia, obteve total=%d err=%v", list.Total, err)
	}
}
