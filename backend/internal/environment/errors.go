package environment

import "errors"

// Erros de negócio do domínio de ambientes. Os handlers HTTP traduzem cada
// um deles para o status HTTP apropriado.
var (
	// ErrEnvironmentNotFound também é retornado quando o ambiente existe mas
	// pertence a outro usuário, para não vazar a existência dele.
	ErrEnvironmentNotFound = errors.New("ambiente não encontrado")
)
