// Package httpx contém helpers para lidar com requisições e respostas HTTP
// em formato JSON, com um formato de erro padronizado.
package httpx

import (
	"encoding/json"
	"net/http"
)

// ErrorResponse é o formato padrão de erro retornado pela API.
type ErrorResponse struct {
	Error   string `json:"error"`
	Details any    `json:"details,omitempty"`
}

// JSON escreve v como JSON na resposta, com o status informado.
func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if v == nil {
		return
	}
	_ = json.NewEncoder(w).Encode(v)
}

// DecodeJSON decodifica o corpo da requisição em v.
func DecodeJSON(r *http.Request, v any) error {
	defer r.Body.Close()
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	return decoder.Decode(v)
}

// WriteError escreve um erro no formato padrão da API.
func WriteError(w http.ResponseWriter, status int, message string, details any) {
	JSON(w, status, ErrorResponse{Error: message, Details: details})
}
