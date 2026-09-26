// Package logger configura o logger estruturado da aplicação.
package logger

import (
	"log/slog"
	"os"
)

// New cria um logger JSON escrevendo em stdout.
func New() *slog.Logger {
	handler := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})
	return slog.New(handler)
}
