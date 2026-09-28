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

	CreatedAt time.Time
	UpdatedAt time.Time
}
