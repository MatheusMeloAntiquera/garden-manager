package httpx

import "github.com/go-playground/validator/v10"

// ValidationDetails converte erros de validação em uma lista de mensagens
// no formato "Campo: regra", usada no campo details das respostas de erro.
func ValidationDetails(errs validator.ValidationErrors) []string {
	messages := make([]string, 0, len(errs))
	for _, fieldErr := range errs {
		messages = append(messages, fieldErr.Field()+": "+fieldErr.Tag())
	}
	return messages
}
