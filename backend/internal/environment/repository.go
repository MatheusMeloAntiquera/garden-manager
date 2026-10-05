package environment

import (
	"context"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/matheusantiquera/garden-manager/backend/domain"
)

// Repository dá acesso aos dados de ambientes. Todas as operações recebem o
// ID do usuário dono, para que um usuário nunca enxergue ambientes de outro.
type Repository interface {
	Create(ctx context.Context, env domain.Environment) (domain.Environment, error)
	FindByID(ctx context.Context, userID, id uuid.UUID) (domain.Environment, error)
	// List retorna uma página de ambientes do usuário, ordenada por nome, e o
	// total de ambientes que atendem ao filtro (ignorando a paginação).
	// active nil não filtra por status.
	List(ctx context.Context, userID uuid.UUID, active *bool, limit, offset int) ([]domain.Environment, int, error)
	Update(ctx context.Context, env domain.Environment) (domain.Environment, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
}

// environmentColumns lista as colunas lidas de um ambiente. plant_count é a
// contagem de plantas ativas e exige que a tabela tenha o nome environments.
const environmentColumns = `id, user_id, name, notes, active,
		(SELECT count(*) FROM plants WHERE plants.environment_id = environments.id AND plants.active),
		created_at, updated_at`

type postgresRepository struct {
	pool *pgxpool.Pool
}

// NewRepository cria um Repository baseado em Postgres via pgx.
func NewRepository(pool *pgxpool.Pool) Repository {
	return &postgresRepository{pool: pool}
}

func (r *postgresRepository) Create(ctx context.Context, env domain.Environment) (domain.Environment, error) {
	const query = `
		INSERT INTO environments (user_id, name, notes, active)
		VALUES ($1, $2, $3, $4)
		RETURNING ` + environmentColumns + `
	`

	created, err := scanEnvironment(r.pool.QueryRow(ctx, query, env.UserID, env.Name, env.Notes, env.Active))
	if err != nil {
		return domain.Environment{}, fmt.Errorf("inserindo ambiente: %w", err)
	}

	return created, nil
}

func (r *postgresRepository) FindByID(ctx context.Context, userID, id uuid.UUID) (domain.Environment, error) {
	const query = `
		SELECT ` + environmentColumns + `
		FROM environments
		WHERE id = $1 AND user_id = $2
	`

	env, err := scanEnvironment(r.pool.QueryRow(ctx, query, id, userID))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Environment{}, ErrEnvironmentNotFound
		}
		return domain.Environment{}, fmt.Errorf("buscando ambiente por id: %w", err)
	}

	return env, nil
}

func (r *postgresRepository) List(ctx context.Context, userID uuid.UUID, active *bool, limit, offset int) ([]domain.Environment, int, error) {
	const countQuery = `
		SELECT count(*)
		FROM environments
		WHERE user_id = $1 AND ($2::boolean IS NULL OR active = $2)
	`

	var total int
	if err := r.pool.QueryRow(ctx, countQuery, userID, active).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("contando ambientes: %w", err)
	}

	const query = `
		SELECT ` + environmentColumns + `
		FROM environments
		WHERE user_id = $1 AND ($2::boolean IS NULL OR active = $2)
		ORDER BY name, created_at
		LIMIT $3 OFFSET $4
	`

	rows, err := r.pool.Query(ctx, query, userID, active, limit, offset)
	if err != nil {
		return nil, 0, fmt.Errorf("listando ambientes: %w", err)
	}
	defer rows.Close()

	envs := make([]domain.Environment, 0, limit)
	for rows.Next() {
		env, err := scanEnvironment(rows)
		if err != nil {
			return nil, 0, fmt.Errorf("lendo ambiente: %w", err)
		}
		envs = append(envs, env)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("listando ambientes: %w", err)
	}

	return envs, total, nil
}

func (r *postgresRepository) Update(ctx context.Context, env domain.Environment) (domain.Environment, error) {
	const query = `
		UPDATE environments
		SET name = $3, notes = $4, active = $5
		WHERE id = $1 AND user_id = $2
		RETURNING ` + environmentColumns + `
	`

	updated, err := scanEnvironment(r.pool.QueryRow(ctx, query, env.ID, env.UserID, env.Name, env.Notes, env.Active))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Environment{}, ErrEnvironmentNotFound
		}
		return domain.Environment{}, fmt.Errorf("atualizando ambiente: %w", err)
	}

	return updated, nil
}

func (r *postgresRepository) Delete(ctx context.Context, userID, id uuid.UUID) error {
	const query = `DELETE FROM environments WHERE id = $1 AND user_id = $2`

	tag, err := r.pool.Exec(ctx, query, id, userID)
	if err != nil {
		return fmt.Errorf("excluindo ambiente: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return ErrEnvironmentNotFound
	}

	return nil
}

// rowScanner abstrai pgx.Row e pgx.Rows para reaproveitar scanEnvironment.
type rowScanner interface {
	Scan(dest ...any) error
}

func scanEnvironment(row rowScanner) (domain.Environment, error) {
	var e domain.Environment
	err := row.Scan(&e.ID, &e.UserID, &e.Name, &e.Notes, &e.Active, &e.PlantCount, &e.CreatedAt, &e.UpdatedAt)
	if err != nil {
		return domain.Environment{}, err
	}
	return e, nil
}
