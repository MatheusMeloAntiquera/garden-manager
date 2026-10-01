package domain

import (
	"time"

	"github.com/google/uuid"
)

// MaintenanceType é um procedimento do catálogo de manutenções (rega, poda,
// adubação...). O catálogo é populado por migration.
type MaintenanceType struct {
	ID        uuid.UUID
	Name      string
	CreatedAt time.Time
	UpdatedAt time.Time
}

// MaintenanceStatus é a situação de um agendamento, calculada a partir do
// prazo. Agendamentos executados não aparecem: são excluídos ao registrar a
// execução.
type MaintenanceStatus string

// Situações possíveis de um agendamento.
const (
	MaintenanceStatusPending MaintenanceStatus = "pending" // dentro do prazo
	MaintenanceStatusOverdue MaintenanceStatus = "overdue" // prazo vencido
)

// MaintenanceSchedule é uma manutenção planejada para uma planta. Ao ser
// executada, vira um MaintenanceLog e o agendamento é excluído.
type MaintenanceSchedule struct {
	ID      uuid.UUID
	UserID  uuid.UUID
	PlantID uuid.UUID
	TypeID  uuid.UUID
	DueAt   time.Time // prazo para execução
	Notes   *string

	// TypeName e PlantName só são preenchidos nas leituras do repositório
	// (que fazem join).
	TypeName  string
	PlantName string

	CreatedAt time.Time
	UpdatedAt time.Time
}

// Status calcula a situação do agendamento no instante now.
func (s MaintenanceSchedule) Status(now time.Time) MaintenanceStatus {
	if s.DueAt.Before(now) {
		return MaintenanceStatusOverdue
	}
	return MaintenanceStatusPending
}

// MaintenanceLog é uma manutenção executada em uma planta, a partir de um
// agendamento ou sem planejamento.
type MaintenanceLog struct {
	ID      uuid.UUID
	UserID  uuid.UUID
	PlantID uuid.UUID
	TypeID  uuid.UUID

	// CreatedFromSchedule indica que a execução nasceu de um agendamento
	// (que foi excluído ao registrá-la).
	CreatedFromSchedule bool

	PerformedAt time.Time
	Notes       *string

	// TypeName e PlantName só são preenchidos nas leituras do repositório.
	TypeName  string
	PlantName string

	CreatedAt time.Time
	UpdatedAt time.Time
}
