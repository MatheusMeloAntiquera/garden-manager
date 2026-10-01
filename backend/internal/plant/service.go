package plant

import (
	"context"
	"errors"
	"strings"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/internal/environment"
	"github.com/matheusantiquera/garden-manager/backend/internal/species"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// Service implementa as regras de negócio de plantas. Todas as operações
// atuam apenas sobre as plantas do usuário informado.
type Service interface {
	Create(ctx context.Context, userID uuid.UUID, input CreateInput) (PlantResponse, error)
	Get(ctx context.Context, userID, id uuid.UUID) (PlantResponse, error)
	List(ctx context.Context, userID uuid.UUID, input ListInput) (ListResponse, error)
	Update(ctx context.Context, userID, id uuid.UUID, input UpdateInput) (PlantResponse, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
}

// EnvironmentFinder é a parte do repositório de ambientes usada para conferir
// que o ambiente informado pertence ao usuário. Satisfeita por
// environment.Repository.
type EnvironmentFinder interface {
	FindByID(ctx context.Context, userID, id uuid.UUID) (domain.Environment, error)
}

// SpeciesFinder é a parte do repositório do catálogo usada para conferir que
// a espécie informada existe. Satisfeita por species.Repository.
type SpeciesFinder interface {
	FindByID(ctx context.Context, id uuid.UUID) (domain.Species, error)
}

type service struct {
	repo         Repository
	environments EnvironmentFinder
	species      SpeciesFinder
	validator    *validator.Validator
}

// NewService cria o Service de plantas.
func NewService(repo Repository, environments EnvironmentFinder, species SpeciesFinder, v *validator.Validator) Service {
	return &service{repo: repo, environments: environments, species: species, validator: v}
}

func (s *service) Create(ctx context.Context, userID uuid.UUID, input CreateInput) (PlantResponse, error) {
	// Normaliza antes de validar, para que um apelido só com espaços conte
	// como ausente na regra "apelido ou espécie".
	input.Nickname = normalizeText(input.Nickname)
	input.Notes = normalizeText(input.Notes)

	if err := s.validator.Struct(input); err != nil {
		return PlantResponse{}, err
	}

	if err := s.checkReferences(ctx, userID, input.SpeciesID, input.EnvironmentID); err != nil {
		return PlantResponse{}, err
	}

	active := true
	if input.Active != nil {
		active = *input.Active
	}

	plant, err := s.repo.Create(ctx, domain.Plant{
		UserID:        userID,
		SpeciesID:     input.SpeciesID,
		EnvironmentID: input.EnvironmentID,
		Nickname:      input.Nickname,
		Notes:         input.Notes,
		Active:        active,
	})
	if err != nil {
		return PlantResponse{}, err
	}

	return NewPlantResponse(plant), nil
}

func (s *service) Get(ctx context.Context, userID, id uuid.UUID) (PlantResponse, error) {
	plant, err := s.repo.FindByID(ctx, userID, id)
	if err != nil {
		return PlantResponse{}, err
	}
	return NewPlantResponse(plant), nil
}

func (s *service) List(ctx context.Context, userID uuid.UUID, input ListInput) (ListResponse, error) {
	params := input.Normalize()

	plants, total, err := s.repo.List(ctx, userID, input.EnvironmentID, input.SpeciesID, input.Active, params.PageSize, params.Offset())
	if err != nil {
		return ListResponse{}, err
	}

	data := make([]PlantResponse, 0, len(plants))
	for _, plant := range plants {
		data = append(data, NewPlantResponse(plant))
	}

	return ListResponse{
		Data:     data,
		Page:     params.Page,
		PageSize: params.PageSize,
		Total:    total,
	}, nil
}

func (s *service) Update(ctx context.Context, userID, id uuid.UUID, input UpdateInput) (PlantResponse, error) {
	input.Nickname = normalizeText(input.Nickname)
	input.Notes = normalizeText(input.Notes)

	if err := s.validator.Struct(input); err != nil {
		return PlantResponse{}, err
	}

	if err := s.checkReferences(ctx, userID, input.SpeciesID, input.EnvironmentID); err != nil {
		return PlantResponse{}, err
	}

	plant, err := s.repo.Update(ctx, domain.Plant{
		ID:            id,
		UserID:        userID,
		SpeciesID:     input.SpeciesID,
		EnvironmentID: input.EnvironmentID,
		Nickname:      input.Nickname,
		Notes:         input.Notes,
		Active:        *input.Active,
	})
	if err != nil {
		return PlantResponse{}, err
	}

	return NewPlantResponse(plant), nil
}

func (s *service) Delete(ctx context.Context, userID, id uuid.UUID) error {
	return s.repo.Delete(ctx, userID, id)
}

// checkReferences confere que a espécie existe no catálogo e que o ambiente
// pertence ao usuário. IDs nulos são ignorados.
func (s *service) checkReferences(ctx context.Context, userID uuid.UUID, speciesID, environmentID *uuid.UUID) error {
	if speciesID != nil {
		if _, err := s.species.FindByID(ctx, *speciesID); err != nil {
			if errors.Is(err, species.ErrSpeciesNotFound) {
				return ErrInvalidSpecies
			}
			return err
		}
	}

	if environmentID != nil {
		if _, err := s.environments.FindByID(ctx, userID, *environmentID); err != nil {
			if errors.Is(err, environment.ErrEnvironmentNotFound) {
				return ErrInvalidEnvironment
			}
			return err
		}
	}

	return nil
}

// normalizeText remove espaços das pontas e converte texto vazio em nil, para
// que seja gravado como NULL.
func normalizeText(text *string) *string {
	if text == nil {
		return nil
	}
	trimmed := strings.TrimSpace(*text)
	if trimmed == "" {
		return nil
	}
	return &trimmed
}
