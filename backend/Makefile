.PHONY: db-up db-down migrate-up migrate-down run test

# Sobe o Postgres em background.
db-up:
	docker compose up -d postgres

# Derruba os containers e remove o volume do banco.
db-down:
	docker compose down -v

# Aplica todas as migrations pendentes.
migrate-up:
	docker compose run --rm migrate

# Desfaz a última migration aplicada.
migrate-down:
	docker compose run --rm migrate \
		-path=/migrations \
		-database=postgres://$${POSTGRES_USER:-garden}:$${POSTGRES_PASSWORD:-garden}@postgres:5432/$${POSTGRES_DB:-garden_manager}?sslmode=disable \
		down 1

# Roda a API localmente (requer as variáveis do .env exportadas no shell).
run:
	go run ./cmd/api

# Roda todos os testes.
test:
	go test ./...
