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

type postgresTypeRepository struct {
	pool *pgxpool.Pool
}

// NewTypeRepository cria um TypeRepository baseado em Postgres via pgx.
func NewTypeRepository(pool *pgxpool.Pool) TypeRepository {
	return &postgresTypeRepository{pool: pool}
}

const typeColumns = `id, name, created_at, updated_at`

func (r *postgresTypeRepository) List(ctx context.Context) ([]domain.MaintenanceType, error) {
	rows, err := r.pool.Query(ctx, `SELECT `+typeColumns+` FROM maintenance_types ORDER BY name`)
	if err != nil {
		return nil, fmt.Errorf("listando tipos de manutenção: %w", err)
	}
	defer rows.Close()

	types := make([]domain.MaintenanceType, 0)
	for rows.Next() {
		t, err := scanType(rows)
		if err != nil {
			return nil, fmt.Errorf("lendo tipo de manutenção: %w", err)
		}
		types = append(types, t)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("listando tipos de manutenção: %w", err)
	}

	return types, nil
}

func (r *postgresTypeRepository) FindByID(ctx context.Context, id uuid.UUID) (domain.MaintenanceType, error) {
	t, err := scanType(r.pool.QueryRow(ctx, `SELECT `+typeColumns+` FROM maintenance_types WHERE id = $1`, id))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.MaintenanceType{}, ErrTypeNotFound
		}
		return domain.MaintenanceType{}, fmt.Errorf("buscando tipo de manutenção por id: %w", err)
	}

	return t, nil
}

func scanType(row rowScanner) (domain.MaintenanceType, error) {
	var t domain.MaintenanceType
	err := row.Scan(&t.ID, &t.Name, &t.CreatedAt, &t.UpdatedAt)
	return t, err
}
