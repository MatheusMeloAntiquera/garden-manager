package environment

import (
	"errors"
	"net/http"
	"strconv"

	"github.com/go-playground/validator/v10"
	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/internal/auth"
	"github.com/matheusantiquera/garden-manager/backend/pkg/httpx"
	"github.com/matheusantiquera/garden-manager/backend/pkg/token"
)

// Handler expõe as rotas HTTP de ambientes.
type Handler struct {
	service Service
}

// NewHandler cria um Handler de ambientes.
func NewHandler(service Service) *Handler {
	return &Handler{service: service}
}

// RegisterRoutes registra as rotas de ambientes no mux informado. Todas
// exigem autenticação via access token.
func (h *Handler) RegisterRoutes(mux *http.ServeMux, tokens token.Manager) {
	requireAuth := auth.RequireAuth(tokens)

	mux.Handle("POST /api/v1/environments", requireAuth(http.HandlerFunc(h.create)))
	mux.Handle("GET /api/v1/environments", requireAuth(http.HandlerFunc(h.list)))
	mux.Handle("GET /api/v1/environments/{id}", requireAuth(http.HandlerFunc(h.get)))
	mux.Handle("PUT /api/v1/environments/{id}", requireAuth(http.HandlerFunc(h.update)))
	mux.Handle("DELETE /api/v1/environments/{id}", requireAuth(http.HandlerFunc(h.delete)))
}

func (h *Handler) create(w http.ResponseWriter, r *http.Request) {
	userID, ok := auth.UserIDFromContext(r.Context())
	if !ok {
		httpx.WriteError(w, http.StatusUnauthorized, "não autenticado", nil)
		return
	}

	var input CreateInput
	if err := httpx.DecodeJSON(r, &input); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "corpo da requisição inválido", nil)
		return
	}

	env, err := h.service.Create(r.Context(), userID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusCreated, env)
}

func (h *Handler) list(w http.ResponseWriter, r *http.Request) {
	userID, ok := auth.UserIDFromContext(r.Context())
	if !ok {
		httpx.WriteError(w, http.StatusUnauthorized, "não autenticado", nil)
		return
	}

	input, err := parseListInput(r)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, err.Error(), nil)
		return
	}

	envs, err := h.service.List(r.Context(), userID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, envs)
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndEnvironmentID(w, r)
	if !ok {
		return
	}

	env, err := h.service.Get(r.Context(), userID, id)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, env)
}

func (h *Handler) update(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndEnvironmentID(w, r)
	if !ok {
		return
	}

	var input UpdateInput
	if err := httpx.DecodeJSON(r, &input); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "corpo da requisição inválido", nil)
		return
	}

	env, err := h.service.Update(r.Context(), userID, id, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, env)
}

func (h *Handler) delete(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndEnvironmentID(w, r)
	if !ok {
		return
	}

	if err := h.service.Delete(r.Context(), userID, id); err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusNoContent, nil)
}

// userAndEnvironmentID extrai o usuário autenticado e o {id} da rota. Em
// caso de falha, já escreve a resposta de erro e retorna ok=false.
func userAndEnvironmentID(w http.ResponseWriter, r *http.Request) (userID, id uuid.UUID, ok bool) {
	userID, ok = auth.UserIDFromContext(r.Context())
	if !ok {
		httpx.WriteError(w, http.StatusUnauthorized, "não autenticado", nil)
		return uuid.Nil, uuid.Nil, false
	}

	id, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "id de ambiente inválido", nil)
		return uuid.Nil, uuid.Nil, false
	}

	return userID, id, true
}

// parseListInput lê os parâmetros page, page_size e active da query string.
// Parâmetros ausentes ficam com o valor zero e são normalizados pelo service.
func parseListInput(r *http.Request) (ListInput, error) {
	query := r.URL.Query()
	var input ListInput

	if raw := query.Get("page"); raw != "" {
		page, err := strconv.Atoi(raw)
		if err != nil || page < 1 {
			return ListInput{}, errors.New("parâmetro page inválido")
		}
		input.Page = page
	}

	if raw := query.Get("page_size"); raw != "" {
		pageSize, err := strconv.Atoi(raw)
		if err != nil || pageSize < 1 || pageSize > MaxPageSize {
			return ListInput{}, errors.New("parâmetro page_size inválido (1 a " + strconv.Itoa(MaxPageSize) + ")")
		}
		input.PageSize = pageSize
	}

	if raw := query.Get("active"); raw != "" {
		active, err := strconv.ParseBool(raw)
		if err != nil {
			return ListInput{}, errors.New("parâmetro active inválido (true ou false)")
		}
		input.Active = &active
	}

	return input, nil
}

// writeServiceError traduz um erro do service para uma resposta HTTP.
func writeServiceError(w http.ResponseWriter, err error) {
	var validationErrs validator.ValidationErrors
	if errors.As(err, &validationErrs) {
		httpx.WriteError(w, http.StatusBadRequest, "dados inválidos", httpx.ValidationDetails(validationErrs))
		return
	}

	switch {
	case errors.Is(err, ErrEnvironmentNotFound):
		httpx.WriteError(w, http.StatusNotFound, err.Error(), nil)
	default:
		httpx.WriteError(w, http.StatusInternalServerError, "erro interno", nil)
	}
}
