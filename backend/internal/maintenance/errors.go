package maintenance

import "errors"

// Erros de negócio do domínio de manutenções. Os handlers HTTP traduzem cada
// um deles para o status HTTP apropriado.
var (
	// ErrScheduleNotFound e ErrLogNotFound também são retornados quando o
	// registro existe mas pertence a outro usuário, para não vazar a
	// existência dele.
	ErrScheduleNotFound = errors.New("agendamento de manutenção não encontrado")
	ErrLogNotFound      = errors.New("execução de manutenção não encontrada")

	// ErrTypeNotFound é retornado pelo repositório de tipos; nas escritas, o
	// service o converte em ErrInvalidType.
	ErrTypeNotFound = errors.New("tipo de manutenção não encontrado")

	// ErrInvalidPlant indica que a planta informada não existe ou pertence a
	// outro usuário.
	ErrInvalidPlant = errors.New("planta informada não encontrada")

	// ErrInvalidType indica que o tipo de manutenção informado não existe.
	ErrInvalidType = errors.New("tipo de manutenção informado não encontrado")

	// ErrInvalidSchedule indica que o agendamento informado em uma execução
	// não existe, pertence a outro usuário ou já foi executado (e, portanto,
	// excluído).
	ErrInvalidSchedule = errors.New("agendamento informado não encontrado")

	// ErrScheduleMismatch indica que a planta ou o tipo informados na
	// execução divergem dos do agendamento.
	ErrScheduleMismatch = errors.New("a execução precisa ser da mesma planta e do mesmo tipo do agendamento")

	// ErrPerformedAtInFuture indica uma execução com data no futuro.
	ErrPerformedAtInFuture = errors.New("a data de execução não pode estar no futuro")
)
