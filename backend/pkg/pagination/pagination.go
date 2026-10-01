// Package pagination lê e normaliza os parâmetros page e page_size das
// listagens paginadas da API.
package pagination

import (
	"errors"
	"net/http"
	"strconv"
)

// Limites de paginação das listagens.
const (
	DefaultPageSize = 20
	MaxPageSize     = 100
)

// Params reúne os parâmetros de paginação de uma listagem.
type Params struct {
	Page     int // começa em 1
	PageSize int // de 1 a MaxPageSize
}

// Parse lê page e page_size da query string. Parâmetros ausentes ficam com o
// valor zero e devem ser completados por Normalize.
func Parse(r *http.Request) (Params, error) {
	query := r.URL.Query()
	var params Params

	if raw := query.Get("page"); raw != "" {
		page, err := strconv.Atoi(raw)
		if err != nil || page < 1 {
			return Params{}, errors.New("parâmetro page inválido")
		}
		params.Page = page
	}

	if raw := query.Get("page_size"); raw != "" {
		pageSize, err := strconv.Atoi(raw)
		if err != nil || pageSize < 1 || pageSize > MaxPageSize {
			return Params{}, errors.New("parâmetro page_size inválido (1 a " + strconv.Itoa(MaxPageSize) + ")")
		}
		params.PageSize = pageSize
	}

	return params, nil
}

// Normalize aplica os valores padrão e os limites, retornando parâmetros
// prontos para uso.
func (p Params) Normalize() Params {
	p.Page = max(p.Page, 1)

	if p.PageSize < 1 {
		p.PageSize = DefaultPageSize
	}
	p.PageSize = min(p.PageSize, MaxPageSize)

	return p
}

// Offset retorna quantos itens pular para chegar à página atual. Espera
// parâmetros já normalizados.
func (p Params) Offset() int {
	return (p.Page - 1) * p.PageSize
}
