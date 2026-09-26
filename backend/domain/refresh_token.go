package domain

import (
	"time"

	"github.com/google/uuid"
)

// RefreshToken representa um refresh token emitido para um usuário.
// Apenas o hash SHA-256 do token é armazenado; o valor puro é devolvido
// ao cliente uma única vez, no momento da emissão.
type RefreshToken struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	TokenHash string
	ExpiresAt time.Time
	RevokedAt *time.Time
	CreatedAt time.Time
}

// IsValid retorna true se o token ainda não expirou nem foi revogado.
func (r RefreshToken) IsValid(now time.Time) bool {
	return r.RevokedAt == nil && now.Before(r.ExpiresAt)
}
