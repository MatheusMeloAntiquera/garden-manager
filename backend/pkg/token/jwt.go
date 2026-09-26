// Package token cuida da geração e validação dos access tokens (JWT) e da
// geração e hashing dos refresh tokens (opacos).
package token

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

// ErrInvalidToken é retornado quando um access token é inválido ou expirou.
var ErrInvalidToken = errors.New("token inválido ou expirado")

// refreshTokenBytes define o tamanho, em bytes, do refresh token opaco antes
// da codificação em base64url.
const refreshTokenBytes = 32

// Claims são as claims customizadas do access token.
type Claims struct {
	jwt.RegisteredClaims
}

// Manager gera e valida access tokens JWT.
type Manager interface {
	// GenerateAccess cria um access token JWT para o usuário informado.
	GenerateAccess(userID uuid.UUID) (token string, expiresIn time.Duration, err error)
	// ParseAccess valida um access token e retorna o ID do usuário associado.
	ParseAccess(token string) (uuid.UUID, error)
}

type jwtManager struct {
	secret []byte
	ttl    time.Duration
}

// NewManager cria um Manager de access tokens assinados com HS256.
func NewManager(secret string, ttl time.Duration) Manager {
	return &jwtManager{secret: []byte(secret), ttl: ttl}
}

func (m *jwtManager) GenerateAccess(userID uuid.UUID) (string, time.Duration, error) {
	now := time.Now()
	claims := Claims{
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   userID.String(),
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(m.ttl)),
			ID:        uuid.NewString(),
		},
	}

	signed, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(m.secret)
	if err != nil {
		return "", 0, fmt.Errorf("assinando access token: %w", err)
	}

	return signed, m.ttl, nil
}

func (m *jwtManager) ParseAccess(tokenString string) (uuid.UUID, error) {
	claims := &Claims{}

	token, err := jwt.ParseWithClaims(tokenString, claims, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("método de assinatura inesperado: %v", t.Header["alg"])
		}
		return m.secret, nil
	})
	if err != nil || !token.Valid {
		return uuid.Nil, ErrInvalidToken
	}

	userID, err := uuid.Parse(claims.Subject)
	if err != nil {
		return uuid.Nil, ErrInvalidToken
	}

	return userID, nil
}

// NewRefreshToken gera um refresh token opaco (não é um JWT), aleatório e
// codificado em base64url, adequado para ser enviado ao cliente.
func NewRefreshToken() (string, error) {
	buf := make([]byte, refreshTokenBytes)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("gerando refresh token: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(buf), nil
}

// HashRefreshToken calcula o SHA-256 de um refresh token, para ser
// armazenado no banco no lugar do valor puro.
func HashRefreshToken(rawToken string) string {
	sum := sha256.Sum256([]byte(rawToken))
	return hex.EncodeToString(sum[:])
}
