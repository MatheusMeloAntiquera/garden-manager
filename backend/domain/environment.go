package domain

import (
	"time"

	"github.com/google/uuid"
)

// Environment representa um ambiente do usuário onde as plantas ficam,
// como uma sala, cozinha ou área externa. O nome é livre.
type Environment struct {
	ID     uuid.UUID
	UserID uuid.UUID
	Name   string
	Notes  *string // observações livres; nulo quando não informadas
	Active bool

	// PlantCount é a quantidade de plantas ativas do ambiente. É calculada na
	// leitura e não é gravada na tabela.
	PlantCount int

	CreatedAt time.Time
	UpdatedAt time.Time
}
