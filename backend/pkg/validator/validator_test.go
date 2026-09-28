package validator

import (
	"testing"
	"time"
)

type signupInput struct {
	Password string `validate:"required,strongpassword"`
}

func TestStrongPasswordValidation(t *testing.T) {
	v, err := New()
	if err != nil {
		t.Fatalf("New retornou erro: %v", err)
	}

	cases := []struct {
		name     string
		password string
		valid    bool
	}{
		{"curta", "a1!", false},
		{"sem numero", "abcdefg!", false},
		{"sem caractere especial", "abcdefg1", false},
		{"so letras e espacos", "abc defg", false},
		{"valida", "abcdefg1!", true},
		{"valida com espaco", "abc def1!", true},
		{"muito longa", generateString(129) + "1!", false},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			err := v.Struct(signupInput{Password: tc.password})
			if tc.valid && err != nil {
				t.Fatalf("esperava senha válida, obteve erro: %v", err)
			}
			if !tc.valid && err == nil {
				t.Fatal("esperava erro de validação, obteve nil")
			}
		})
	}
}

type birthDateInput struct {
	BirthDate string `validate:"required,birthdate"`
}

func TestBirthDateValidation(t *testing.T) {
	v, err := New()
	if err != nil {
		t.Fatalf("New retornou erro: %v", err)
	}

	today := time.Now().UTC()

	cases := []struct {
		name      string
		birthDate string
		valid     bool
	}{
		{"valida", "1990-05-20", true},
		{"hoje", today.Format(DateLayout), true},
		{"limite minimo", "1900-01-01", true},
		{"futura", today.AddDate(0, 0, 1).Format(DateLayout), false},
		{"antes de 1900", "1899-12-31", false},
		{"formato invalido", "20/05/1990", false},
		{"data inexistente", "1990-02-30", false},
		{"vazia", "", false},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			err := v.Struct(birthDateInput{BirthDate: tc.birthDate})
			if tc.valid && err != nil {
				t.Fatalf("esperava data válida, obteve erro: %v", err)
			}
			if !tc.valid && err == nil {
				t.Fatal("esperava erro de validação, obteve nil")
			}
		})
	}
}

func generateString(n int) string {
	s := make([]byte, n)
	for i := range s {
		s[i] = 'a'
	}
	return string(s)
}
