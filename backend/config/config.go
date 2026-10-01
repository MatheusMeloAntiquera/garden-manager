// Package config carrega a configuração da aplicação a partir de variáveis
// de ambiente.
package config

import (
	"fmt"
	"time"

	"github.com/kelseyhightower/envconfig"
)

// Config agrupa toda a configuração da API.
type Config struct {
	HTTPPort string `envconfig:"HTTP_PORT" default:"8080"`

	DatabaseURL string `envconfig:"DATABASE_URL" required:"true"`

	JWTSecret       string        `envconfig:"JWT_SECRET" required:"true"`
	AccessTokenTTL  time.Duration `envconfig:"ACCESS_TOKEN_TTL" default:"15m"`
	RefreshTokenTTL time.Duration `envconfig:"REFRESH_TOKEN_TTL" default:"720h"`

	// Timezone é o fuso em que a API interpreta e formata datas sem offset,
	// como "AAAA-MM-DD HH:mm:ss" (ex.: prazos de manutenção).
	Timezone string `envconfig:"APP_TIMEZONE" default:"America/Sao_Paulo"`
}

// New carrega a configuração a partir das variáveis de ambiente.
func New() (Config, error) {
	var cfg Config
	if err := envconfig.Process("", &cfg); err != nil {
		return Config{}, fmt.Errorf("carregando configuração: %w", err)
	}
	return cfg, nil
}
