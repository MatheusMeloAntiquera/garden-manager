package domain

import (
	"time"

	"github.com/google/uuid"
)

// Species é uma espécie do catálogo de plantas. O catálogo é compartilhado
// entre todos os usuários e somente leitura pela API.
type Species struct {
	ID             uuid.UUID
	ScientificName string
	Family         string
	Category       string // folhagem, suculenta, flor, arvore, erva, hortalica, frutifera ou grama
	GBIFKey        *int64 // chave do táxon no GBIF; nula quando o GBIF agrupa táxons de uso distinto
	CommonNames    []CommonName

	CreatedAt time.Time
	UpdatedAt time.Time
}

// CommonName é um nome popular de uma espécie. O mesmo nome pode existir em
// espécies diferentes (ex.: caliandra-vermelha).
type CommonName struct {
	Name      string
	IsPrimary bool
}

// PrimaryName retorna o nome popular principal da espécie, ou nil se ela
// não tiver nenhum.
func (s Species) PrimaryName() *string {
	for _, n := range s.CommonNames {
		if n.IsPrimary {
			name := n.Name
			return &name
		}
	}
	return nil
}
