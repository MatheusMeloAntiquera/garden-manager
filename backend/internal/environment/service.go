package environment

import (
	"context"
	"strings"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// Service implementa as regras de negócio de ambientes. Todas as operações
// atuam apenas sobre os ambientes do usuário informado.
type Service interface {
	Create(ctx context.Context, userID uuid.UUID, input CreateInput) (EnvironmentResponse, error)
	Get(ctx context.Context, userID, id uuid.UUID) (EnvironmentResponse, error)
	List(ctx context.Context, userID uuid.UUID, input ListInput) (ListResponse, error)
	Update(ctx context.Context, userID, id uuid.UUID, input UpdateInput) (EnvironmentResponse, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
}

type service struct {
	repo      Repository
	validator *validator.Validator
}

// NewService cria o Service de ambientes.
func NewService(repo Repository, v *validator.Validator) Service {
	return &service{repo: repo, validator: v}
}

func (s *service) Create(ctx context.Context, userID uuid.UUID, input CreateInput) (EnvironmentResponse, error) {
	// Normaliza antes de validar, para que um nome só com espaços seja
	// rejeitado pelo "required".
	input.Name = strings.TrimSpace(input.Name)
	input.Notes = normalizeNotes(input.Notes)

	if err := s.validator.Struct(input); err != nil {
		return EnvironmentResponse{}, err
	}

	active := true
	if input.Active != nil {
		active = *input.Active
	}

	env, err := s.repo.Create(ctx, domain.Environment{
		UserID: userID,
		Name:   input.Name,
		Notes:  input.Notes,
		Active: active,
	})
	if err != nil {
		return EnvironmentResponse{}, err
	}

	return NewEnvironmentResponse(env), nil
}

func (s *service) Get(ctx context.Context, userID, id uuid.UUID) (EnvironmentResponse, error) {
	env, err := s.repo.FindByID(ctx, userID, id)
	if err != nil {
		return EnvironmentResponse{}, err
	}
	return NewEnvironmentResponse(env), nil
}

func (s *service) List(ctx context.Context, userID uuid.UUID, input ListInput) (ListResponse, error) {
	page := max(input.Page, 1)

	pageSize := input.PageSize
	if pageSize < 1 {
		pageSize = DefaultPageSize
	}
	pageSize = min(pageSize, MaxPageSize)

	envs, total, err := s.repo.List(ctx, userID, input.Active, pageSize, (page-1)*pageSize)
	if err != nil {
		return ListResponse{}, err
	}

	data := make([]EnvironmentResponse, 0, len(envs))
	for _, env := range envs {
		data = append(data, NewEnvironmentResponse(env))
	}

	return ListResponse{
		Data:     data,
		Page:     page,
		PageSize: pageSize,
		Total:    total,
	}, nil
}

func (s *service) Update(ctx context.Context, userID, id uuid.UUID, input UpdateInput) (EnvironmentResponse, error) {
	input.Name = strings.TrimSpace(input.Name)
	input.Notes = normalizeNotes(input.Notes)

	if err := s.validator.Struct(input); err != nil {
		return EnvironmentResponse{}, err
	}

	env, err := s.repo.Update(ctx, domain.Environment{
		ID:     id,
		UserID: userID,
		Name:   input.Name,
		Notes:  input.Notes,
		Active: *input.Active,
	})
	if err != nil {
		return EnvironmentResponse{}, err
	}

	return NewEnvironmentResponse(env), nil
}

func (s *service) Delete(ctx context.Context, userID, id uuid.UUID) error {
	return s.repo.Delete(ctx, userID, id)
}

// normalizeNotes remove espaços das pontas e converte observações vazias em
// nil, para que sejam gravadas como NULL.
func normalizeNotes(notes *string) *string {
	if notes == nil {
		return nil
	}
	trimmed := strings.TrimSpace(*notes)
	if trimmed == "" {
		return nil
	}
	return &trimmed
}
