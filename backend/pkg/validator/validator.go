// Package validator centraliza a validação de structs de entrada da API,
// incluindo regras customizadas como a de senha forte.
package validator

import (
	"time"
	"unicode"

	"github.com/go-playground/validator/v10"
)

// DateLayout é o formato (AAAA-MM-DD) aceito pela regra birthdate.
const DateLayout = "2006-01-02"

// minBirthDate é a data de nascimento mais antiga aceita pela regra birthdate.
var minBirthDate = time.Date(1900, time.January, 1, 0, 0, 0, 0, time.UTC)

// Validator valida structs anotadas com tags `validate`.
type Validator struct {
	v *validator.Validate
}

// New cria um Validator com as regras customizadas da aplicação registradas.
func New() (*Validator, error) {
	v := validator.New(validator.WithRequiredStructEnabled())

	if err := v.RegisterValidation("strongpassword", validateStrongPassword); err != nil {
		return nil, err
	}
	if err := v.RegisterValidation("birthdate", validateBirthDate); err != nil {
		return nil, err
	}

	return &Validator{v: v}, nil
}

// Struct valida os campos de s de acordo com suas tags `validate`.
func (val *Validator) Struct(s any) error {
	return val.v.Struct(s)
}

// validateStrongPassword exige senha com 8 a 128 caracteres, contendo pelo
// menos um dígito e pelo menos um caractere especial.
func validateStrongPassword(fl validator.FieldLevel) bool {
	password := fl.Field().String()

	if len(password) < 8 || len(password) > 128 {
		return false
	}

	var hasDigit, hasSpecial bool
	for _, r := range password {
		switch {
		case unicode.IsDigit(r):
			hasDigit = true
		case !unicode.IsLetter(r) && !unicode.IsDigit(r) && !unicode.IsSpace(r):
			hasSpecial = true
		}
	}

	return hasDigit && hasSpecial
}

// validateBirthDate exige uma data no formato AAAA-MM-DD, entre 1900-01-01
// e a data de hoje (UTC), inclusive.
func validateBirthDate(fl validator.FieldLevel) bool {
	date, err := time.Parse(DateLayout, fl.Field().String())
	if err != nil {
		return false
	}

	today := time.Now().UTC().Truncate(24 * time.Hour)

	return !date.Before(minBirthDate) && !date.After(today)
}
