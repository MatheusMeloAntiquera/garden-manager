// Command api sobe o servidor HTTP da API do garden-manager.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/matheusantiquera/garden-manager/backend/config"
	"github.com/matheusantiquera/garden-manager/backend/internal/auth"
	"github.com/matheusantiquera/garden-manager/backend/internal/environment"
	"github.com/matheusantiquera/garden-manager/backend/internal/maintenance"
	"github.com/matheusantiquera/garden-manager/backend/internal/plant"
	"github.com/matheusantiquera/garden-manager/backend/internal/species"
	"github.com/matheusantiquera/garden-manager/backend/pkg/datetime"
	"github.com/matheusantiquera/garden-manager/backend/pkg/logger"
	"github.com/matheusantiquera/garden-manager/backend/pkg/password"
	"github.com/matheusantiquera/garden-manager/backend/pkg/postgres"
	"github.com/matheusantiquera/garden-manager/backend/pkg/token"
	"github.com/matheusantiquera/garden-manager/backend/pkg/validator"
)

func main() {
	log := logger.New()

	if err := run(log); err != nil {
		log.Error("encerrando com erro", "error", err)
		os.Exit(1)
	}
}

func run(log *slog.Logger) error {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	cfg, err := config.New()
	if err != nil {
		return err
	}

	location, err := time.LoadLocation(cfg.Timezone)
	if err != nil {
		return fmt.Errorf("carregando fuso APP_TIMEZONE: %w", err)
	}
	datetime.SetLocation(location)

	pool, err := postgres.New(ctx, cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer pool.Close()

	v, err := validator.New()
	if err != nil {
		return err
	}

	hasher := password.NewHasher()
	tokens := token.NewManager(cfg.JWTSecret, cfg.AccessTokenTTL)

	authRepo := auth.NewRepository(pool)
	authService, err := auth.NewService(authRepo, hasher, tokens, v, cfg.RefreshTokenTTL)
	if err != nil {
		return err
	}
	authHandler := auth.NewHandler(authService)

	environmentRepo := environment.NewRepository(pool)
	environmentService := environment.NewService(environmentRepo, v)
	environmentHandler := environment.NewHandler(environmentService)

	speciesRepo := species.NewRepository(pool)
	speciesService := species.NewService(speciesRepo)
	speciesHandler := species.NewHandler(speciesService)

	plantRepo := plant.NewRepository(pool)
	plantService := plant.NewService(plantRepo, environmentRepo, speciesRepo, v)
	plantHandler := plant.NewHandler(plantService)

	maintenanceService := maintenance.NewService(
		maintenance.NewTypeRepository(pool),
		maintenance.NewScheduleRepository(pool),
		maintenance.NewLogRepository(pool),
		plantRepo,
		v,
	)
	maintenanceHandler := maintenance.NewHandler(maintenanceService)

	mux := http.NewServeMux()
	authHandler.RegisterRoutes(mux, tokens)
	environmentHandler.RegisterRoutes(mux, tokens)
	speciesHandler.RegisterRoutes(mux, tokens)
	plantHandler.RegisterRoutes(mux, tokens)
	maintenanceHandler.RegisterRoutes(mux, tokens)

	server := &http.Server{
		Addr:              ":" + cfg.HTTPPort,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
	}

	serverErr := make(chan error, 1)
	go func() {
		log.Info("servidor iniciado", "port", cfg.HTTPPort)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serverErr <- err
			return
		}
		serverErr <- nil
	}()

	select {
	case <-ctx.Done():
		log.Info("encerrando servidor")
	case err := <-serverErr:
		if err != nil {
			return err
		}
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	return server.Shutdown(shutdownCtx)
}
