package auth

import (
	"errors"
	"net/http"

	"github.com/go-playground/validator/v10"

	"github.com/matheusantiquera/garden-manager/backend/pkg/httpx"
	"github.com/matheusantiquera/garden-manager/backend/pkg/token"
)

// Handler expõe as rotas HTTP de autenticação.
type Handler struct {
	service Service
}

// NewHandler cria um Handler de autenticação.
func NewHandler(service Service) *Handler {
	return &Handler{service: service}
}

// RegisterRoutes registra as rotas de autenticação no mux informado.
// tokens é usado apenas para proteger a rota /users/me com RequireAuth.
func (h *Handler) RegisterRoutes(mux *http.ServeMux, tokens token.Manager) {
	mux.HandleFunc("POST /api/v1/auth/signup", h.signup)
	mux.HandleFunc("POST /api/v1/auth/login", h.login)
	mux.HandleFunc("POST /api/v1/auth/refresh", h.refresh)
	mux.HandleFunc("POST /api/v1/auth/logout", h.logout)

	mux.Handle("GET /api/v1/users/me", RequireAuth(tokens)(http.HandlerFunc(h.me)))
}

func (h *Handler) signup(w http.ResponseWriter, r *http.Request) {
	var input SignupInput
	if err := httpx.DecodeJSON(r, &input); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "corpo da requisição inválido", nil)
		return
	}

	user, err := h.service.Signup(r.Context(), input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusCreated, user)
}

func (h *Handler) login(w http.ResponseWriter, r *http.Request) {
	var input LoginInput
	if err := httpx.DecodeJSON(r, &input); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "corpo da requisição inválido", nil)
		return
	}

	tokens, err := h.service.Login(r.Context(), input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, tokens)
}

func (h *Handler) refresh(w http.ResponseWriter, r *http.Request) {
	var input RefreshInput
	if err := httpx.DecodeJSON(r, &input); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "corpo da requisição inválido", nil)
		return
	}

	tokens, err := h.service.Refresh(r.Context(), input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, tokens)
}

func (h *Handler) logout(w http.ResponseWriter, r *http.Request) {
	var input LogoutInput
	if err := httpx.DecodeJSON(r, &input); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "corpo da requisição inválido", nil)
		return
	}

	if err := h.service.Logout(r.Context(), input); err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusNoContent, nil)
}

func (h *Handler) me(w http.ResponseWriter, r *http.Request) {
	userID, ok := UserIDFromContext(r.Context())
	if !ok {
		httpx.WriteError(w, http.StatusUnauthorized, "não autenticado", nil)
		return
	}

	user, err := h.service.GetUser(r.Context(), userID)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, user)
}

// writeServiceError traduz um erro do service para uma resposta HTTP.
func writeServiceError(w http.ResponseWriter, err error) {
	var validationErrs validator.ValidationErrors
	if errors.As(err, &validationErrs) {
		httpx.WriteError(w, http.StatusBadRequest, "dados inválidos", httpx.ValidationDetails(validationErrs))
		return
	}

	switch {
	case errors.Is(err, ErrEmailTaken):
		httpx.WriteError(w, http.StatusConflict, err.Error(), nil)
	case errors.Is(err, ErrInvalidCredentials):
		httpx.WriteError(w, http.StatusUnauthorized, err.Error(), nil)
	case errors.Is(err, ErrInvalidRefreshToken):
		httpx.WriteError(w, http.StatusUnauthorized, err.Error(), nil)
	case errors.Is(err, ErrAccountBlocked):
		httpx.WriteError(w, http.StatusForbidden, err.Error(), nil)
	case errors.Is(err, ErrAccountInactive):
		httpx.WriteError(w, http.StatusForbidden, err.Error(), nil)
	case errors.Is(err, ErrUserNotFound):
		httpx.WriteError(w, http.StatusNotFound, err.Error(), nil)
	default:
		httpx.WriteError(w, http.StatusInternalServerError, "erro interno", nil)
	}
}
