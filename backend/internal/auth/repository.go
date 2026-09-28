package auth

import (
	"context"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/matheusantiquera/garden-manager/backend/domain"
)

// uniqueViolation é o código de erro do Postgres para violação de
// constraint UNIQUE.
const uniqueViolation = "23505"

// Repository dá acesso aos dados de usuários e refresh tokens.
//
// TODO(#1): nenhuma linha de refresh_tokens é apagada — CreateRefreshToken
// e RotateRefreshToken só acrescentam linhas, e RevokeRefreshToken apenas
// marca revoked_at. Isso faz a tabela crescer sem limite.
// https://github.com/MatheusMeloAntiquera/garden-manager/issues/1
type Repository interface {
	CreateUser(ctx context.Context, user domain.User) (domain.User, error)
	FindUserByEmail(ctx context.Context, email string) (domain.User, error)
	FindUserByID(ctx context.Context, id uuid.UUID) (domain.User, error)

	// IncrementLoginAttempts incrementa o contador de tentativas de login e
	// bloqueia a conta atomicamente quando o novo total atinge maxAttempts.
	// Retorna se a conta ficou (ou já estava) bloqueada.
	IncrementLoginAttempts(ctx context.Context, userID uuid.UUID, maxAttempts int) (blocked bool, err error)
	ResetLoginAttempts(ctx context.Context, userID uuid.UUID) error

	CreateRefreshToken(ctx context.Context, rt domain.RefreshToken) error
	// FindRefreshTokenWithUser busca um refresh token pelo hash, junto com o
	// usuário associado a ele.
	FindRefreshTokenWithUser(ctx context.Context, tokenHash string) (domain.RefreshToken, domain.User, error)
	RevokeRefreshToken(ctx context.Context, id uuid.UUID) error
	// RotateRefreshToken revoga oldTokenID e cria newToken em uma única
	// transação.
	RotateRefreshToken(ctx context.Context, oldTokenID uuid.UUID, newToken domain.RefreshToken) error
}

type postgresRepository struct {
	pool *pgxpool.Pool
}

// NewRepository cria um Repository baseado em Postgres via pgx.
func NewRepository(pool *pgxpool.Pool) Repository {
	return &postgresRepository{pool: pool}
}

func (r *postgresRepository) CreateUser(ctx context.Context, user domain.User) (domain.User, error) {
	const query = `
		INSERT INTO users (name, email, password, birth_date)
		VALUES ($1, $2, $3, $4)
		RETURNING id, name, email, password, login_attempts, blocked, active,
			birth_date, email_verified_at, created_at, updated_at
	`

	row := r.pool.QueryRow(ctx, query, user.Name, user.Email, user.Password, user.BirthDate)

	created, err := scanUser(row)
	if err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == uniqueViolation {
			return domain.User{}, ErrEmailTaken
		}
		return domain.User{}, fmt.Errorf("inserindo usuário: %w", err)
	}

	return created, nil
}

func (r *postgresRepository) FindUserByEmail(ctx context.Context, email string) (domain.User, error) {
	const query = `
		SELECT id, name, email, password, login_attempts, blocked, active,
			birth_date, email_verified_at, created_at, updated_at
		FROM users
		WHERE email = $1
	`

	user, err := scanUser(r.pool.QueryRow(ctx, query, email))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.User{}, ErrUserNotFound
		}
		return domain.User{}, fmt.Errorf("buscando usuário por e-mail: %w", err)
	}

	return user, nil
}

func (r *postgresRepository) FindUserByID(ctx context.Context, id uuid.UUID) (domain.User, error) {
	const query = `
		SELECT id, name, email, password, login_attempts, blocked, active,
			birth_date, email_verified_at, created_at, updated_at
		FROM users
		WHERE id = $1
	`

	user, err := scanUser(r.pool.QueryRow(ctx, query, id))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.User{}, ErrUserNotFound
		}
		return domain.User{}, fmt.Errorf("buscando usuário por id: %w", err)
	}

	return user, nil
}

func (r *postgresRepository) IncrementLoginAttempts(ctx context.Context, userID uuid.UUID, maxAttempts int) (bool, error) {
	const query = `
		UPDATE users
		SET login_attempts = login_attempts + 1,
			blocked = blocked OR (login_attempts + 1 >= $2)
		WHERE id = $1
		RETURNING blocked
	`

	var blocked bool
	if err := r.pool.QueryRow(ctx, query, userID, maxAttempts).Scan(&blocked); err != nil {
		return false, fmt.Errorf("incrementando tentativas de login: %w", err)
	}

	return blocked, nil
}

func (r *postgresRepository) ResetLoginAttempts(ctx context.Context, userID uuid.UUID) error {
	const query = `UPDATE users SET login_attempts = 0 WHERE id = $1`

	if _, err := r.pool.Exec(ctx, query, userID); err != nil {
		return fmt.Errorf("resetando tentativas de login: %w", err)
	}

	return nil
}

func (r *postgresRepository) CreateRefreshToken(ctx context.Context, rt domain.RefreshToken) error {
	const query = `
		INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at)
		VALUES ($1, $2, $3, $4)
	`

	if _, err := r.pool.Exec(ctx, query, rt.ID, rt.UserID, rt.TokenHash, rt.ExpiresAt); err != nil {
		return fmt.Errorf("inserindo refresh token: %w", err)
	}

	return nil
}

func (r *postgresRepository) FindRefreshTokenWithUser(ctx context.Context, tokenHash string) (domain.RefreshToken, domain.User, error) {
	const query = `
		SELECT
			rt.id, rt.user_id, rt.token_hash, rt.expires_at, rt.revoked_at, rt.created_at,
			u.id, u.name, u.email, u.password, u.login_attempts, u.blocked, u.active,
			u.birth_date, u.email_verified_at, u.created_at, u.updated_at
		FROM refresh_tokens rt
		JOIN users u ON u.id = rt.user_id
		WHERE rt.token_hash = $1
	`

	var rt domain.RefreshToken
	var user domain.User

	err := r.pool.QueryRow(ctx, query, tokenHash).Scan(
		&rt.ID, &rt.UserID, &rt.TokenHash, &rt.ExpiresAt, &rt.RevokedAt, &rt.CreatedAt,
		&user.ID, &user.Name, &user.Email, &user.Password, &user.LoginAttempts, &user.Blocked, &user.Active,
		&user.BirthDate, &user.EmailVerifiedAt, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.RefreshToken{}, domain.User{}, ErrInvalidRefreshToken
		}
		return domain.RefreshToken{}, domain.User{}, fmt.Errorf("buscando refresh token: %w", err)
	}

	return rt, user, nil
}

func (r *postgresRepository) RevokeRefreshToken(ctx context.Context, id uuid.UUID) error {
	const query = `UPDATE refresh_tokens SET revoked_at = now() WHERE id = $1 AND revoked_at IS NULL`

	if _, err := r.pool.Exec(ctx, query, id); err != nil {
		return fmt.Errorf("revogando refresh token: %w", err)
	}

	return nil
}

func (r *postgresRepository) RotateRefreshToken(ctx context.Context, oldTokenID uuid.UUID, newToken domain.RefreshToken) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("iniciando transação: %w", err)
	}
	defer tx.Rollback(ctx) //nolint:errcheck // rollback é um no-op após o commit

	const revokeQuery = `UPDATE refresh_tokens SET revoked_at = now() WHERE id = $1 AND revoked_at IS NULL`
	if _, err := tx.Exec(ctx, revokeQuery, oldTokenID); err != nil {
		return fmt.Errorf("revogando refresh token antigo: %w", err)
	}

	const insertQuery = `
		INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at)
		VALUES ($1, $2, $3, $4)
	`
	if _, err := tx.Exec(ctx, insertQuery, newToken.ID, newToken.UserID, newToken.TokenHash, newToken.ExpiresAt); err != nil {
		return fmt.Errorf("inserindo novo refresh token: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("efetivando transação: %w", err)
	}

	return nil
}

// rowScanner abstrai pgx.Row para permitir testar scanUser isoladamente.
type rowScanner interface {
	Scan(dest ...any) error
}

func scanUser(row rowScanner) (domain.User, error) {
	var u domain.User
	err := row.Scan(
		&u.ID, &u.Name, &u.Email, &u.Password, &u.LoginAttempts, &u.Blocked, &u.Active,
		&u.BirthDate, &u.EmailVerifiedAt, &u.CreatedAt, &u.UpdatedAt,
	)
	if err != nil {
		return domain.User{}, err
	}
	return u, nil
}
