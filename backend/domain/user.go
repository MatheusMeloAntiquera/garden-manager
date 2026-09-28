package domain

import (
	"time"

	"github.com/google/uuid"
)

// User representa uma conta na aplicação.
type User struct {
	ID       uuid.UUID
	Name     string
	Email    string
	Password *string // hash argon2id no formato PHC; nulo para contas criadas apenas via OAuth (futuro)

	BirthDate *time.Time // apenas a data (sem horário); nula para usuários antigos e contas OAuth (futuro)

	LoginAttempts int
	Blocked       bool
	Active        bool

	EmailVerifiedAt *time.Time // preparação para verificação de e-mail e login via OAuth (futuro)

	CreatedAt time.Time
	UpdatedAt time.Time
}
