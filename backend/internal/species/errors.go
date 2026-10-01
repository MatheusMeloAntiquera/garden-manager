package species

import "errors"

// Erros de negócio do catálogo de espécies. Os handlers HTTP traduzem cada
// um deles para o status HTTP apropriado.
var (
	ErrSpeciesNotFound = errors.New("espécie não encontrada")
	ErrInvalidCategory = errors.New("categoria inválida")
)
