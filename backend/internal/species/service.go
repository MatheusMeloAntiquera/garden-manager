package species

import (
	"context"
	"slices"
	"strings"

	"github.com/google/uuid"
)

// Service implementa a consulta ao catálogo de espécies.
type Service interface {
	List(ctx context.Context, input ListInput) (ListResponse, error)
	Get(ctx context.Context, id uuid.UUID) (SpeciesResponse, error)
}

type service struct {
	repo Repository
}

// NewService cria o Service do catálogo de espécies.
func NewService(repo Repository) Service {
	return &service{repo: repo}
}

func (s *service) List(ctx context.Context, input ListInput) (ListResponse, error) {
	query := strings.TrimSpace(input.Query)

	category := strings.TrimSpace(input.Category)
	if category != "" && !slices.Contains(categories, category) {
		return ListResponse{}, ErrInvalidCategory
	}

	params := input.Normalize()

	species, total, err := s.repo.List(ctx, query, category, params.PageSize, params.Offset())
	if err != nil {
		return ListResponse{}, err
	}

	data := make([]SpeciesResponse, 0, len(species))
	for _, sp := range species {
		data = append(data, NewSpeciesResponse(sp))
	}

	return ListResponse{
		Data:     data,
		Page:     params.Page,
		PageSize: params.PageSize,
		Total:    total,
	}, nil
}

func (s *service) Get(ctx context.Context, id uuid.UUID) (SpeciesResponse, error) {
	species, err := s.repo.FindByID(ctx, id)
	if err != nil {
		return SpeciesResponse{}, err
	}
	return NewSpeciesResponse(species), nil
}
