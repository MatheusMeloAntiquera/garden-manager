package plant

import (
	"time"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
)

// CreateInput é o corpo esperado por POST /plants. Toda planta precisa de
// apelido ou de espécie; species_id e environment_id são opcionais.
type CreateInput struct {
	SpeciesID     *uuid.UUID `json:"species_id"`
	EnvironmentID *uuid.UUID `json:"environment_id"`
	Nickname      *string    `json:"nickname" validate:"required_without=SpeciesID,omitempty,max=100"`
	Notes         *string    `json:"notes" validate:"omitempty,max=2000"`
	Active        *bool      `json:"active"` // opcional; quando omitido, a planta é criada ativa
}

// UpdateInput é o corpo esperado por PUT /plants/{id}. Como é um PUT, todos
// os campos substituem os valores atuais; campos opcionais omitidos ficam
// vazios. Active é ponteiro para que "required" aceite false.
type UpdateInput struct {
	SpeciesID     *uuid.UUID `json:"species_id"`
	EnvironmentID *uuid.UUID `json:"environment_id"`
	Nickname      *string    `json:"nickname" validate:"required_without=SpeciesID,omitempty,max=100"`
	Notes         *string    `json:"notes" validate:"omitempty,max=2000"`
	Active        *bool      `json:"active" validate:"required"`
}

// ListInput reúne os parâmetros de GET /plants.
type ListInput struct {
	pagination.Params
	EnvironmentID *uuid.UUID
	SpeciesID     *uuid.UUID
	Active        *bool  // nil lista ativas e inativas
	Query         string // busca por apelido, nome científico ou nome popular; vazio não filtra
}

// SpeciesRef é o resumo da espécie dentro de PlantResponse.
type SpeciesRef struct {
	ID             uuid.UUID `json:"id"`
	ScientificName string    `json:"scientific_name"`
	CommonName     *string   `json:"common_name"`
}

// EnvironmentRef é o resumo do ambiente dentro de PlantResponse.
type EnvironmentRef struct {
	ID   uuid.UUID `json:"id"`
	Name string    `json:"name"`
}

// PlantResponse é a representação pública de uma planta.
type PlantResponse struct {
	ID          uuid.UUID       `json:"id"`
	DisplayName string          `json:"display_name"` // apelido ou, sem ele, o nome da espécie
	Nickname    *string         `json:"nickname"`
	Notes       *string         `json:"notes"`
	Active      bool            `json:"active"`
	Species     *SpeciesRef     `json:"species"`
	Environment *EnvironmentRef `json:"environment"`
	CreatedAt   time.Time       `json:"created_at"`
	UpdatedAt   time.Time       `json:"updated_at"`
}

// NewPlantResponse converte um domain.Plant em PlantResponse.
func NewPlantResponse(p domain.Plant) PlantResponse {
	resp := PlantResponse{
		ID:          p.ID,
		DisplayName: DisplayName(p),
		Nickname:    p.Nickname,
		Notes:       p.Notes,
		Active:      p.Active,
		CreatedAt:   p.CreatedAt,
		UpdatedAt:   p.UpdatedAt,
	}

	if p.Species != nil {
		resp.Species = &SpeciesRef{
			ID:             p.Species.ID,
			ScientificName: p.Species.ScientificName,
			CommonName:     p.Species.CommonName,
		}
	}
	if p.Environment != nil {
		resp.Environment = &EnvironmentRef{ID: p.Environment.ID, Name: p.Environment.Name}
	}

	return resp
}

// DisplayName retorna o nome a exibir para a planta: o apelido, ou, sem ele,
// o nome popular principal da espécie, ou por último o nome científico.
func DisplayName(p domain.Plant) string {
	switch {
	case p.Nickname != nil:
		return *p.Nickname
	case p.Species != nil && p.Species.CommonName != nil:
		return *p.Species.CommonName
	case p.Species != nil:
		return p.Species.ScientificName
	default:
		return ""
	}
}

// ListResponse é o corpo retornado por GET /plants.
type ListResponse struct {
	Data     []PlantResponse `json:"data"`
	Page     int             `json:"page"`
	PageSize int             `json:"page_size"`
	Total    int             `json:"total"`
}
