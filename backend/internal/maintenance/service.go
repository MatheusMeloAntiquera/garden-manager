package maintenance

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/internal/plant"
	"github.com/matheusantiquera/garden-manager/backend/pkg/datetime"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// Service implementa as regras de negócio de manutenções: o catálogo de
// tipos, os agendamentos (o planejado) e as execuções (o realizado). Todas as
// operações atuam apenas sobre os registros do usuário informado.
type Service interface {
	ListTypes(ctx context.Context) (TypeListResponse, error)

	CreateSchedule(ctx context.Context, userID uuid.UUID, input ScheduleInput) (ScheduleResponse, error)
	GetSchedule(ctx context.Context, userID, id uuid.UUID) (ScheduleResponse, error)
	ListSchedules(ctx context.Context, userID uuid.UUID, input ListSchedulesInput) (ListResponse[ScheduleResponse], error)
	// ListPlantSchedules lista os agendamentos de uma planta, retornando
	// plant.ErrPlantNotFound se ela não existir ou for de outro usuário.
	ListPlantSchedules(ctx context.Context, userID, plantID uuid.UUID, input ListSchedulesInput) (ListResponse[ScheduleResponse], error)
	UpdateSchedule(ctx context.Context, userID, id uuid.UUID, input ScheduleInput) (ScheduleResponse, error)
	DeleteSchedule(ctx context.Context, userID, id uuid.UUID) error

	CreateLog(ctx context.Context, userID uuid.UUID, input CreateLogInput) (LogResponse, error)
	GetLog(ctx context.Context, userID, id uuid.UUID) (LogResponse, error)
	ListLogs(ctx context.Context, userID uuid.UUID, input ListLogsInput) (ListResponse[LogResponse], error)
	// ListPlantLogs lista as execuções de uma planta, retornando
	// plant.ErrPlantNotFound se ela não existir ou for de outro usuário.
	ListPlantLogs(ctx context.Context, userID, plantID uuid.UUID, input ListLogsInput) (ListResponse[LogResponse], error)
	UpdateLog(ctx context.Context, userID, id uuid.UUID, input UpdateLogInput) (LogResponse, error)
	DeleteLog(ctx context.Context, userID, id uuid.UUID) error
}

// PlantFinder é a parte do repositório de plantas usada para conferir que a
// planta informada pertence ao usuário. Satisfeita por plant.Repository.
type PlantFinder interface {
	FindByID(ctx context.Context, userID, id uuid.UUID) (domain.Plant, error)
}

type service struct {
	types     TypeRepository
	schedules ScheduleRepository
	logs      LogRepository
	plants    PlantFinder
	validator *validator.Validator
	now       func() time.Time // substituível nos testes
}

// NewService cria o Service de manutenções.
func NewService(types TypeRepository, schedules ScheduleRepository, logs LogRepository, plants PlantFinder, v *validator.Validator) Service {
	return &service{types: types, schedules: schedules, logs: logs, plants: plants, validator: v, now: time.Now}
}

func (s *service) ListTypes(ctx context.Context) (TypeListResponse, error) {
	types, err := s.types.List(ctx)
	if err != nil {
		return TypeListResponse{}, err
	}

	data := make([]TypeResponse, 0, len(types))
	for _, t := range types {
		data = append(data, TypeResponse{ID: t.ID, Name: t.Name})
	}

	return TypeListResponse{Data: data}, nil
}

func (s *service) CreateSchedule(ctx context.Context, userID uuid.UUID, input ScheduleInput) (ScheduleResponse, error) {
	input.Notes = normalizeText(input.Notes)

	if err := s.validator.Struct(input); err != nil {
		return ScheduleResponse{}, err
	}

	if err := s.checkReferences(ctx, userID, *input.PlantID, *input.TypeID); err != nil {
		return ScheduleResponse{}, err
	}

	schedule, err := s.schedules.Create(ctx, domain.MaintenanceSchedule{
		UserID:  userID,
		PlantID: *input.PlantID,
		TypeID:  *input.TypeID,
		DueAt:   input.DueAt.Time,
		Notes:   input.Notes,
	})
	if err != nil {
		return ScheduleResponse{}, err
	}

	return NewScheduleResponse(schedule, s.now()), nil
}

func (s *service) GetSchedule(ctx context.Context, userID, id uuid.UUID) (ScheduleResponse, error) {
	schedule, err := s.schedules.FindByID(ctx, userID, id)
	if err != nil {
		return ScheduleResponse{}, err
	}
	return NewScheduleResponse(schedule, s.now()), nil
}

func (s *service) ListSchedules(ctx context.Context, userID uuid.UUID, input ListSchedulesInput) (ListResponse[ScheduleResponse], error) {
	params := input.Normalize()

	schedules, total, err := s.schedules.List(ctx, userID, ScheduleFilter{
		PlantID: input.PlantID,
		TypeID:  input.TypeID,
		Status:  input.Status,
		DueFrom: timeOf(input.DueFrom),
		DueTo:   timeOf(input.DueTo),
	}, params.PageSize, params.Offset())
	if err != nil {
		return ListResponse[ScheduleResponse]{}, err
	}

	now := s.now()
	data := make([]ScheduleResponse, 0, len(schedules))
	for _, schedule := range schedules {
		data = append(data, NewScheduleResponse(schedule, now))
	}

	return ListResponse[ScheduleResponse]{Data: data, Page: params.Page, PageSize: params.PageSize, Total: total}, nil
}

func (s *service) ListPlantSchedules(ctx context.Context, userID, plantID uuid.UUID, input ListSchedulesInput) (ListResponse[ScheduleResponse], error) {
	if _, err := s.plants.FindByID(ctx, userID, plantID); err != nil {
		return ListResponse[ScheduleResponse]{}, err
	}

	input.PlantID = &plantID
	return s.ListSchedules(ctx, userID, input)
}

func (s *service) UpdateSchedule(ctx context.Context, userID, id uuid.UUID, input ScheduleInput) (ScheduleResponse, error) {
	input.Notes = normalizeText(input.Notes)

	if err := s.validator.Struct(input); err != nil {
		return ScheduleResponse{}, err
	}

	if err := s.checkReferences(ctx, userID, *input.PlantID, *input.TypeID); err != nil {
		return ScheduleResponse{}, err
	}

	schedule, err := s.schedules.Update(ctx, domain.MaintenanceSchedule{
		ID:      id,
		UserID:  userID,
		PlantID: *input.PlantID,
		TypeID:  *input.TypeID,
		DueAt:   input.DueAt.Time,
		Notes:   input.Notes,
	})
	if err != nil {
		return ScheduleResponse{}, err
	}

	return NewScheduleResponse(schedule, s.now()), nil
}

func (s *service) DeleteSchedule(ctx context.Context, userID, id uuid.UUID) error {
	return s.schedules.Delete(ctx, userID, id)
}

func (s *service) CreateLog(ctx context.Context, userID uuid.UUID, input CreateLogInput) (LogResponse, error) {
	input.Notes = normalizeText(input.Notes)

	if err := s.validator.Struct(input); err != nil {
		return LogResponse{}, err
	}

	if input.PerformedAt.After(s.now()) {
		return LogResponse{}, ErrPerformedAtInFuture
	}

	if input.ScheduleID != nil {
		schedule, err := s.schedules.FindByID(ctx, userID, *input.ScheduleID)
		if err != nil {
			if errors.Is(err, ErrScheduleNotFound) {
				return LogResponse{}, ErrInvalidSchedule
			}
			return LogResponse{}, err
		}

		// Planta, tipo e observações omitidos são herdados do agendamento,
		// que será excluído; planta e tipo informados precisam bater com ele.
		if input.PlantID == nil {
			input.PlantID = &schedule.PlantID
		}
		if input.TypeID == nil {
			input.TypeID = &schedule.TypeID
		}
		if input.Notes == nil {
			input.Notes = schedule.Notes
		}
		if *input.PlantID != schedule.PlantID || *input.TypeID != schedule.TypeID {
			return LogResponse{}, ErrScheduleMismatch
		}
	}

	if err := s.checkReferences(ctx, userID, *input.PlantID, *input.TypeID); err != nil {
		return LogResponse{}, err
	}

	log := domain.MaintenanceLog{
		UserID:              userID,
		PlantID:             *input.PlantID,
		TypeID:              *input.TypeID,
		CreatedFromSchedule: input.ScheduleID != nil,
		PerformedAt:         input.PerformedAt.Time,
		Notes:               input.Notes,
	}

	var err error
	if input.ScheduleID != nil {
		log, err = s.logs.CreateFromSchedule(ctx, log, *input.ScheduleID)
	} else {
		log, err = s.logs.Create(ctx, log)
	}
	if err != nil {
		return LogResponse{}, err
	}

	return NewLogResponse(log), nil
}

func (s *service) GetLog(ctx context.Context, userID, id uuid.UUID) (LogResponse, error) {
	log, err := s.logs.FindByID(ctx, userID, id)
	if err != nil {
		return LogResponse{}, err
	}
	return NewLogResponse(log), nil
}

func (s *service) ListLogs(ctx context.Context, userID uuid.UUID, input ListLogsInput) (ListResponse[LogResponse], error) {
	params := input.Normalize()

	logs, total, err := s.logs.List(ctx, userID, LogFilter{
		PlantID:       input.PlantID,
		TypeID:        input.TypeID,
		PerformedFrom: timeOf(input.PerformedFrom),
		PerformedTo:   timeOf(input.PerformedTo),
	}, params.PageSize, params.Offset())
	if err != nil {
		return ListResponse[LogResponse]{}, err
	}

	data := make([]LogResponse, 0, len(logs))
	for _, log := range logs {
		data = append(data, NewLogResponse(log))
	}

	return ListResponse[LogResponse]{Data: data, Page: params.Page, PageSize: params.PageSize, Total: total}, nil
}

func (s *service) ListPlantLogs(ctx context.Context, userID, plantID uuid.UUID, input ListLogsInput) (ListResponse[LogResponse], error) {
	if _, err := s.plants.FindByID(ctx, userID, plantID); err != nil {
		return ListResponse[LogResponse]{}, err
	}

	input.PlantID = &plantID
	return s.ListLogs(ctx, userID, input)
}

func (s *service) UpdateLog(ctx context.Context, userID, id uuid.UUID, input UpdateLogInput) (LogResponse, error) {
	input.Notes = normalizeText(input.Notes)

	if err := s.validator.Struct(input); err != nil {
		return LogResponse{}, err
	}

	if input.PerformedAt.After(s.now()) {
		return LogResponse{}, ErrPerformedAtInFuture
	}

	if err := s.checkReferences(ctx, userID, *input.PlantID, *input.TypeID); err != nil {
		return LogResponse{}, err
	}

	log, err := s.logs.Update(ctx, domain.MaintenanceLog{
		ID:          id,
		UserID:      userID,
		PlantID:     *input.PlantID,
		TypeID:      *input.TypeID,
		PerformedAt: input.PerformedAt.Time,
		Notes:       input.Notes,
	})
	if err != nil {
		return LogResponse{}, err
	}

	return NewLogResponse(log), nil
}

func (s *service) DeleteLog(ctx context.Context, userID, id uuid.UUID) error {
	return s.logs.Delete(ctx, userID, id)
}

// checkReferences confere que a planta pertence ao usuário e que o tipo
// existe no catálogo.
func (s *service) checkReferences(ctx context.Context, userID, plantID, typeID uuid.UUID) error {
	if _, err := s.plants.FindByID(ctx, userID, plantID); err != nil {
		if errors.Is(err, plant.ErrPlantNotFound) {
			return ErrInvalidPlant
		}
		return err
	}

	if _, err := s.types.FindByID(ctx, typeID); err != nil {
		if errors.Is(err, ErrTypeNotFound) {
			return ErrInvalidType
		}
		return err
	}

	return nil
}

// timeOf converte um filtro de data opcional em *time.Time.
func timeOf(d *datetime.LocalDateTime) *time.Time {
	if d == nil {
		return nil
	}
	return &d.Time
}

// normalizeText remove espaços das pontas e converte texto vazio em nil, para
// que seja gravado como NULL.
func normalizeText(text *string) *string {
	if text == nil {
		return nil
	}
	trimmed := strings.TrimSpace(*text)
	if trimmed == "" {
		return nil
	}
	return &trimmed
}
