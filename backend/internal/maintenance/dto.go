package maintenance

import (
	"time"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/datetime"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
)

// ScheduleInput é o corpo esperado por POST /maintenance-schedules e por
// PUT /maintenance-schedules/{id}. Como o PUT substitui o registro, os dois
// usam os mesmos campos.
type ScheduleInput struct {
	PlantID *uuid.UUID              `json:"plant_id" validate:"required"`
	TypeID  *uuid.UUID              `json:"type_id" validate:"required"`
	DueAt   *datetime.LocalDateTime `json:"due_at" validate:"required"`
	Notes   *string                 `json:"notes" validate:"omitempty,max=2000"`
}

// CreateLogInput é o corpo esperado por POST /maintenance-logs. Quando
// schedule_id é informado, a execução registra aquele agendamento, que é
// excluído; plant_id, type_id e notes omitidos são herdados dele.
type CreateLogInput struct {
	PlantID     *uuid.UUID              `json:"plant_id" validate:"required_without=ScheduleID"`
	TypeID      *uuid.UUID              `json:"type_id" validate:"required_without=ScheduleID"`
	ScheduleID  *uuid.UUID              `json:"schedule_id"`
	PerformedAt *datetime.LocalDateTime `json:"performed_at" validate:"required"`
	Notes       *string                 `json:"notes" validate:"omitempty,max=2000"`
}

// UpdateLogInput é o corpo esperado por PUT /maintenance-logs/{id}.
// created_from_schedule é definido só na criação e não pode ser alterado.
type UpdateLogInput struct {
	PlantID     *uuid.UUID              `json:"plant_id" validate:"required"`
	TypeID      *uuid.UUID              `json:"type_id" validate:"required"`
	PerformedAt *datetime.LocalDateTime `json:"performed_at" validate:"required"`
	Notes       *string                 `json:"notes" validate:"omitempty,max=2000"`
}

// ListSchedulesInput reúne os parâmetros de GET /maintenance-schedules.
type ListSchedulesInput struct {
	pagination.Params
	PlantID *uuid.UUID
	TypeID  *uuid.UUID
	Status  *domain.MaintenanceStatus
	DueFrom *datetime.LocalDateTime
	DueTo   *datetime.LocalDateTime
}

// ListLogsInput reúne os parâmetros de GET /maintenance-logs.
type ListLogsInput struct {
	pagination.Params
	PlantID       *uuid.UUID
	TypeID        *uuid.UUID
	PerformedFrom *datetime.LocalDateTime
	PerformedTo   *datetime.LocalDateTime
}

// TypeResponse é a representação pública de um tipo de manutenção.
type TypeResponse struct {
	ID   uuid.UUID `json:"id"`
	Name string    `json:"name"`
}

// TypeListResponse é o corpo retornado por GET /maintenance-types.
type TypeListResponse struct {
	Data []TypeResponse `json:"data"`
}

// PlantRef é o resumo da planta dentro das respostas de manutenção.
type PlantRef struct {
	ID          uuid.UUID `json:"id"`
	DisplayName string    `json:"display_name"`
}

// ScheduleResponse é a representação pública de um agendamento.
type ScheduleResponse struct {
	ID        uuid.UUID                `json:"id"`
	Plant     PlantRef                 `json:"plant"`
	Type      TypeResponse             `json:"type"`
	DueAt     datetime.LocalDateTime   `json:"due_at"`
	Notes     *string                  `json:"notes"`
	Status    domain.MaintenanceStatus `json:"status"`
	CreatedAt time.Time                `json:"created_at"`
	UpdatedAt time.Time                `json:"updated_at"`
}

// NewScheduleResponse converte um domain.MaintenanceSchedule em
// ScheduleResponse, calculando o status no instante now.
func NewScheduleResponse(s domain.MaintenanceSchedule, now time.Time) ScheduleResponse {
	return ScheduleResponse{
		ID:        s.ID,
		Plant:     PlantRef{ID: s.PlantID, DisplayName: s.PlantName},
		Type:      TypeResponse{ID: s.TypeID, Name: s.TypeName},
		DueAt:     datetime.New(s.DueAt),
		Notes:     s.Notes,
		Status:    s.Status(now),
		CreatedAt: s.CreatedAt,
		UpdatedAt: s.UpdatedAt,
	}
}

// LogResponse é a representação pública de uma execução.
type LogResponse struct {
	ID                  uuid.UUID              `json:"id"`
	Plant               PlantRef               `json:"plant"`
	Type                TypeResponse           `json:"type"`
	CreatedFromSchedule bool                   `json:"created_from_schedule"`
	PerformedAt         datetime.LocalDateTime `json:"performed_at"`
	Notes               *string                `json:"notes"`
	CreatedAt           time.Time              `json:"created_at"`
	UpdatedAt           time.Time              `json:"updated_at"`
}

// NewLogResponse converte um domain.MaintenanceLog em LogResponse.
func NewLogResponse(l domain.MaintenanceLog) LogResponse {
	return LogResponse{
		ID:                  l.ID,
		Plant:               PlantRef{ID: l.PlantID, DisplayName: l.PlantName},
		Type:                TypeResponse{ID: l.TypeID, Name: l.TypeName},
		CreatedFromSchedule: l.CreatedFromSchedule,
		PerformedAt:         datetime.New(l.PerformedAt),
		Notes:               l.Notes,
		CreatedAt:           l.CreatedAt,
		UpdatedAt:           l.UpdatedAt,
	}
}

// ListResponse é o corpo das listagens paginadas de agendamentos e execuções.
type ListResponse[T any] struct {
	Data     []T `json:"data"`
	Page     int `json:"page"`
	PageSize int `json:"page_size"`
	Total    int `json:"total"`
}
