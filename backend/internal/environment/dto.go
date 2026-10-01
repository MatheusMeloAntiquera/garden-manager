package environment

import (
	"time"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
)

// CreateInput é o corpo esperado por POST /environments.
type CreateInput struct {
	Name   string  `json:"name" validate:"required,max=100"`
	Notes  *string `json:"notes" validate:"omitempty,max=2000"`
	Active *bool   `json:"active"` // opcional; quando omitido, o ambiente é criado ativo
}

// UpdateInput é o corpo esperado por PUT /environments/{id}. Como é um PUT,
// todos os campos substituem os valores atuais; notes omitido limpa as
// observações. Active é ponteiro para que "required" aceite false.
type UpdateInput struct {
	Name   string  `json:"name" validate:"required,max=100"`
	Notes  *string `json:"notes" validate:"omitempty,max=2000"`
	Active *bool   `json:"active" validate:"required"`
}

// ListInput reúne os parâmetros de GET /environments.
type ListInput struct {
	pagination.Params
	Active *bool // nil lista ativos e inativos
}

// EnvironmentResponse é a representação pública de um ambiente.
type EnvironmentResponse struct {
	ID        uuid.UUID `json:"id"`
	Name      string    `json:"name"`
	Notes     *string   `json:"notes"`
	Active    bool      `json:"active"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

// NewEnvironmentResponse converte um domain.Environment em EnvironmentResponse.
func NewEnvironmentResponse(e domain.Environment) EnvironmentResponse {
	return EnvironmentResponse{
		ID:        e.ID,
		Name:      e.Name,
		Notes:     e.Notes,
		Active:    e.Active,
		CreatedAt: e.CreatedAt,
		UpdatedAt: e.UpdatedAt,
	}
}

// ListResponse é o corpo retornado por GET /environments.
type ListResponse struct {
	Data     []EnvironmentResponse `json:"data"`
	Page     int                   `json:"page"`
	PageSize int                   `json:"page_size"`
	Total    int                   `json:"total"`
}
