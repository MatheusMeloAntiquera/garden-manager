package species

import (
	"errors"
	"net/http"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/internal/auth"
	"github.com/matheusantiquera/garden-manager/backend/pkg/httpx"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
	"github.com/matheusantiquera/garden-manager/backend/pkg/token"
)

// Handler expõe as rotas HTTP do catálogo de espécies.
type Handler struct {
	service Service
}

// NewHandler cria um Handler do catálogo de espécies.
func NewHandler(service Service) *Handler {
	return &Handler{service: service}
}

// RegisterRoutes registra as rotas do catálogo no mux informado. Todas
// exigem autenticação via access token.
func (h *Handler) RegisterRoutes(mux *http.ServeMux, tokens token.Manager) {
	requireAuth := auth.RequireAuth(tokens)

	mux.Handle("GET /api/v1/species", requireAuth(http.HandlerFunc(h.list)))
	mux.Handle("GET /api/v1/species/{id}", requireAuth(http.HandlerFunc(h.get)))
}

func (h *Handler) list(w http.ResponseWriter, r *http.Request) {
	params, err := pagination.Parse(r)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, err.Error(), nil)
		return
	}

	query := r.URL.Query()
	result, err := h.service.List(r.Context(), ListInput{
		Params:   params,
		Query:    query.Get("q"),
		Category: query.Get("category"),
	})
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, result)
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "id de espécie inválido", nil)
		return
	}

	species, err := h.service.Get(r.Context(), id)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, species)
}

// writeServiceError traduz um erro do service para uma resposta HTTP.
func writeServiceError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, ErrSpeciesNotFound):
		httpx.WriteError(w, http.StatusNotFound, err.Error(), nil)
	case errors.Is(err, ErrInvalidCategory):
		httpx.WriteError(w, http.StatusBadRequest, err.Error(), nil)
	default:
		httpx.WriteError(w, http.StatusInternalServerError, "erro interno", nil)
	}
}
