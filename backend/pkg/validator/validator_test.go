package validator

import "testing"

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

func generateString(n int) string {
	s := make([]byte, n)
	for i := range s {
		s[i] = 'a'
	}
	return string(s)
}
