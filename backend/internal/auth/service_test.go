package auth

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/password"
	"github.com/matheusantiquera/garden-manager/backend/pkg/token"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

// fakeRepository é uma implementação em memória de Repository, usada para
// testar o service sem depender de um Postgres real.
type fakeRepository struct {
	mu            sync.Mutex
	usersByID     map[uuid.UUID]domain.User
	refreshTokens map[uuid.UUID]domain.RefreshToken
}

func newFakeRepository() *fakeRepository {
	return &fakeRepository{
		usersByID:     make(map[uuid.UUID]domain.User),
		refreshTokens: make(map[uuid.UUID]domain.RefreshToken),
	}
}

func (f *fakeRepository) CreateUser(_ context.Context, user domain.User) (domain.User, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	for _, existing := range f.usersByID {
		if existing.Email == user.Email {
			return domain.User{}, ErrEmailTaken
		}
	}

	user.ID = uuid.New()
	user.Active = true
	now := time.Now()
	user.CreatedAt = now
	user.UpdatedAt = now
	f.usersByID[user.ID] = user

	return user, nil
}

func (f *fakeRepository) FindUserByEmail(_ context.Context, email string) (domain.User, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	for _, u := range f.usersByID {
		if u.Email == email {
			return u, nil
		}
	}
	return domain.User{}, ErrUserNotFound
}

func (f *fakeRepository) FindUserByID(_ context.Context, id uuid.UUID) (domain.User, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	u, ok := f.usersByID[id]
	if !ok {
		return domain.User{}, ErrUserNotFound
	}
	return u, nil
}

func (f *fakeRepository) IncrementLoginAttempts(_ context.Context, userID uuid.UUID, maxAttempts int) (bool, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	u, ok := f.usersByID[userID]
	if !ok {
		return false, ErrUserNotFound
	}

	u.LoginAttempts++
	if u.LoginAttempts >= maxAttempts {
		u.Blocked = true
	}
	f.usersByID[userID] = u

	return u.Blocked, nil
}

func (f *fakeRepository) ResetLoginAttempts(_ context.Context, userID uuid.UUID) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	u, ok := f.usersByID[userID]
	if !ok {
		return ErrUserNotFound
	}
	u.LoginAttempts = 0
	f.usersByID[userID] = u

	return nil
}

func (f *fakeRepository) CreateRefreshToken(_ context.Context, rt domain.RefreshToken) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	f.refreshTokens[rt.ID] = rt
	return nil
}

func (f *fakeRepository) FindRefreshTokenWithUser(_ context.Context, tokenHash string) (domain.RefreshToken, domain.User, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	for _, rt := range f.refreshTokens {
		if rt.TokenHash == tokenHash {
			user, ok := f.usersByID[rt.UserID]
			if !ok {
				return domain.RefreshToken{}, domain.User{}, ErrUserNotFound
			}
			return rt, user, nil
		}
	}

	return domain.RefreshToken{}, domain.User{}, ErrInvalidRefreshToken
}

func (f *fakeRepository) RevokeRefreshToken(_ context.Context, id uuid.UUID) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	rt, ok := f.refreshTokens[id]
	if !ok {
		return nil
	}
	now := time.Now()
	rt.RevokedAt = &now
	f.refreshTokens[id] = rt

	return nil
}

func (f *fakeRepository) RotateRefreshToken(_ context.Context, oldTokenID uuid.UUID, newToken domain.RefreshToken) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	if old, ok := f.refreshTokens[oldTokenID]; ok {
		now := time.Now()
		old.RevokedAt = &now
		f.refreshTokens[oldTokenID] = old
	}
	f.refreshTokens[newToken.ID] = newToken

	return nil
}

// newTestService monta um Service com dependências reais (hasher, JWT,
// validator), exceto pelo repositório, que é o fake em memória.
func newTestService(t *testing.T) (Service, *fakeRepository) {
	t.Helper()

	repo := newFakeRepository()
	hasher := password.NewHasher()
	tokens := token.NewManager("test-secret", 15*time.Minute)

	v, err := validator.New()
	if err != nil {
		t.Fatalf("validator.New retornou erro: %v", err)
	}

	svc, err := NewService(repo, hasher, tokens, v, 720*time.Hour)
	if err != nil {
		t.Fatalf("NewService retornou erro: %v", err)
	}

	return svc, repo
}

func signupTestUser(t *testing.T, svc Service, email, password string) UserResponse {
	t.Helper()

	user, err := svc.Signup(context.Background(), SignupInput{
		Name:     "Usuário Teste",
		Email:    email,
		Password: password,
	})
	if err != nil {
		t.Fatalf("Signup retornou erro: %v", err)
	}
	return user
}

func TestSignupAndLoginSuccess(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()

	signupTestUser(t, svc, "teste@example.com", "Sup3r$ecret")

	tokens, err := svc.Login(ctx, LoginInput{Email: "teste@example.com", Password: "Sup3r$ecret"})
	if err != nil {
		t.Fatalf("Login retornou erro: %v", err)
	}
	if tokens.AccessToken == "" || tokens.RefreshToken == "" {
		t.Fatal("esperava access e refresh tokens não vazios")
	}
}

func TestSignupDuplicateEmail(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()

	signupTestUser(t, svc, "duplicado@example.com", "Sup3r$ecret")

	_, err := svc.Signup(ctx, SignupInput{Name: "Outro", Email: "duplicado@example.com", Password: "Sup3r$ecret"})
	if !errors.Is(err, ErrEmailTaken) {
		t.Fatalf("esperava ErrEmailTaken, obteve: %v", err)
	}
}

func TestLoginWrongPasswordBlocksAccountAfterThreeAttempts(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()

	signupTestUser(t, svc, "bloqueio@example.com", "Sup3r$ecret")

	for i := 0; i < MaxLoginAttempts-1; i++ {
		_, err := svc.Login(ctx, LoginInput{Email: "bloqueio@example.com", Password: "senha-errada"})
		if !errors.Is(err, ErrInvalidCredentials) {
			t.Fatalf("tentativa %d: esperava ErrInvalidCredentials, obteve: %v", i+1, err)
		}
	}

	// Terceira tentativa errada: a conta deve ser bloqueada.
	_, err := svc.Login(ctx, LoginInput{Email: "bloqueio@example.com", Password: "senha-errada"})
	if !errors.Is(err, ErrAccountBlocked) {
		t.Fatalf("esperava ErrAccountBlocked na 3ª tentativa, obteve: %v", err)
	}

	// Mesmo com a senha certa, a conta continua bloqueada.
	_, err = svc.Login(ctx, LoginInput{Email: "bloqueio@example.com", Password: "Sup3r$ecret"})
	if !errors.Is(err, ErrAccountBlocked) {
		t.Fatalf("esperava ErrAccountBlocked com a conta já bloqueada, obteve: %v", err)
	}
}

func TestLoginSuccessResetsAttempts(t *testing.T) {
	svc, repo := newTestService(t)
	ctx := context.Background()

	user := signupTestUser(t, svc, "reset@example.com", "Sup3r$ecret")

	_, err := svc.Login(ctx, LoginInput{Email: "reset@example.com", Password: "senha-errada"})
	if !errors.Is(err, ErrInvalidCredentials) {
		t.Fatalf("esperava ErrInvalidCredentials, obteve: %v", err)
	}

	if _, err := svc.Login(ctx, LoginInput{Email: "reset@example.com", Password: "Sup3r$ecret"}); err != nil {
		t.Fatalf("Login retornou erro: %v", err)
	}

	stored, err := repo.FindUserByID(ctx, user.ID)
	if err != nil {
		t.Fatalf("FindUserByID retornou erro: %v", err)
	}
	if stored.LoginAttempts != 0 {
		t.Fatalf("esperava login_attempts=0 após login bem-sucedido, obteve %d", stored.LoginAttempts)
	}
}

func TestLoginUnknownEmail(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()

	_, err := svc.Login(ctx, LoginInput{Email: "nao-existe@example.com", Password: "Sup3r$ecret"})
	if !errors.Is(err, ErrInvalidCredentials) {
		t.Fatalf("esperava ErrInvalidCredentials, obteve: %v", err)
	}
}

func TestRefreshRotatesTokenAndRejectsOldOne(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()

	signupTestUser(t, svc, "refresh@example.com", "Sup3r$ecret")

	loginTokens, err := svc.Login(ctx, LoginInput{Email: "refresh@example.com", Password: "Sup3r$ecret"})
	if err != nil {
		t.Fatalf("Login retornou erro: %v", err)
	}

	refreshed, err := svc.Refresh(ctx, RefreshInput{RefreshToken: loginTokens.RefreshToken})
	if err != nil {
		t.Fatalf("Refresh retornou erro: %v", err)
	}
	if refreshed.RefreshToken == loginTokens.RefreshToken {
		t.Fatal("esperava um novo refresh token, diferente do original")
	}

	// O refresh token antigo não pode mais ser usado.
	_, err = svc.Refresh(ctx, RefreshInput{RefreshToken: loginTokens.RefreshToken})
	if !errors.Is(err, ErrInvalidRefreshToken) {
		t.Fatalf("esperava ErrInvalidRefreshToken para o token antigo, obteve: %v", err)
	}

	// O novo refresh token deve funcionar normalmente.
	if _, err := svc.Refresh(ctx, RefreshInput{RefreshToken: refreshed.RefreshToken}); err != nil {
		t.Fatalf("Refresh com o novo token retornou erro: %v", err)
	}
}

func TestLogoutRevokesRefreshToken(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()

	signupTestUser(t, svc, "logout@example.com", "Sup3r$ecret")

	loginTokens, err := svc.Login(ctx, LoginInput{Email: "logout@example.com", Password: "Sup3r$ecret"})
	if err != nil {
		t.Fatalf("Login retornou erro: %v", err)
	}

	if err := svc.Logout(ctx, LogoutInput{RefreshToken: loginTokens.RefreshToken}); err != nil {
		t.Fatalf("Logout retornou erro: %v", err)
	}

	_, err = svc.Refresh(ctx, RefreshInput{RefreshToken: loginTokens.RefreshToken})
	if !errors.Is(err, ErrInvalidRefreshToken) {
		t.Fatalf("esperava ErrInvalidRefreshToken após logout, obteve: %v", err)
	}
}
