package plant

import (
	"errors"
	"net/http"
	"strconv"

	"github.com/go-playground/validator/v10"
	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/internal/auth"
	"github.com/matheusantiquera/garden-manager/backend/pkg/httpx"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
	"github.com/matheusantiquera/garden-manager/backend/pkg/token"
)

// Handler expõe as rotas HTTP de plantas.
type Handler struct {
	service Service
}

// NewHandler cria um Handler de plantas.
func NewHandler(service Service) *Handler {
	return &Handler{service: service}
}

// RegisterRoutes registra as rotas de plantas no mux informado. Todas exigem
// autenticação via access token.
func (h *Handler) RegisterRoutes(mux *http.ServeMux, tokens token.Manager) {
	requireAuth := auth.RequireAuth(tokens)

	mux.Handle("POST /api/v1/plants", requireAuth(http.HandlerFunc(h.create)))
	mux.Handle("GET /api/v1/plants", requireAuth(http.HandlerFunc(h.list)))
	mux.Handle("GET /api/v1/plants/{id}", requireAuth(http.HandlerFunc(h.get)))
	mux.Handle("PUT /api/v1/plants/{id}", requireAuth(http.HandlerFunc(h.update)))
	mux.Handle("DELETE /api/v1/plants/{id}", requireAuth(http.HandlerFunc(h.delete)))
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

	plant, err := h.service.Create(r.Context(), userID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusCreated, plant)
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

	plants, err := h.service.List(r.Context(), userID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, plants)
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndPlantID(w, r)
	if !ok {
		return
	}

	plant, err := h.service.Get(r.Context(), userID, id)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, plant)
}

func (h *Handler) update(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndPlantID(w, r)
	if !ok {
		return
	}

	var input UpdateInput
	if err := httpx.DecodeJSON(r, &input); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "corpo da requisição inválido", nil)
		return
	}

	plant, err := h.service.Update(r.Context(), userID, id, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, plant)
}

func (h *Handler) delete(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndPlantID(w, r)
	if !ok {
		return
	}

	if err := h.service.Delete(r.Context(), userID, id); err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusNoContent, nil)
}

// userAndPlantID extrai o usuário autenticado e o {id} da rota. Em caso de
// falha, já escreve a resposta de erro e retorna ok=false.
func userAndPlantID(w http.ResponseWriter, r *http.Request) (userID, id uuid.UUID, ok bool) {
	userID, ok = auth.UserIDFromContext(r.Context())
	if !ok {
		httpx.WriteError(w, http.StatusUnauthorized, "não autenticado", nil)
		return uuid.Nil, uuid.Nil, false
	}

	id, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "id de planta inválido", nil)
		return uuid.Nil, uuid.Nil, false
	}

	return userID, id, true
}

// parseListInput lê page, page_size, environment_id, species_id, active e q da
// query string. Parâmetros ausentes ficam nulos ou com o valor zero e são
// normalizados pelo service.
func parseListInput(r *http.Request) (ListInput, error) {
	params, err := pagination.Parse(r)
	if err != nil {
		return ListInput{}, err
	}
	query := r.URL.Query()
	input := ListInput{Params: params, Query: query.Get("q")}

	if raw := query.Get("environment_id"); raw != "" {
		id, err := uuid.Parse(raw)
		if err != nil {
			return ListInput{}, errors.New("parâmetro environment_id inválido")
		}
		input.EnvironmentID = &id
	}

	if raw := query.Get("species_id"); raw != "" {
		id, err := uuid.Parse(raw)
		if err != nil {
			return ListInput{}, errors.New("parâmetro species_id inválido")
		}
		input.SpeciesID = &id
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
	case errors.Is(err, ErrPlantNotFound):
		httpx.WriteError(w, http.StatusNotFound, err.Error(), nil)
	case errors.Is(err, ErrInvalidEnvironment), errors.Is(err, ErrInvalidSpecies):
		httpx.WriteError(w, http.StatusUnprocessableEntity, err.Error(), nil)
	default:
		httpx.WriteError(w, http.StatusInternalServerError, "erro interno", nil)
	}
}
