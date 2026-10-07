package maintenance

import (
	"errors"
	"net/http"

	"github.com/go-playground/validator/v10"
	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/internal/auth"
	"github.com/matheusantiquera/garden-manager/backend/internal/plant"
	"github.com/matheusantiquera/garden-manager/backend/pkg/datetime"
	"github.com/matheusantiquera/garden-manager/backend/pkg/httpx"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
	"github.com/matheusantiquera/garden-manager/backend/pkg/token"
)

// Handler expõe as rotas HTTP de manutenções.
type Handler struct {
	service Service
}

// NewHandler cria um Handler de manutenções.
func NewHandler(service Service) *Handler {
	return &Handler{service: service}
}

// RegisterRoutes registra as rotas de manutenções no mux informado, incluindo
// as listagens aninhadas em /plants/{id}. Todas exigem autenticação via
// access token.
func (h *Handler) RegisterRoutes(mux *http.ServeMux, tokens token.Manager) {
	requireAuth := auth.RequireAuth(tokens)

	mux.Handle("GET /api/v1/maintenance-types", requireAuth(http.HandlerFunc(h.listTypes)))

	mux.Handle("POST /api/v1/maintenance-schedules", requireAuth(http.HandlerFunc(h.createSchedule)))
	mux.Handle("GET /api/v1/maintenance-schedules", requireAuth(http.HandlerFunc(h.listSchedules)))
	mux.Handle("GET /api/v1/maintenance-schedules/{id}", requireAuth(http.HandlerFunc(h.getSchedule)))
	mux.Handle("PUT /api/v1/maintenance-schedules/{id}", requireAuth(http.HandlerFunc(h.updateSchedule)))
	mux.Handle("DELETE /api/v1/maintenance-schedules/{id}", requireAuth(http.HandlerFunc(h.deleteSchedule)))

	mux.Handle("POST /api/v1/maintenance-logs", requireAuth(http.HandlerFunc(h.createLog)))
	mux.Handle("GET /api/v1/maintenance-logs", requireAuth(http.HandlerFunc(h.listLogs)))
	mux.Handle("GET /api/v1/maintenance-logs/{id}", requireAuth(http.HandlerFunc(h.getLog)))
	mux.Handle("PUT /api/v1/maintenance-logs/{id}", requireAuth(http.HandlerFunc(h.updateLog)))
	mux.Handle("DELETE /api/v1/maintenance-logs/{id}", requireAuth(http.HandlerFunc(h.deleteLog)))

	mux.Handle("GET /api/v1/plants/{id}/maintenance-schedules", requireAuth(http.HandlerFunc(h.listPlantSchedules)))
	mux.Handle("GET /api/v1/plants/{id}/maintenance-logs", requireAuth(http.HandlerFunc(h.listPlantLogs)))
}

func (h *Handler) listTypes(w http.ResponseWriter, r *http.Request) {
	types, err := h.service.ListTypes(r.Context())
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, types)
}

func (h *Handler) createSchedule(w http.ResponseWriter, r *http.Request) {
	userID, ok := authenticatedUser(w, r)
	if !ok {
		return
	}

	var input ScheduleInput
	if !decodeBody(w, r, &input) {
		return
	}

	schedule, err := h.service.CreateSchedule(r.Context(), userID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusCreated, schedule)
}

func (h *Handler) listSchedules(w http.ResponseWriter, r *http.Request) {
	userID, ok := authenticatedUser(w, r)
	if !ok {
		return
	}

	input, err := parseListSchedulesInput(r)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, err.Error(), nil)
		return
	}

	schedules, err := h.service.ListSchedules(r.Context(), userID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, schedules)
}

func (h *Handler) listPlantSchedules(w http.ResponseWriter, r *http.Request) {
	userID, plantID, ok := userAndPathID(w, r, "id de planta inválido")
	if !ok {
		return
	}

	input, err := parseListSchedulesInput(r)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, err.Error(), nil)
		return
	}

	schedules, err := h.service.ListPlantSchedules(r.Context(), userID, plantID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, schedules)
}

func (h *Handler) getSchedule(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndPathID(w, r, "id de agendamento inválido")
	if !ok {
		return
	}

	schedule, err := h.service.GetSchedule(r.Context(), userID, id)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, schedule)
}

func (h *Handler) updateSchedule(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndPathID(w, r, "id de agendamento inválido")
	if !ok {
		return
	}

	var input ScheduleInput
	if !decodeBody(w, r, &input) {
		return
	}

	schedule, err := h.service.UpdateSchedule(r.Context(), userID, id, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, schedule)
}

func (h *Handler) deleteSchedule(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndPathID(w, r, "id de agendamento inválido")
	if !ok {
		return
	}

	if err := h.service.DeleteSchedule(r.Context(), userID, id); err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusNoContent, nil)
}

func (h *Handler) createLog(w http.ResponseWriter, r *http.Request) {
	userID, ok := authenticatedUser(w, r)
	if !ok {
		return
	}

	var input CreateLogInput
	if !decodeBody(w, r, &input) {
		return
	}

	log, err := h.service.CreateLog(r.Context(), userID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusCreated, log)
}

func (h *Handler) listLogs(w http.ResponseWriter, r *http.Request) {
	userID, ok := authenticatedUser(w, r)
	if !ok {
		return
	}

	input, err := parseListLogsInput(r)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, err.Error(), nil)
		return
	}

	logs, err := h.service.ListLogs(r.Context(), userID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, logs)
}

func (h *Handler) listPlantLogs(w http.ResponseWriter, r *http.Request) {
	userID, plantID, ok := userAndPathID(w, r, "id de planta inválido")
	if !ok {
		return
	}

	input, err := parseListLogsInput(r)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, err.Error(), nil)
		return
	}

	logs, err := h.service.ListPlantLogs(r.Context(), userID, plantID, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, logs)
}

func (h *Handler) getLog(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndPathID(w, r, "id de execução inválido")
	if !ok {
		return
	}

	log, err := h.service.GetLog(r.Context(), userID, id)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, log)
}

func (h *Handler) updateLog(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndPathID(w, r, "id de execução inválido")
	if !ok {
		return
	}

	var input UpdateLogInput
	if !decodeBody(w, r, &input) {
		return
	}

	log, err := h.service.UpdateLog(r.Context(), userID, id, input)
	if err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusOK, log)
}

func (h *Handler) deleteLog(w http.ResponseWriter, r *http.Request) {
	userID, id, ok := userAndPathID(w, r, "id de execução inválido")
	if !ok {
		return
	}

	if err := h.service.DeleteLog(r.Context(), userID, id); err != nil {
		writeServiceError(w, err)
		return
	}

	httpx.JSON(w, http.StatusNoContent, nil)
}

// authenticatedUser extrai o usuário autenticado. Em caso de falha, já
// escreve a resposta de erro e retorna ok=false.
func authenticatedUser(w http.ResponseWriter, r *http.Request) (uuid.UUID, bool) {
	userID, ok := auth.UserIDFromContext(r.Context())
	if !ok {
		httpx.WriteError(w, http.StatusUnauthorized, "não autenticado", nil)
	}
	return userID, ok
}

// userAndPathID extrai o usuário autenticado e o {id} da rota, usando
// invalidIDMessage quando o id não é um UUID. Em caso de falha, já escreve a
// resposta de erro e retorna ok=false.
func userAndPathID(w http.ResponseWriter, r *http.Request, invalidIDMessage string) (userID, id uuid.UUID, ok bool) {
	userID, ok = authenticatedUser(w, r)
	if !ok {
		return uuid.Nil, uuid.Nil, false
	}

	id, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, invalidIDMessage, nil)
		return uuid.Nil, uuid.Nil, false
	}

	return userID, id, true
}

// decodeBody lê o corpo JSON em v. Em caso de falha, já escreve a resposta de
// erro (com mensagem específica para datas fora do formato) e retorna false.
func decodeBody(w http.ResponseWriter, r *http.Request, v any) bool {
	err := httpx.DecodeJSON(r, v)
	switch {
	case err == nil:
		return true
	case errors.Is(err, datetime.ErrInvalidFormat):
		httpx.WriteError(w, http.StatusBadRequest, datetime.ErrInvalidFormat.Error(), nil)
	default:
		httpx.WriteError(w, http.StatusBadRequest, "corpo da requisição inválido", nil)
	}
	return false
}

// parseListSchedulesInput lê page, page_size, plant_id, type_id, status, plant_active,
// due_from e due_to da query string.
func parseListSchedulesInput(r *http.Request) (ListSchedulesInput, error) {
	params, err := pagination.Parse(r)
	if err != nil {
		return ListSchedulesInput{}, err
	}
	input := ListSchedulesInput{Params: params}
	query := r.URL.Query()

	if input.PlantID, err = parseUUIDParam(query.Get("plant_id"), "plant_id"); err != nil {
		return ListSchedulesInput{}, err
	}
	if input.TypeID, err = parseUUIDParam(query.Get("type_id"), "type_id"); err != nil {
		return ListSchedulesInput{}, err
	}

	if raw := query.Get("status"); raw != "" {
		status := domain.MaintenanceStatus(raw)
		switch status {
		case domain.MaintenanceStatusPending, domain.MaintenanceStatusOverdue:
			input.Status = &status
		default:
			return ListSchedulesInput{}, errors.New("parâmetro status inválido (pending ou overdue)")
		}
	}

	if input.DueFrom, err = parseDateTimeParam(query.Get("due_from"), "due_from"); err != nil {
		return ListSchedulesInput{}, err
	}
	if input.PlantActive, err = parseBoolParam(query.Get("plant_active"), "plant_active"); err != nil {
		return ListSchedulesInput{}, err
	}

	if input.DueTo, err = parseDateTimeParam(query.Get("due_to"), "due_to"); err != nil {
		return ListSchedulesInput{}, err
	}

	return input, nil
}

// parseListLogsInput lê page, page_size, plant_id, type_id, plant_active, performed_from e
// performed_to da query string.
func parseListLogsInput(r *http.Request) (ListLogsInput, error) {
	params, err := pagination.Parse(r)
	if err != nil {
		return ListLogsInput{}, err
	}
	input := ListLogsInput{Params: params}
	query := r.URL.Query()

	if input.PlantID, err = parseUUIDParam(query.Get("plant_id"), "plant_id"); err != nil {
		return ListLogsInput{}, err
	}
	if input.TypeID, err = parseUUIDParam(query.Get("type_id"), "type_id"); err != nil {
		return ListLogsInput{}, err
	}
	if input.PerformedFrom, err = parseDateTimeParam(query.Get("performed_from"), "performed_from"); err != nil {
		return ListLogsInput{}, err
	}
	if input.PlantActive, err = parseBoolParam(query.Get("plant_active"), "plant_active"); err != nil {
		return ListLogsInput{}, err
	}

	if input.PerformedTo, err = parseDateTimeParam(query.Get("performed_to"), "performed_to"); err != nil {
		return ListLogsInput{}, err
	}

	return input, nil
}

// parseUUIDParam converte um parâmetro opcional da query string em UUID.
func parseUUIDParam(raw, name string) (*uuid.UUID, error) {
	if raw == "" {
		return nil, nil
	}
	id, err := uuid.Parse(raw)
	if err != nil {
		return nil, errors.New("parâmetro " + name + " inválido")
	}
	return &id, nil
}

// parseBoolParam converte um parâmetro opcional da query string em bool (true ou false).
func parseBoolParam(raw, name string) (*bool, error) {
	switch raw {
	case "":
		return nil, nil
	case "true":
		v := true
		return &v, nil
	case "false":
		v := false
		return &v, nil
	}
	return nil, errors.New("parâmetro " + name + " inválido (true ou false)")
}

// parseDateTimeParam converte um parâmetro opcional da query string no
// formato AAAA-MM-DD HH:mm:ss.
func parseDateTimeParam(raw, name string) (*datetime.LocalDateTime, error) {
	if raw == "" {
		return nil, nil
	}
	d, err := datetime.Parse(raw)
	if err != nil {
		return nil, errors.New("parâmetro " + name + " inválido (use o formato AAAA-MM-DD HH:mm:ss)")
	}
	return &d, nil
}

// writeServiceError traduz um erro do service para uma resposta HTTP.
func writeServiceError(w http.ResponseWriter, err error) {
	var validationErrs validator.ValidationErrors
	if errors.As(err, &validationErrs) {
		httpx.WriteError(w, http.StatusBadRequest, "dados inválidos", httpx.ValidationDetails(validationErrs))
		return
	}

	switch {
	case errors.Is(err, ErrScheduleNotFound), errors.Is(err, ErrLogNotFound), errors.Is(err, plant.ErrPlantNotFound):
		httpx.WriteError(w, http.StatusNotFound, err.Error(), nil)
	case errors.Is(err, ErrInvalidPlant), errors.Is(err, ErrArchivedPlant), errors.Is(err, ErrInvalidType), errors.Is(err, ErrInvalidSchedule),
		errors.Is(err, ErrScheduleMismatch), errors.Is(err, ErrPerformedAtInFuture):
		httpx.WriteError(w, http.StatusUnprocessableEntity, err.Error(), nil)
	default:
		httpx.WriteError(w, http.StatusInternalServerError, "erro interno", nil)
	}
}
