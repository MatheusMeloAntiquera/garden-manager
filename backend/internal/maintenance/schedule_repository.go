package maintenance

import (
	"context"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/matheusantiquera/garden-manager/backend/domain"
)

type postgresScheduleRepository struct {
	pool *pgxpool.Pool
}

// NewScheduleRepository cria um ScheduleRepository baseado em Postgres via
// pgx.
func NewScheduleRepository(pool *pgxpool.Pool) ScheduleRepository {
	return &postgresScheduleRepository{pool: pool}
}

// scheduleColumns e scheduleJoins montam o agendamento junto com o nome do
// tipo e o nome de exibição da planta. Exigem que a tabela ou CTE de
// agendamentos tenha o alias m.
const (
	scheduleColumns = `
		m.id, m.user_id, m.plant_id, m.type_id, m.due_at, m.notes, m.created_at, m.updated_at,
		mt.name, ` + plantNameColumn

	scheduleJoins = `
		JOIN maintenance_types mt ON mt.id = m.type_id` + plantNameJoins

	// scheduleReturningColumns é o RETURNING das CTEs de INSERT e UPDATE.
	scheduleReturningColumns = `id, user_id, plant_id, type_id, due_at, notes, created_at, updated_at`
)

func (r *postgresScheduleRepository) Create(ctx context.Context, schedule domain.MaintenanceSchedule) (domain.MaintenanceSchedule, error) {
	const query = `
		WITH m AS (
			INSERT INTO maintenance_schedules (user_id, plant_id, type_id, due_at, notes)
			VALUES ($1, $2, $3, $4, $5)
			RETURNING ` + scheduleReturningColumns + `
		)
		SELECT ` + scheduleColumns + `
		FROM m ` + scheduleJoins

	created, err := scanSchedule(r.pool.QueryRow(ctx, query,
		schedule.UserID, schedule.PlantID, schedule.TypeID, schedule.DueAt, schedule.Notes))
	if err != nil {
		return domain.MaintenanceSchedule{}, fmt.Errorf("inserindo agendamento de manutenção: %w", err)
	}

	return created, nil
}

func (r *postgresScheduleRepository) FindByID(ctx context.Context, userID, id uuid.UUID) (domain.MaintenanceSchedule, error) {
	const query = `
		SELECT ` + scheduleColumns + `
		FROM maintenance_schedules m ` + scheduleJoins + `
		WHERE m.id = $1 AND m.user_id = $2`

	schedule, err := scanSchedule(r.pool.QueryRow(ctx, query, id, userID))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.MaintenanceSchedule{}, ErrScheduleNotFound
		}
		return domain.MaintenanceSchedule{}, fmt.Errorf("buscando agendamento de manutenção por id: %w", err)
	}

	return schedule, nil
}

// scheduleListFilter é o WHERE compartilhado por List e pelo count.
const scheduleListFilter = `
	WHERE m.user_id = $1
	AND ($2::uuid IS NULL OR m.plant_id = $2)
	AND ($3::uuid IS NULL OR m.type_id = $3)
	AND ($4::text IS NULL
		OR ($4 = 'overdue' AND m.due_at < now())
		OR ($4 = 'pending' AND m.due_at >= now()))
	AND ($5::timestamptz IS NULL OR m.due_at >= $5)
	AND ($6::timestamptz IS NULL OR m.due_at <= $6)
	AND ($7::bool IS NULL OR EXISTS (SELECT 1 FROM plants p WHERE p.id = m.plant_id AND p.active = $7))`

func (r *postgresScheduleRepository) List(ctx context.Context, userID uuid.UUID, filter ScheduleFilter, limit, offset int) ([]domain.MaintenanceSchedule, int, error) {
	var status *string
	if filter.Status != nil {
		s := string(*filter.Status)
		status = &s
	}
	args := []any{userID, filter.PlantID, filter.TypeID, status, filter.DueFrom, filter.DueTo, filter.PlantActive}

	var total int
	if err := r.pool.QueryRow(ctx, `SELECT count(*) FROM maintenance_schedules m`+scheduleListFilter, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("contando agendamentos de manutenção: %w", err)
	}

	const query = `
		SELECT ` + scheduleColumns + `
		FROM maintenance_schedules m ` + scheduleJoins + scheduleListFilter + `
		ORDER BY m.due_at, m.created_at
		LIMIT $8 OFFSET $9`

	rows, err := r.pool.Query(ctx, query, append(args, limit, offset)...)
	if err != nil {
		return nil, 0, fmt.Errorf("listando agendamentos de manutenção: %w", err)
	}
	defer rows.Close()

	schedules := make([]domain.MaintenanceSchedule, 0, limit)
	for rows.Next() {
		schedule, err := scanSchedule(rows)
		if err != nil {
			return nil, 0, fmt.Errorf("lendo agendamento de manutenção: %w", err)
		}
		schedules = append(schedules, schedule)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("listando agendamentos de manutenção: %w", err)
	}

	return schedules, total, nil
}

func (r *postgresScheduleRepository) Update(ctx context.Context, schedule domain.MaintenanceSchedule) (domain.MaintenanceSchedule, error) {
	const query = `
		WITH m AS (
			UPDATE maintenance_schedules
			SET plant_id = $3, type_id = $4, due_at = $5, notes = $6
			WHERE id = $1 AND user_id = $2
			RETURNING ` + scheduleReturningColumns + `
		)
		SELECT ` + scheduleColumns + `
		FROM m ` + scheduleJoins

	updated, err := scanSchedule(r.pool.QueryRow(ctx, query,
		schedule.ID, schedule.UserID, schedule.PlantID, schedule.TypeID, schedule.DueAt, schedule.Notes))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.MaintenanceSchedule{}, ErrScheduleNotFound
		}
		return domain.MaintenanceSchedule{}, fmt.Errorf("atualizando agendamento de manutenção: %w", err)
	}

	return updated, nil
}

func (r *postgresScheduleRepository) Delete(ctx context.Context, userID, id uuid.UUID) error {
	tag, err := r.pool.Exec(ctx, `DELETE FROM maintenance_schedules WHERE id = $1 AND user_id = $2`, id, userID)
	if err != nil {
		return fmt.Errorf("excluindo agendamento de manutenção: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return ErrScheduleNotFound
	}

	return nil
}

// scanSchedule lê as colunas de scheduleColumns.
func scanSchedule(row rowScanner) (domain.MaintenanceSchedule, error) {
	var s domain.MaintenanceSchedule
	err := row.Scan(
		&s.ID, &s.UserID, &s.PlantID, &s.TypeID, &s.DueAt, &s.Notes, &s.CreatedAt, &s.UpdatedAt,
		&s.TypeName, &s.PlantName,
	)
	return s, err
}
