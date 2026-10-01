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

type postgresLogRepository struct {
	pool *pgxpool.Pool
}

// NewLogRepository cria um LogRepository baseado em Postgres via pgx.
func NewLogRepository(pool *pgxpool.Pool) LogRepository {
	return &postgresLogRepository{pool: pool}
}

// logColumns e logJoins montam a execução junto com o nome do tipo e o nome
// de exibição da planta. Exigem que a tabela ou CTE de execuções tenha o
// alias m.
const (
	logColumns = `
		m.id, m.user_id, m.plant_id, m.type_id, m.created_from_schedule, m.performed_at, m.notes,
		m.created_at, m.updated_at,
		mt.name, ` + plantNameColumn

	logJoins = `
		JOIN maintenance_types mt ON mt.id = m.type_id` + plantNameJoins

	// logReturningColumns é o RETURNING das CTEs de INSERT e UPDATE.
	logReturningColumns = `id, user_id, plant_id, type_id, created_from_schedule, performed_at, notes, created_at, updated_at`
)

// querier abstrai *pgxpool.Pool e pgx.Tx para reaproveitar insertLog.
type querier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

func (r *postgresLogRepository) Create(ctx context.Context, log domain.MaintenanceLog) (domain.MaintenanceLog, error) {
	return insertLog(ctx, r.pool, log)
}

func (r *postgresLogRepository) CreateFromSchedule(ctx context.Context, log domain.MaintenanceLog, scheduleID uuid.UUID) (domain.MaintenanceLog, error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return domain.MaintenanceLog{}, fmt.Errorf("iniciando transação: %w", err)
	}
	defer tx.Rollback(ctx) //nolint:errcheck // sem efeito depois do Commit

	// Excluir primeiro trava o agendamento: duas execuções simultâneas do
	// mesmo agendamento não passam as duas daqui.
	tag, err := tx.Exec(ctx, `DELETE FROM maintenance_schedules WHERE id = $1 AND user_id = $2`, scheduleID, log.UserID)
	if err != nil {
		return domain.MaintenanceLog{}, fmt.Errorf("excluindo agendamento executado: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return domain.MaintenanceLog{}, ErrInvalidSchedule
	}

	created, err := insertLog(ctx, tx, log)
	if err != nil {
		return domain.MaintenanceLog{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return domain.MaintenanceLog{}, fmt.Errorf("confirmando transação: %w", err)
	}

	return created, nil
}

func insertLog(ctx context.Context, q querier, log domain.MaintenanceLog) (domain.MaintenanceLog, error) {
	const query = `
		WITH m AS (
			INSERT INTO maintenance_logs (user_id, plant_id, type_id, created_from_schedule, performed_at, notes)
			VALUES ($1, $2, $3, $4, $5, $6)
			RETURNING ` + logReturningColumns + `
		)
		SELECT ` + logColumns + `
		FROM m ` + logJoins

	created, err := scanLog(q.QueryRow(ctx, query,
		log.UserID, log.PlantID, log.TypeID, log.CreatedFromSchedule, log.PerformedAt, log.Notes))
	if err != nil {
		return domain.MaintenanceLog{}, fmt.Errorf("inserindo execução de manutenção: %w", err)
	}

	return created, nil
}

func (r *postgresLogRepository) FindByID(ctx context.Context, userID, id uuid.UUID) (domain.MaintenanceLog, error) {
	const query = `
		SELECT ` + logColumns + `
		FROM maintenance_logs m ` + logJoins + `
		WHERE m.id = $1 AND m.user_id = $2`

	log, err := scanLog(r.pool.QueryRow(ctx, query, id, userID))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.MaintenanceLog{}, ErrLogNotFound
		}
		return domain.MaintenanceLog{}, fmt.Errorf("buscando execução de manutenção por id: %w", err)
	}

	return log, nil
}

// logListFilter é o WHERE compartilhado por List e pelo count.
const logListFilter = `
	WHERE m.user_id = $1
	AND ($2::uuid IS NULL OR m.plant_id = $2)
	AND ($3::uuid IS NULL OR m.type_id = $3)
	AND ($4::timestamptz IS NULL OR m.performed_at >= $4)
	AND ($5::timestamptz IS NULL OR m.performed_at <= $5)`

func (r *postgresLogRepository) List(ctx context.Context, userID uuid.UUID, filter LogFilter, limit, offset int) ([]domain.MaintenanceLog, int, error) {
	args := []any{userID, filter.PlantID, filter.TypeID, filter.PerformedFrom, filter.PerformedTo}

	var total int
	if err := r.pool.QueryRow(ctx, `SELECT count(*) FROM maintenance_logs m`+logListFilter, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("contando execuções de manutenção: %w", err)
	}

	const query = `
		SELECT ` + logColumns + `
		FROM maintenance_logs m ` + logJoins + logListFilter + `
		ORDER BY m.performed_at DESC, m.created_at DESC
		LIMIT $6 OFFSET $7`

	rows, err := r.pool.Query(ctx, query, append(args, limit, offset)...)
	if err != nil {
		return nil, 0, fmt.Errorf("listando execuções de manutenção: %w", err)
	}
	defer rows.Close()

	logs := make([]domain.MaintenanceLog, 0, limit)
	for rows.Next() {
		log, err := scanLog(rows)
		if err != nil {
			return nil, 0, fmt.Errorf("lendo execução de manutenção: %w", err)
		}
		logs = append(logs, log)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("listando execuções de manutenção: %w", err)
	}

	return logs, total, nil
}

func (r *postgresLogRepository) Update(ctx context.Context, log domain.MaintenanceLog) (domain.MaintenanceLog, error) {
	const query = `
		WITH m AS (
			UPDATE maintenance_logs
			SET plant_id = $3, type_id = $4, performed_at = $5, notes = $6
			WHERE id = $1 AND user_id = $2
			RETURNING ` + logReturningColumns + `
		)
		SELECT ` + logColumns + `
		FROM m ` + logJoins

	updated, err := scanLog(r.pool.QueryRow(ctx, query,
		log.ID, log.UserID, log.PlantID, log.TypeID, log.PerformedAt, log.Notes))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.MaintenanceLog{}, ErrLogNotFound
		}
		return domain.MaintenanceLog{}, fmt.Errorf("atualizando execução de manutenção: %w", err)
	}

	return updated, nil
}

func (r *postgresLogRepository) Delete(ctx context.Context, userID, id uuid.UUID) error {
	tag, err := r.pool.Exec(ctx, `DELETE FROM maintenance_logs WHERE id = $1 AND user_id = $2`, id, userID)
	if err != nil {
		return fmt.Errorf("excluindo execução de manutenção: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return ErrLogNotFound
	}

	return nil
}

// scanLog lê as colunas de logColumns.
func scanLog(row rowScanner) (domain.MaintenanceLog, error) {
	var l domain.MaintenanceLog
	err := row.Scan(
		&l.ID, &l.UserID, &l.PlantID, &l.TypeID, &l.CreatedFromSchedule, &l.PerformedAt, &l.Notes,
		&l.CreatedAt, &l.UpdatedAt,
		&l.TypeName, &l.PlantName,
	)
	return l, err
}
