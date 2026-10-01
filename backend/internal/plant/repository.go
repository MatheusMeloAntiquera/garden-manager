package plant

import (
	"context"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/matheusantiquera/garden-manager/backend/domain"
)

// Repository dá acesso aos dados de plantas. Todas as operações recebem o ID
// do usuário dono, para que um usuário nunca enxergue plantas de outro. As
// leituras (e o retorno de Create e Update) já trazem o resumo da espécie e
// do ambiente.
type Repository interface {
	Create(ctx context.Context, plant domain.Plant) (domain.Plant, error)
	FindByID(ctx context.Context, userID, id uuid.UUID) (domain.Plant, error)
	// List retorna uma página de plantas do usuário, ordenada pelo nome de
	// exibição, e o total que atende aos filtros (ignorando a paginação).
	// Filtros nulos não restringem o resultado.
	List(ctx context.Context, userID uuid.UUID, environmentID, speciesID *uuid.UUID, active *bool, limit, offset int) ([]domain.Plant, int, error)
	Update(ctx context.Context, plant domain.Plant) (domain.Plant, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
}

type postgresRepository struct {
	pool *pgxpool.Pool
}

// NewRepository cria um Repository baseado em Postgres via pgx.
func NewRepository(pool *pgxpool.Pool) Repository {
	return &postgresRepository{pool: pool}
}

// plantColumns e plantJoins montam a planta junto com o resumo da espécie
// (com o nome popular principal) e do ambiente. Exigem que a tabela ou CTE
// de plantas tenha o alias p. O join com environments confere o user_id
// como defesa extra contra vincular o ambiente de outro usuário.
const (
	plantColumns = `
		p.id, p.user_id, p.species_id, p.environment_id, p.nickname, p.notes, p.active, p.created_at, p.updated_at,
		s.id, s.scientific_name, cn.name,
		e.id, e.name`

	plantJoins = `
		LEFT JOIN species s ON s.id = p.species_id
		LEFT JOIN species_common_names cn ON cn.species_id = s.id AND cn.is_primary
		LEFT JOIN environments e ON e.id = p.environment_id AND e.user_id = p.user_id`

	// returningColumns é o RETURNING das CTEs de INSERT e UPDATE.
	returningColumns = `id, user_id, species_id, environment_id, nickname, notes, active, created_at, updated_at`
)

func (r *postgresRepository) Create(ctx context.Context, plant domain.Plant) (domain.Plant, error) {
	const query = `
		WITH p AS (
			INSERT INTO plants (user_id, species_id, environment_id, nickname, notes, active)
			VALUES ($1, $2, $3, $4, $5, $6)
			RETURNING ` + returningColumns + `
		)
		SELECT ` + plantColumns + `
		FROM p ` + plantJoins

	created, err := scanPlant(r.pool.QueryRow(ctx, query,
		plant.UserID, plant.SpeciesID, plant.EnvironmentID, plant.Nickname, plant.Notes, plant.Active))
	if err != nil {
		return domain.Plant{}, fmt.Errorf("inserindo planta: %w", err)
	}

	return created, nil
}

func (r *postgresRepository) FindByID(ctx context.Context, userID, id uuid.UUID) (domain.Plant, error) {
	const query = `
		SELECT ` + plantColumns + `
		FROM plants p ` + plantJoins + `
		WHERE p.id = $1 AND p.user_id = $2`

	plant, err := scanPlant(r.pool.QueryRow(ctx, query, id, userID))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Plant{}, ErrPlantNotFound
		}
		return domain.Plant{}, fmt.Errorf("buscando planta por id: %w", err)
	}

	return plant, nil
}

// listFilter é o WHERE compartilhado por List e pelo count.
const listFilter = `
	WHERE p.user_id = $1
	AND ($2::uuid IS NULL OR p.environment_id = $2)
	AND ($3::uuid IS NULL OR p.species_id = $3)
	AND ($4::boolean IS NULL OR p.active = $4)`

func (r *postgresRepository) List(ctx context.Context, userID uuid.UUID, environmentID, speciesID *uuid.UUID, active *bool, limit, offset int) ([]domain.Plant, int, error) {
	var total int
	err := r.pool.QueryRow(ctx, `SELECT count(*) FROM plants p`+listFilter, userID, environmentID, speciesID, active).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("contando plantas: %w", err)
	}

	const query = `
		SELECT ` + plantColumns + `
		FROM plants p ` + plantJoins + listFilter + `
		ORDER BY lower(coalesce(p.nickname, cn.name, s.scientific_name)), p.created_at
		LIMIT $5 OFFSET $6`

	rows, err := r.pool.Query(ctx, query, userID, environmentID, speciesID, active, limit, offset)
	if err != nil {
		return nil, 0, fmt.Errorf("listando plantas: %w", err)
	}
	defer rows.Close()

	plants := make([]domain.Plant, 0, limit)
	for rows.Next() {
		plant, err := scanPlant(rows)
		if err != nil {
			return nil, 0, fmt.Errorf("lendo planta: %w", err)
		}
		plants = append(plants, plant)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("listando plantas: %w", err)
	}

	return plants, total, nil
}

func (r *postgresRepository) Update(ctx context.Context, plant domain.Plant) (domain.Plant, error) {
	const query = `
		WITH p AS (
			UPDATE plants
			SET species_id = $3, environment_id = $4, nickname = $5, notes = $6, active = $7
			WHERE id = $1 AND user_id = $2
			RETURNING ` + returningColumns + `
		)
		SELECT ` + plantColumns + `
		FROM p ` + plantJoins

	updated, err := scanPlant(r.pool.QueryRow(ctx, query,
		plant.ID, plant.UserID, plant.SpeciesID, plant.EnvironmentID, plant.Nickname, plant.Notes, plant.Active))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Plant{}, ErrPlantNotFound
		}
		return domain.Plant{}, fmt.Errorf("atualizando planta: %w", err)
	}

	return updated, nil
}

func (r *postgresRepository) Delete(ctx context.Context, userID, id uuid.UUID) error {
	const query = `DELETE FROM plants WHERE id = $1 AND user_id = $2`

	tag, err := r.pool.Exec(ctx, query, id, userID)
	if err != nil {
		return fmt.Errorf("excluindo planta: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return ErrPlantNotFound
	}

	return nil
}

// rowScanner abstrai pgx.Row e pgx.Rows para reaproveitar scanPlant.
type rowScanner interface {
	Scan(dest ...any) error
}

// scanPlant lê as colunas de plantColumns. Os campos do join de espécie e de
// ambiente vêm nulos quando a planta não tem espécie ou ambiente.
func scanPlant(row rowScanner) (domain.Plant, error) {
	var (
		p                domain.Plant
		speciesRefID     *uuid.UUID
		scientificName   *string
		commonName       *string
		environmentRefID *uuid.UUID
		environmentName  *string
	)

	err := row.Scan(
		&p.ID, &p.UserID, &p.SpeciesID, &p.EnvironmentID, &p.Nickname, &p.Notes, &p.Active, &p.CreatedAt, &p.UpdatedAt,
		&speciesRefID, &scientificName, &commonName,
		&environmentRefID, &environmentName,
	)
	if err != nil {
		return domain.Plant{}, err
	}

	if speciesRefID != nil {
		p.Species = &domain.PlantSpecies{ID: *speciesRefID, ScientificName: *scientificName, CommonName: commonName}
	}
	if environmentRefID != nil {
		p.Environment = &domain.PlantEnvironment{ID: *environmentRefID, Name: *environmentName}
	}

	return p, nil
}
