package auth

import "errors"

// Erros de negócio do domínio de autenticação. Os handlers HTTP traduzem
// cada um deles para o status HTTP apropriado.
var (
	ErrEmailTaken          = errors.New("e-mail já cadastrado")
	ErrInvalidCredentials  = errors.New("e-mail ou senha inválidos")
	ErrAccountBlocked      = errors.New("conta bloqueada após múltiplas tentativas de login inválidas")
	ErrAccountInactive     = errors.New("conta inativa")
	ErrInvalidRefreshToken = errors.New("refresh token inválido ou expirado")
	ErrUserNotFound        = errors.New("usuário não encontrado")
)
