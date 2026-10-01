package plant

import "errors"

// Erros de negócio do domínio de plantas. Os handlers HTTP traduzem cada um
// deles para o status HTTP apropriado.
var (
	// ErrPlantNotFound também é retornado quando a planta existe mas pertence
	// a outro usuário, para não vazar a existência dela.
	ErrPlantNotFound = errors.New("planta não encontrada")

	// ErrInvalidEnvironment indica que o ambiente informado não existe ou
	// pertence a outro usuário.
	ErrInvalidEnvironment = errors.New("ambiente informado não encontrado")

	// ErrInvalidSpecies indica que a espécie informada não existe no catálogo.
	ErrInvalidSpecies = errors.New("espécie informada não encontrada")
)
