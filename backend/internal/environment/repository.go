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
//
// Create, FindByID e Update trabalham com a entidade domain.Environment.
// FindViewByID e List são leituras para a API e devolvem EnvironmentView, com
// os dados calculados na consulta.
type Repository interface {
	Create(ctx context.Context, env domain.Environment) (domain.Environment, error)
	FindByID(ctx context.Context, userID, id uuid.UUID) (domain.Environment, error)
	FindViewByID(ctx context.Context, userID, id uuid.UUID) (EnvironmentView, error)
	// List retorna uma página de ambientes do usuário, ordenada por nome, e o
	// total de ambientes que atendem ao filtro (ignorando a paginação).
	// active nil não filtra por status.
	List(ctx context.Context, userID uuid.UUID, active *bool, limit, offset int) ([]EnvironmentView, int, error)
	Update(ctx context.Context, env domain.Environment) (domain.Environment, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
}

const (
	// environmentColumns são as colunas da entidade, lidas por scanEnvironment.
	environmentColumns = `id, user_id, name, notes, active, created_at, updated_at`

	// viewColumns são as colunas de EnvironmentView, lidas por
	// scanEnvironmentView. A contagem considera só plantas ativas e exige que a
	// tabela tenha o nome environments.
	viewColumns = environmentColumns + `,
		(SELECT count(*) FROM plants WHERE plants.environment_id = environments.id AND plants.active)`
)

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
		RETURNING ` + environmentColumns

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

func (r *postgresRepository) FindViewByID(ctx context.Context, userID, id uuid.UUID) (EnvironmentView, error) {
	const query = `
		SELECT ` + viewColumns + `
		FROM environments
		WHERE id = $1 AND user_id = $2
	`

	view, err := scanEnvironmentView(r.pool.QueryRow(ctx, query, id, userID))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return EnvironmentView{}, ErrEnvironmentNotFound
		}
		return EnvironmentView{}, fmt.Errorf("buscando ambiente por id: %w", err)
	}

	return view, nil
}

func (r *postgresRepository) List(ctx context.Context, userID uuid.UUID, active *bool, limit, offset int) ([]EnvironmentView, int, error) {
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
		SELECT ` + viewColumns + `
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

	views := make([]EnvironmentView, 0, limit)
	for rows.Next() {
		view, err := scanEnvironmentView(rows)
		if err != nil {
			return nil, 0, fmt.Errorf("lendo ambiente: %w", err)
		}
		views = append(views, view)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("listando ambientes: %w", err)
	}

	return views, total, nil
}

func (r *postgresRepository) Update(ctx context.Context, env domain.Environment) (domain.Environment, error) {
	const query = `
		UPDATE environments
		SET name = $3, notes = $4, active = $5
		WHERE id = $1 AND user_id = $2
		RETURNING ` + environmentColumns

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

// rowScanner abstrai pgx.Row e pgx.Rows para reaproveitar os scans.
type rowScanner interface {
	Scan(dest ...any) error
}

// scanEnvironment lê as colunas de environmentColumns.
func scanEnvironment(row rowScanner) (domain.Environment, error) {
	var e domain.Environment
	err := row.Scan(&e.ID, &e.UserID, &e.Name, &e.Notes, &e.Active, &e.CreatedAt, &e.UpdatedAt)
	if err != nil {
		return domain.Environment{}, err
	}
	return e, nil
}

// scanEnvironmentView lê as colunas de viewColumns.
func scanEnvironmentView(row rowScanner) (EnvironmentView, error) {
	var v EnvironmentView
	err := row.Scan(&v.ID, &v.UserID, &v.Name, &v.Notes, &v.Active, &v.CreatedAt, &v.UpdatedAt, &v.PlantCount)
	if err != nil {
		return EnvironmentView{}, err
	}
	return v, nil
}
