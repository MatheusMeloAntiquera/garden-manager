package auth

import (
	"time"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// SignupInput é o corpo esperado por POST /auth/signup.
type SignupInput struct {
	Name      string `json:"name" validate:"required,min=2,max=100"`
	Email     string `json:"email" validate:"required,email,max=255"`
	Password  string `json:"password" validate:"required,strongpassword"`
	BirthDate string `json:"birth_date" validate:"required,birthdate"` // AAAA-MM-DD
}

// LoginInput é o corpo esperado por POST /auth/login.
type LoginInput struct {
	Email    string `json:"email" validate:"required,email"`
	Password string `json:"password" validate:"required"`
}

// RefreshInput é o corpo esperado por POST /auth/refresh.
type RefreshInput struct {
	RefreshToken string `json:"refresh_token" validate:"required"`
}

// LogoutInput é o corpo esperado por POST /auth/logout.
type LogoutInput struct {
	RefreshToken string `json:"refresh_token" validate:"required"`
}

// UserResponse é a representação pública de um usuário, sem dados
// sensíveis como a senha.
type UserResponse struct {
	ID        uuid.UUID `json:"id"`
	Name      string    `json:"name"`
	Email     string    `json:"email"`
	BirthDate *string   `json:"birth_date"` // AAAA-MM-DD; null para contas sem data cadastrada
	Active    bool      `json:"active"`
	CreatedAt time.Time `json:"created_at"`
}

// NewUserResponse converte um domain.User em UserResponse.
func NewUserResponse(u domain.User) UserResponse {
	var birthDate *string
	if u.BirthDate != nil {
		formatted := u.BirthDate.Format(validator.DateLayout)
		birthDate = &formatted
	}

	return UserResponse{
		ID:        u.ID,
		Name:      u.Name,
		Email:     u.Email,
		BirthDate: birthDate,
		Active:    u.Active,
		CreatedAt: u.CreatedAt,
	}
}

// TokenResponse é o corpo retornado por login e refresh bem-sucedidos.
type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	TokenType    string `json:"token_type"`
	ExpiresIn    int64  `json:"expires_in"` // segundos até a expiração do access token
}
