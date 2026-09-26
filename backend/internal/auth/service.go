package auth

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/password"
	"github.com/matheusantiquera/garden-manager/backend/pkg/token"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// MaxLoginAttempts é o número de tentativas de login inválidas permitidas
// antes de a conta ser bloqueada.
const MaxLoginAttempts = 3

// Service implementa as regras de negócio de autenticação.
type Service interface {
	Signup(ctx context.Context, input SignupInput) (UserResponse, error)
	Login(ctx context.Context, input LoginInput) (TokenResponse, error)
	Refresh(ctx context.Context, input RefreshInput) (TokenResponse, error)
	Logout(ctx context.Context, input LogoutInput) error
	GetUser(ctx context.Context, userID uuid.UUID) (UserResponse, error)
}

type service struct {
	repo      Repository
	hasher    password.Hasher
	tokens    token.Manager
	validator *validator.Validator

	refreshTokenTTL time.Duration

	// dummyHash é um hash argon2id gerado uma única vez, na construção do
	// service, e usado apenas para que o Login gaste um tempo de CPU
	// equivalente ao de uma verificação real quando o e-mail não existe ou
	// a conta não tem senha (login via OAuth). O texto usado para gerá-lo
	// é irrelevante e nunca é comparado com a senha de ninguém — o que
	// importa é só o custo computacional do Verify contra ele, para não
	// vazar por diferença de tempo quais e-mails estão cadastrados.
	dummyHash string
}

// NewService cria o Service de autenticação.
func NewService(
	repo Repository,
	hasher password.Hasher,
	tokens token.Manager,
	v *validator.Validator,
	refreshTokenTTL time.Duration,
) (Service, error) {
	// O texto abaixo não é uma senha real de nenhuma conta; serve só de
	// entrada para gerar um hash argon2id válido (ver o campo dummyHash).
	dummyHash, err := hasher.Hash("dummy-password-para-equalizar-tempo-de-resposta")
	if err != nil {
		return nil, fmt.Errorf("gerando hash dummy: %w", err)
	}

	return &service{
		repo:            repo,
		hasher:          hasher,
		tokens:          tokens,
		validator:       v,
		refreshTokenTTL: refreshTokenTTL,
		dummyHash:       dummyHash,
	}, nil
}

func (s *service) Signup(ctx context.Context, input SignupInput) (UserResponse, error) {
	if err := s.validator.Struct(input); err != nil {
		return UserResponse{}, err
	}

	email := normalizeEmail(input.Email)

	hash, err := s.hasher.Hash(input.Password)
	if err != nil {
		return UserResponse{}, fmt.Errorf("gerando hash da senha: %w", err)
	}

	user, err := s.repo.CreateUser(ctx, domain.User{
		Name:     strings.TrimSpace(input.Name),
		Email:    email,
		Password: &hash,
	})
	if err != nil {
		return UserResponse{}, err
	}

	return NewUserResponse(user), nil
}

func (s *service) Login(ctx context.Context, input LoginInput) (TokenResponse, error) {
	if err := s.validator.Struct(input); err != nil {
		return TokenResponse{}, err
	}

	email := normalizeEmail(input.Email)

	user, err := s.repo.FindUserByEmail(ctx, email)
	if errors.Is(err, ErrUserNotFound) {
		// Equaliza o tempo de resposta mesmo quando o e-mail não existe.
		_, _ = s.hasher.Verify(input.Password, s.dummyHash)
		return TokenResponse{}, ErrInvalidCredentials
	}
	if err != nil {
		return TokenResponse{}, err
	}

	if user.Password == nil {
		// Conta sem senha (reservado para login via OAuth no futuro).
		_, _ = s.hasher.Verify(input.Password, s.dummyHash)
		return TokenResponse{}, ErrInvalidCredentials
	}

	if user.Blocked {
		return TokenResponse{}, ErrAccountBlocked
	}
	if !user.Active {
		return TokenResponse{}, ErrAccountInactive
	}

	validPassword, err := s.hasher.Verify(input.Password, *user.Password)
	if err != nil {
		return TokenResponse{}, fmt.Errorf("verificando senha: %w", err)
	}

	if !validPassword {
		blocked, attemptErr := s.repo.IncrementLoginAttempts(ctx, user.ID, MaxLoginAttempts)
		if attemptErr != nil {
			return TokenResponse{}, attemptErr
		}
		if blocked {
			return TokenResponse{}, ErrAccountBlocked
		}
		return TokenResponse{}, ErrInvalidCredentials
	}

	if user.LoginAttempts > 0 {
		if err := s.repo.ResetLoginAttempts(ctx, user.ID); err != nil {
			return TokenResponse{}, err
		}
	}

	return s.issueTokens(ctx, user.ID)
}

func (s *service) Refresh(ctx context.Context, input RefreshInput) (TokenResponse, error) {
	if err := s.validator.Struct(input); err != nil {
		return TokenResponse{}, err
	}

	hash := token.HashRefreshToken(input.RefreshToken)

	rt, user, err := s.repo.FindRefreshTokenWithUser(ctx, hash)
	if err != nil {
		return TokenResponse{}, err
	}

	if !rt.IsValid(time.Now()) {
		return TokenResponse{}, ErrInvalidRefreshToken
	}
	if user.Blocked {
		return TokenResponse{}, ErrAccountBlocked
	}
	if !user.Active {
		return TokenResponse{}, ErrAccountInactive
	}

	rawToken, err := token.NewRefreshToken()
	if err != nil {
		return TokenResponse{}, err
	}

	newRefreshToken := domain.RefreshToken{
		ID:        uuid.New(),
		UserID:    user.ID,
		TokenHash: token.HashRefreshToken(rawToken),
		ExpiresAt: time.Now().Add(s.refreshTokenTTL),
	}

	if err := s.repo.RotateRefreshToken(ctx, rt.ID, newRefreshToken); err != nil {
		return TokenResponse{}, err
	}

	accessToken, expiresIn, err := s.tokens.GenerateAccess(user.ID)
	if err != nil {
		return TokenResponse{}, err
	}

	return TokenResponse{
		AccessToken:  accessToken,
		RefreshToken: rawToken,
		TokenType:    "Bearer",
		ExpiresIn:    int64(expiresIn.Seconds()),
	}, nil
}

func (s *service) Logout(ctx context.Context, input LogoutInput) error {
	if err := s.validator.Struct(input); err != nil {
		return err
	}

	hash := token.HashRefreshToken(input.RefreshToken)

	rt, _, err := s.repo.FindRefreshTokenWithUser(ctx, hash)
	if err != nil {
		return err
	}

	return s.repo.RevokeRefreshToken(ctx, rt.ID)
}

func (s *service) GetUser(ctx context.Context, userID uuid.UUID) (UserResponse, error) {
	user, err := s.repo.FindUserByID(ctx, userID)
	if err != nil {
		return UserResponse{}, err
	}
	return NewUserResponse(user), nil
}

// issueTokens gera e persiste um novo par de access/refresh token para o
// usuário informado.
func (s *service) issueTokens(ctx context.Context, userID uuid.UUID) (TokenResponse, error) {
	rawRefreshToken, err := token.NewRefreshToken()
	if err != nil {
		return TokenResponse{}, err
	}

	refreshToken := domain.RefreshToken{
		ID:        uuid.New(),
		UserID:    userID,
		TokenHash: token.HashRefreshToken(rawRefreshToken),
		ExpiresAt: time.Now().Add(s.refreshTokenTTL),
	}

	if err := s.repo.CreateRefreshToken(ctx, refreshToken); err != nil {
		return TokenResponse{}, err
	}

	accessToken, expiresIn, err := s.tokens.GenerateAccess(userID)
	if err != nil {
		return TokenResponse{}, err
	}

	return TokenResponse{
		AccessToken:  accessToken,
		RefreshToken: rawRefreshToken,
		TokenType:    "Bearer",
		ExpiresIn:    int64(expiresIn.Seconds()),
	}, nil
}

func normalizeEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}
