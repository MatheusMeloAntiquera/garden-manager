package species

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/matheusantiquera/garden-manager/backend/domain"
)

// Repository dá acesso, somente leitura, ao catálogo de espécies.
type Repository interface {
	// List retorna uma página de espécies, ordenada pelo nome popular
	// principal, e o total que atende aos filtros (ignorando a paginação).
	// query e category vazios não filtram.
	List(ctx context.Context, query, category string, limit, offset int) ([]domain.Species, int, error)
	FindByID(ctx context.Context, id uuid.UUID) (domain.Species, error)
}

type postgresRepository struct {
	pool *pgxpool.Pool
}

// NewRepository cria um Repository baseado em Postgres via pgx.
func NewRepository(pool *pgxpool.Pool) Repository {
	return &postgresRepository{pool: pool}
}

// listFilter é o WHERE compartilhado por List e pelo count. $1 é o texto da
// busca já escapado para LIKE (vazio = sem busca) e $2 é a categoria (vazia =
// todas). O EXISTS evita repetir a espécie quando vários nomes dela casam
// com a busca.
const listFilter = `
	WHERE ($2::text = '' OR s.category = $2)
	AND ($1::text = '' OR unaccent(lower(s.scientific_name)) LIKE '%' || unaccent(lower($1)) || '%'
		OR EXISTS (
			SELECT 1 FROM species_common_names n
			WHERE n.species_id = s.id
			AND unaccent(lower(n.name)) LIKE '%' || unaccent(lower($1)) || '%'
		))
`

func (r *postgresRepository) List(ctx context.Context, query, category string, limit, offset int) ([]domain.Species, int, error) {
	pattern := escapeLike(query)

	var total int
	if err := r.pool.QueryRow(ctx, `SELECT count(*) FROM species s`+listFilter, pattern, category).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("contando espécies: %w", err)
	}

	const listQuery = `
		SELECT s.id, s.scientific_name, s.family, s.category, s.gbif_key, s.created_at, s.updated_at
		FROM species s
		LEFT JOIN species_common_names p ON p.species_id = s.id AND p.is_primary
	` + listFilter + `
		ORDER BY unaccent(lower(coalesce(p.name, s.scientific_name))), s.scientific_name
		LIMIT $3 OFFSET $4
	`

	rows, err := r.pool.Query(ctx, listQuery, pattern, category, limit, offset)
	if err != nil {
		return nil, 0, fmt.Errorf("listando espécies: %w", err)
	}
	defer rows.Close()

	species := make([]domain.Species, 0, limit)
	for rows.Next() {
		s, err := scanSpecies(rows)
		if err != nil {
			return nil, 0, fmt.Errorf("lendo espécie: %w", err)
		}
		species = append(species, s)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("listando espécies: %w", err)
	}

	if err := r.loadCommonNames(ctx, species); err != nil {
		return nil, 0, err
	}

	return species, total, nil
}

func (r *postgresRepository) FindByID(ctx context.Context, id uuid.UUID) (domain.Species, error) {
	const query = `
		SELECT id, scientific_name, family, category, gbif_key, created_at, updated_at
		FROM species
		WHERE id = $1
	`

	s, err := scanSpecies(r.pool.QueryRow(ctx, query, id))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Species{}, ErrSpeciesNotFound
		}
		return domain.Species{}, fmt.Errorf("buscando espécie por id: %w", err)
	}

	species := []domain.Species{s}
	if err := r.loadCommonNames(ctx, species); err != nil {
		return domain.Species{}, err
	}

	return species[0], nil
}

// loadCommonNames preenche CommonNames de cada espécie com uma única
// consulta, com o nome principal primeiro.
func (r *postgresRepository) loadCommonNames(ctx context.Context, species []domain.Species) error {
	if len(species) == 0 {
		return nil
	}

	ids := make([]uuid.UUID, len(species))
	index := make(map[uuid.UUID]int, len(species))
	for i, s := range species {
		ids[i] = s.ID
		index[s.ID] = i
	}

	const query = `
		SELECT species_id, name, is_primary
		FROM species_common_names
		WHERE species_id = ANY($1)
		ORDER BY is_primary DESC, name
	`

	rows, err := r.pool.Query(ctx, query, ids)
	if err != nil {
		return fmt.Errorf("buscando nomes populares: %w", err)
	}
	defer rows.Close()

	for rows.Next() {
		var speciesID uuid.UUID
		var name domain.CommonName
		if err := rows.Scan(&speciesID, &name.Name, &name.IsPrimary); err != nil {
			return fmt.Errorf("lendo nome popular: %w", err)
		}
		i := index[speciesID]
		species[i].CommonNames = append(species[i].CommonNames, name)
	}

	return rows.Err()
}

// escapeLike escapa os caracteres especiais do LIKE, para que a busca do
// usuário seja tratada como texto literal.
func escapeLike(s string) string {
	return strings.NewReplacer(`\`, `\\`, `%`, `\%`, `_`, `\_`).Replace(s)
}

type rowScanner interface {
	Scan(dest ...any) error
}

func scanSpecies(row rowScanner) (domain.Species, error) {
	var s domain.Species
	err := row.Scan(&s.ID, &s.ScientificName, &s.Family, &s.Category, &s.GBIFKey, &s.CreatedAt, &s.UpdatedAt)
	if err != nil {
		return domain.Species{}, err
	}
	return s, nil
}
