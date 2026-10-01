package species

import (
	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
)

// Categorias aceitas no catálogo (mesmos valores do CHECK da tabela species).
var categories = []string{"folhagem", "suculenta", "flor", "arvore", "erva", "hortalica", "frutifera", "grama"}

// ListInput reúne os parâmetros de GET /species.
type ListInput struct {
	pagination.Params
	Query    string // busca por nome popular ou científico, sem diferenciar acentos nem maiúsculas
	Category string // vazio não filtra por categoria
}

// SpeciesResponse é a representação pública de uma espécie do catálogo.
type SpeciesResponse struct {
	ID             uuid.UUID `json:"id"`
	ScientificName string    `json:"scientific_name"`
	Family         string    `json:"family"`
	Category       string    `json:"category"`
	CommonName     *string   `json:"common_name"` // nome popular principal
	CommonNames    []string  `json:"common_names"`
}

// NewSpeciesResponse converte um domain.Species em SpeciesResponse.
func NewSpeciesResponse(s domain.Species) SpeciesResponse {
	names := make([]string, 0, len(s.CommonNames))
	for _, n := range s.CommonNames {
		names = append(names, n.Name)
	}

	return SpeciesResponse{
		ID:             s.ID,
		ScientificName: s.ScientificName,
		Family:         s.Family,
		Category:       s.Category,
		CommonName:     s.PrimaryName(),
		CommonNames:    names,
	}
}

// ListResponse é o corpo retornado por GET /species.
type ListResponse struct {
	Data     []SpeciesResponse `json:"data"`
	Page     int               `json:"page"`
	PageSize int               `json:"page_size"`
	Total    int               `json:"total"`
}
