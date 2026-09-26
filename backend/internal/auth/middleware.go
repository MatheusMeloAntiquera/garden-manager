package auth

import (
	"context"
	"net/http"
	"strings"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/pkg/httpx"
	"github.com/matheusantiquera/garden-manager/backend/pkg/token"
)

type contextKey string

const userIDContextKey contextKey = "userID"

// RequireAuth valida o access token JWT enviado no header Authorization
// (formato "Bearer <token>") e, se válido, coloca o ID do usuário no
// contexto da requisição.
func RequireAuth(tokens token.Manager) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			header := r.Header.Get("Authorization")
			tokenString, ok := strings.CutPrefix(header, "Bearer ")
			if !ok || tokenString == "" {
				httpx.WriteError(w, http.StatusUnauthorized, "token de acesso ausente", nil)
				return
			}

			userID, err := tokens.ParseAccess(tokenString)
			if err != nil {
				httpx.WriteError(w, http.StatusUnauthorized, "token de acesso inválido ou expirado", nil)
				return
			}

			ctx := context.WithValue(r.Context(), userIDContextKey, userID)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// UserIDFromContext extrai o ID do usuário autenticado do contexto,
// colocado ali pelo middleware RequireAuth.
func UserIDFromContext(ctx context.Context) (uuid.UUID, bool) {
	userID, ok := ctx.Value(userIDContextKey).(uuid.UUID)
	return userID, ok
}
