# Garden Manager - Backend

API REST desenvolvida em Golang.

## Estrutura

O projeto segue o [Package Oriented Design](https://www.gobeyond.dev/packages-as-layers/):

```
backend/
├── cmd/api          # ponto de entrada da aplicação (main.go)
├── config           # carregamento de configuração via variáveis de ambiente
├── domain           # tipos de domínio (User, RefreshToken, Environment)
├── internal/auth    # domínio de autenticação: dto, service, repository, handler
├── internal/environment # domínio de ambientes: dto, service, repository, handler
├── pkg              # código reutilizável: postgres, password, token, validator, httpx, logger
└── migrations       # migrations SQL do banco
```

## Requisitos

- Go 1.22+
- Docker e Docker Compose

## Rodando localmente

1. Copie o arquivo de variáveis de ambiente e ajuste se necessário:

   ```bash
   cp .env.example .env
   ```

2. Suba o Postgres:

   ```bash
   make db-up
   ```

3. Rode as migrations:

   ```bash
   make migrate-up
   ```

4. Rode a API (as variáveis do `.env` precisam estar exportadas no shell, ou use uma ferramenta como `direnv`):

   ```bash
   make run
   ```

A API sobe em `http://localhost:8080`.

## Testes

```bash
make test
```

## Autenticação

### Rotas

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/auth/signup` | Cria uma conta |
| POST | `/api/v1/auth/login` | Autentica e retorna access + refresh token |
| POST | `/api/v1/auth/refresh` | Troca um refresh token válido por um novo par de tokens |
| POST | `/api/v1/auth/logout` | Revoga um refresh token |
| GET | `/api/v1/users/me` | Retorna os dados do usuário autenticado (requer `Authorization: Bearer <access_token>`) |

### Regras de senha

A senha deve ter entre 8 e 128 caracteres, com pelo menos um número e um caractere especial.

### Armazenamento de senha

As senhas são armazenadas com **argon2id** (hash + salt aleatório por senha), no formato PHC. A senha em texto puro nunca é persistida.

### Tokens

- **Access token**: JWT (HS256), válido por `ACCESS_TOKEN_TTL` (padrão 15 minutos).
- **Refresh token**: valor aleatório opaco, válido por `REFRESH_TOKEN_TTL` (padrão 30 dias). Apenas o hash SHA-256 dele é armazenado no banco. A cada uso em `/auth/refresh`, o token é rotacionado (o antigo é revogado e um novo é emitido).

### Bloqueio de conta

Após 3 tentativas de login com senha incorreta, a conta é bloqueada (`users.blocked = true`) e passa a retornar `403 Forbidden` em qualquer tentativa de login, mesmo com a senha correta.

**Desbloqueio (manual, por enquanto):**

```sql
UPDATE users SET blocked = false, login_attempts = 0 WHERE email = 'usuario@exemplo.com';
```

### Logout

O logout revoga o refresh token informado. Como a estratégia usa access + refresh token, o **access token em uso continua válido até expirar** (no máximo `ACCESS_TOKEN_TTL`, 15 minutos por padrão) — isso é esperado com essa abordagem, já que o JWT não tem estado no servidor.

### Preparado para OAuth (não implementado ainda)

- A coluna `users.password` aceita `NULL`, para contas que só terão login social no futuro.
- A coluna `users.email_verified_at` já existe, para permitir vincular contas por e-mail verificado.
- O login por senha rejeita contas sem senha cadastrada.

## Ambientes

Um ambiente é o espaço onde as plantas ficam (sala, cozinha, área externa…); o nome é livre e pode se repetir. Cada ambiente pertence ao usuário que o criou: todas as rotas exigem `Authorization: Bearer <access_token>` e só enxergam os ambientes do próprio usuário. Ambientes de outro usuário respondem `404`.

### Rotas

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/environments` | Cria um ambiente (`201`) |
| GET | `/api/v1/environments` | Lista os ambientes do usuário, ordenados por nome |
| GET | `/api/v1/environments/{id}` | Retorna um ambiente |
| PUT | `/api/v1/environments/{id}` | Substitui nome, observações e status do ambiente |
| DELETE | `/api/v1/environments/{id}` | Exclui o ambiente definitivamente (`204`) |

### Campos

| Campo | Regras |
|---|---|
| `name` | obrigatório, até 100 caracteres (espaços nas pontas são removidos) |
| `notes` | opcional, até 2000 caracteres; em branco vira `null` |
| `active` | opcional no POST (padrão `true`); obrigatório no PUT |

Como o PUT substitui o recurso inteiro, omitir `notes` nele limpa as observações.

### Listagem

Parâmetros de query (todos opcionais):

- `page`: página, a partir de 1 (padrão 1);
- `page_size`: itens por página, de 1 a 100 (padrão 20);
- `active`: `true` ou `false`, filtra pelo status. Sem ele, lista ativos e inativos.

Resposta:

```json
{ "data": [ { "id": "…", "name": "Cozinha", "notes": null, "active": true, "created_at": "…", "updated_at": "…" } ], "page": 1, "page_size": 20, "total": 1 }
```

## Exemplos (curl)

```bash
# Criar conta
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name":"Maria","email":"maria@example.com","password":"Sup3r$ecret","birth_date":"1990-05-20"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"maria@example.com","password":"Sup3r$ecret"}'

# Usuário autenticado
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <access_token>"

# Refresh
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token":"<refresh_token>"}'

# Logout
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refresh_token":"<refresh_token>"}'

# Criar ambiente
curl -X POST http://localhost:8080/api/v1/environments   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"name":"Cozinha","notes":"Janela voltada para o leste"}'

# Listar ambientes ativos
curl "http://localhost:8080/api/v1/environments?active=true&page=1&page_size=20"   -H "Authorization: Bearer <access_token>"

# Editar ambiente
curl -X PUT http://localhost:8080/api/v1/environments/<id>   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"name":"Cozinha","notes":"Janela voltada para o leste","active":false}'

# Excluir ambiente
curl -X DELETE http://localhost:8080/api/v1/environments/<id>   -H "Authorization: Bearer <access_token>"
```
