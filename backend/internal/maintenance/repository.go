package maintenance

import (
	"context"
	"time"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
)

// TypeRepository dá acesso ao catálogo de tipos de manutenção (somente
// leitura; o catálogo é populado por migration).
type TypeRepository interface {
	// List retorna todos os tipos, ordenados por nome.
	List(ctx context.Context) ([]domain.MaintenanceType, error)
	FindByID(ctx context.Context, id uuid.UUID) (domain.MaintenanceType, error)
}

// ScheduleFilter reúne os filtros da listagem de agendamentos. Filtros nulos
// não restringem o resultado.
type ScheduleFilter struct {
	PlantID *uuid.UUID
	TypeID  *uuid.UUID
	Status  *domain.MaintenanceStatus
	DueFrom *time.Time
	DueTo   *time.Time
}

// ScheduleRepository dá acesso aos agendamentos. Todas as operações recebem
// o ID do usuário dono, para que um usuário nunca enxergue registros de
// outro. As leituras (e o retorno de Create e Update) já trazem o nome do
// tipo e o nome de exibição da planta.
type ScheduleRepository interface {
	Create(ctx context.Context, schedule domain.MaintenanceSchedule) (domain.MaintenanceSchedule, error)
	FindByID(ctx context.Context, userID, id uuid.UUID) (domain.MaintenanceSchedule, error)
	// List retorna uma página de agendamentos do usuário, ordenada pelo
	// prazo, e o total que atende aos filtros (ignorando a paginação).
	List(ctx context.Context, userID uuid.UUID, filter ScheduleFilter, limit, offset int) ([]domain.MaintenanceSchedule, int, error)
	Update(ctx context.Context, schedule domain.MaintenanceSchedule) (domain.MaintenanceSchedule, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
}

// LogFilter reúne os filtros da listagem de execuções. Filtros nulos não
// restringem o resultado.
type LogFilter struct {
	PlantID       *uuid.UUID
	TypeID        *uuid.UUID
	PerformedFrom *time.Time
	PerformedTo   *time.Time
}

// LogRepository dá acesso às execuções, com as mesmas garantias de
// ScheduleRepository.
type LogRepository interface {
	Create(ctx context.Context, log domain.MaintenanceLog) (domain.MaintenanceLog, error)
	// CreateFromSchedule grava a execução e exclui o agendamento scheduleID
	// do mesmo usuário, numa única transação. Retorna ErrInvalidSchedule se
	// o agendamento não existir mais (por exemplo, se já foi executado).
	CreateFromSchedule(ctx context.Context, log domain.MaintenanceLog, scheduleID uuid.UUID) (domain.MaintenanceLog, error)
	FindByID(ctx context.Context, userID, id uuid.UUID) (domain.MaintenanceLog, error)
	// List retorna uma página de execuções do usuário, da mais recente para a
	// mais antiga, e o total que atende aos filtros.
	List(ctx context.Context, userID uuid.UUID, filter LogFilter, limit, offset int) ([]domain.MaintenanceLog, int, error)
	// Update altera planta, tipo, data e observações; created_from_schedule
	// não muda.
	Update(ctx context.Context, log domain.MaintenanceLog) (domain.MaintenanceLog, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
}

// plantNameJoins monta o nome de exibição da planta (apelido, nome popular
// principal ou nome científico, como plant.DisplayName) a partir de uma
// tabela ou CTE com a coluna plant_id e o alias m. Usado com plantNameColumn.
const (
	plantNameJoins = `
		JOIN plants p ON p.id = m.plant_id
		LEFT JOIN species sp ON sp.id = p.species_id
		LEFT JOIN species_common_names cn ON cn.species_id = sp.id AND cn.is_primary`

	plantNameColumn = `coalesce(p.nickname, cn.name, sp.scientific_name, '')`
)

// rowScanner abstrai pgx.Row e pgx.Rows para reaproveitar as funções de scan.
type rowScanner interface {
	Scan(dest ...any) error
}
