# Garden Manager - Backend

API REST desenvolvida em Golang.

## Estrutura

O projeto segue o [Package Oriented Design](https://www.gobeyond.dev/packages-as-layers/):

```
backend/
├── cmd/api          # ponto de entrada da aplicação (main.go)
├── config           # carregamento de configuração via variáveis de ambiente
├── domain           # tipos de domínio (User, RefreshToken, Environment, Species, Plant, Maintenance*)
├── internal/auth    # domínio de autenticação: dto, service, repository, handler
├── internal/environment # domínio de ambientes: dto, service, repository, handler
├── internal/species # catálogo de espécies (somente leitura): dto, service, repository, handler
├── internal/plant   # domínio de plantas: dto, service, repository, handler
├── internal/maintenance # domínio de manutenções (tipos, agendamentos e execuções): dto, service, repositories, handler
├── pkg              # código reutilizável: postgres, password, token, validator, httpx, pagination, datetime, logger
└── migrations       # migrations SQL do banco
```

## Requisitos

- Go 1.22+
- Docker e Docker Compose (o Postgres 18 sobe pelo Compose; as migrations usam `uuidv7()`, que só existe a partir da versão 18)

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
{ "data": [ { "id": "…", "name": "Cozinha", "notes": null, "active": true, "plant_count": 2, "created_at": "…", "updated_at": "…" } ], "page": 1, "page_size": 20, "total": 1 }
```

`plant_count` é a quantidade de plantas **ativas** do ambiente; plantas inativas (arquivadas) não entram na conta. Ele vem na listagem, no `GET` por id e nas respostas de `POST` e `PUT`, e é somente leitura (não vai no corpo das requisições).

## Catálogo de espécies

Catálogo compartilhado entre todos os usuários e **somente leitura** pela API, com 192 espécies comuns em casas, jardins e hortas no Brasil (folhagens, suculentas, flores, árvores, ervas, hortaliças, frutíferas e gramas). Ele é carregado pela migration `000006_seed_species`; os nomes científicos foram conferidos na API do [GBIF](https://www.gbif.org/) e o `gbif_key` (quando existe) guarda o táxon correspondente lá.

Cada espécie tem um nome científico e vários nomes populares em português, um deles principal. Um mesmo nome popular pode valer para espécies diferentes (ex.: `caliandra-vermelha`).

Todas as rotas exigem `Authorization: Bearer <access_token>`.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/v1/species` | Busca e lista espécies, ordenadas pelo nome popular principal |
| GET | `/api/v1/species/{id}` | Retorna uma espécie |

Parâmetros de query da listagem (todos opcionais):

- `q`: busca por nome popular ou científico, sem diferenciar maiúsculas nem acentos (`ipe` acha `ipê-amarelo`);
- `category`: `folhagem`, `suculenta`, `flor`, `arvore`, `erva`, `hortalica`, `frutifera` ou `grama`;
- `page` e `page_size`: paginação, como em ambientes (padrão 1 e 20; máximo de 100 por página).

Resposta de uma espécie:

```json
{ "id": "…", "scientific_name": "Ficus elastica", "family": "Moraceae", "category": "folhagem", "common_name": "falsa-seringueira", "common_names": ["falsa-seringueira", "figueira-da-borracha", "seringueira-de-jardim", "árvore-da-borracha"] }
```

## Plantas

Uma planta é um exemplar que o usuário tem. Ela pode apontar para uma espécie do catálogo e para um ambiente, e pertence ao usuário que a criou: todas as rotas exigem `Authorization: Bearer <access_token>` e só enxergam as plantas do próprio usuário. Plantas de outro usuário respondem `404`.

### Rotas

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/plants` | Cria uma planta (`201`) |
| GET | `/api/v1/plants` | Lista as plantas do usuário, ordenadas pelo nome de exibição |
| GET | `/api/v1/plants/{id}` | Retorna uma planta |
| PUT | `/api/v1/plants/{id}` | Substitui espécie, ambiente, apelido, observações e status |
| DELETE | `/api/v1/plants/{id}` | Exclui a planta definitivamente (`204`), junto com as manutenções dela |
| GET | `/api/v1/plants/{id}/maintenance-schedules` | Lista os agendamentos de manutenção da planta (ver [Manutenções](#manutenções)) |
| GET | `/api/v1/plants/{id}/maintenance-logs` | Lista as execuções de manutenção da planta (ver [Manutenções](#manutenções)) |

### Campos

| Campo | Regras |
|---|---|
| `species_id` | opcional; precisa existir no catálogo |
| `environment_id` | opcional; precisa ser um ambiente do próprio usuário |
| `nickname` | até 100 caracteres (espaços nas pontas são removidos); **obrigatório quando não há `species_id`** |
| `notes` | opcional, até 2000 caracteres; em branco vira `null` |
| `active` | opcional no POST (padrão `true`); obrigatório no PUT |

Toda planta precisa de apelido ou de espécie. Como o PUT substitui o recurso inteiro, omitir `environment_id`, `species_id` ou `notes` nele os limpa.

Um `species_id` ou `environment_id` inexistente (ou de outro usuário) responde `422 Unprocessable Entity`. Excluir um ambiente **não** apaga as plantas dele: elas ficam sem ambiente (`environment: null`).

### Listagem

Parâmetros de query (todos opcionais): `environment_id`, `species_id`, `active` (`true` ou `false`), `q`, `page` e `page_size`.

`q` busca por apelido, nome científico ou nome popular da espécie, sem diferenciar maiúsculas nem acentos (`falsa` acha qualquer planta da espécie `Ficus elastica`, cujo nome popular principal é `falsa-seringueira`, mesmo que ela tenha outro apelido). Ele pode ser combinado com os demais filtros.

A resposta traz o resumo da espécie e do ambiente, e `display_name`, que é o apelido ou, sem ele, o nome popular principal da espécie:

```json
{ "data": [ { "id": "…", "display_name": "Ficus da sala", "nickname": "Ficus da sala", "notes": null, "active": true, "species": { "id": "…", "scientific_name": "Ficus elastica", "common_name": "falsa-seringueira" }, "environment": { "id": "…", "name": "Sala" }, "created_at": "…", "updated_at": "…" } ], "page": 1, "page_size": 20, "total": 1 }
```

## Manutenções

As manutenções ficam em dois recursos separados:

- **agendamentos** (`maintenance-schedules`): o que está planejado e ainda não foi feito, com um prazo;
- **execuções** (`maintenance-logs`): o que foi de fato feito, com a data de execução, a partir de um agendamento ou sem planejamento.

Ao registrar a execução de um agendamento (`schedule_id` no POST de execução), **o agendamento é excluído**. A execução guarda `created_from_schedule: true` para indicar que nasceu de um agendamento. Assim, a lista de agendamentos contém só o que ainda falta fazer.

Separar o planejado do realizado permite registrar execuções não planejadas, remarcar ou cancelar agendamentos sem mexer no histórico e, no futuro, ter agendamentos recorrentes.

Todas as rotas exigem `Authorization: Bearer <access_token>` e só enxergam os registros do próprio usuário; os de outro usuário respondem `404`. Excluir uma planta apaga as manutenções dela.

### Formato das datas

`due_at` e `performed_at` usam o formato **`AAAA-MM-DD HH:mm:ss`** (ex.: `"2026-10-05 08:30:00"`), na entrada e na saída, assim como os filtros de intervalo da listagem. Como o formato não tem fuso, a API interpreta e formata os valores no fuso `APP_TIMEZONE` (padrão `America/Sao_Paulo`). Uma data fora do formato responde `400`. `created_at` e `updated_at` continuam em RFC 3339, como no resto da API.

### Tipos de manutenção

Catálogo **somente leitura**, carregado pela migration `000009_seed_maintenance_types`: Adubação, Mudança de Ambiente, Outro, Poda, Rega e Replante.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/v1/maintenance-types` | Lista os tipos, ordenados por nome (sem paginação) |

```json
{ "data": [ { "id": "…", "name": "Adubação" }, { "id": "…", "name": "Mudança de Ambiente" } ] }
```

### Agendamentos

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/maintenance-schedules` | Cria um agendamento (`201`) |
| GET | `/api/v1/maintenance-schedules` | Lista os agendamentos do usuário, ordenados pelo prazo |
| GET | `/api/v1/maintenance-schedules/{id}` | Retorna um agendamento |
| PUT | `/api/v1/maintenance-schedules/{id}` | Substitui planta, tipo, prazo e observações |
| DELETE | `/api/v1/maintenance-schedules/{id}` | Exclui (cancela) o agendamento (`204`) |

| Campo | Regras |
|---|---|
| `plant_id` | obrigatório; precisa ser uma planta do próprio usuário |
| `type_id` | obrigatório; precisa existir em `/maintenance-types` |
| `due_at` | obrigatório; prazo para execução (`AAAA-MM-DD HH:mm:ss`) |
| `notes` | opcional, até 2000 caracteres; em branco vira `null` |

A resposta traz `status`, calculado na hora da consulta: `overdue` se o prazo já venceu, senão `pending`.

Parâmetros de query da listagem (todos opcionais): `plant_id`, `type_id`, `status` (`pending` ou `overdue`), `due_from` e `due_to` (intervalo do prazo, inclusive), `page` e `page_size`.

```json
{ "data": [ { "id": "…", "plant": { "id": "…", "display_name": "Samambaia da vovó" }, "type": { "id": "…", "name": "Rega" }, "due_at": "2026-10-05 08:00:00", "notes": null, "status": "pending", "created_at": "…", "updated_at": "…" } ], "page": 1, "page_size": 20, "total": 1 }
```

### Execuções

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/maintenance-logs` | Registra uma execução (`201`); com `schedule_id`, exclui o agendamento |
| GET | `/api/v1/maintenance-logs` | Lista as execuções do usuário, da mais recente para a mais antiga |
| GET | `/api/v1/maintenance-logs/{id}` | Retorna uma execução |
| PUT | `/api/v1/maintenance-logs/{id}` | Substitui planta, tipo, data de execução e observações |
| DELETE | `/api/v1/maintenance-logs/{id}` | Exclui a execução (`204`) |

| Campo | Regras |
|---|---|
| `schedule_id` | opcional, só no POST; agendamento do próprio usuário que está sendo executado (é excluído) |
| `plant_id` | obrigatório sem `schedule_id`; com ele, pode ser omitido (é herdado do agendamento) ou precisa ser igual ao dele |
| `type_id` | mesma regra de `plant_id` |
| `performed_at` | obrigatório; data da execução (`AAAA-MM-DD HH:mm:ss`), não pode estar no futuro |
| `notes` | opcional, até 2000 caracteres; em branco vira `null`. Com `schedule_id` e sem `notes`, herda as observações do agendamento |

A resposta traz `created_from_schedule` (**criado por agendamento**), definido pela API: `true` quando a execução foi criada com `schedule_id`. Ele só é definido na criação: o PUT não aceita `schedule_id` e mantém o valor.

O registro da execução e a exclusão do agendamento acontecem numa única transação. Informar um agendamento que já foi executado (e, portanto, excluído) responde `422`.

Erros específicos (`422`): planta, tipo ou agendamento inexistentes (ou de outro usuário), planta/tipo diferentes dos do agendamento, ou `performed_at` no futuro.

Parâmetros de query da listagem (todos opcionais): `plant_id`, `type_id`, `performed_from` e `performed_to` (intervalo da execução, inclusive), `page` e `page_size`.

```json
{ "data": [ { "id": "…", "plant": { "id": "…", "display_name": "Samambaia da vovó" }, "type": { "id": "…", "name": "Rega" }, "created_from_schedule": true, "performed_at": "2026-10-05 08:30:00", "notes": null, "created_at": "…", "updated_at": "…" } ], "page": 1, "page_size": 20, "total": 1 }
```

### Manutenções de uma planta

`GET /api/v1/plants/{id}/maintenance-schedules` e `GET /api/v1/plants/{id}/maintenance-logs` aceitam os mesmos parâmetros das listagens acima (o `plant_id` é o da rota) e respondem `404` se a planta não existir ou for de outro usuário.

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

# Buscar espécies no catálogo
curl "http://localhost:8080/api/v1/species?q=esponjinha&category=flor"   -H "Authorization: Bearer <access_token>"

# Criar planta (espécie do catálogo + ambiente)
curl -X POST http://localhost:8080/api/v1/plants   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"species_id":"<species_id>","environment_id":"<environment_id>","nickname":"Ficus da sala"}'

# Criar planta só com apelido
curl -X POST http://localhost:8080/api/v1/plants   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"nickname":"Samambaia da vovó","notes":"Regar de manhã"}'

# Listar plantas de um ambiente
curl "http://localhost:8080/api/v1/plants?environment_id=<environment_id>&active=true"   -H "Authorization: Bearer <access_token>"

# Editar planta
curl -X PUT http://localhost:8080/api/v1/plants/<id>   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"species_id":"<species_id>","nickname":"Ficus da varanda","active":true}'

# Excluir planta
curl -X DELETE http://localhost:8080/api/v1/plants/<id>   -H "Authorization: Bearer <access_token>"

# Listar tipos de manutenção
curl http://localhost:8080/api/v1/maintenance-types   -H "Authorization: Bearer <access_token>"

# Agendar uma manutenção
curl -X POST http://localhost:8080/api/v1/maintenance-schedules   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"plant_id":"<plant_id>","type_id":"<type_id>","due_at":"2026-10-05 08:00:00","notes":"Meio litro de água"}'

# Listar agendamentos atrasados
curl "http://localhost:8080/api/v1/maintenance-schedules?status=overdue"   -H "Authorization: Bearer <access_token>"

# Editar agendamento
curl -X PUT http://localhost:8080/api/v1/maintenance-schedules/<id>   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"plant_id":"<plant_id>","type_id":"<type_id>","due_at":"2026-10-06 08:00:00"}'

# Excluir agendamento
curl -X DELETE http://localhost:8080/api/v1/maintenance-schedules/<id>   -H "Authorization: Bearer <access_token>"

# Executar um agendamento (planta e tipo herdados dele; o agendamento é excluído)
curl -X POST http://localhost:8080/api/v1/maintenance-logs   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"schedule_id":"<schedule_id>","performed_at":"2026-10-05 08:30:00"}'

# Registrar uma execução sem agendamento
curl -X POST http://localhost:8080/api/v1/maintenance-logs   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"plant_id":"<plant_id>","type_id":"<type_id>","performed_at":"2026-10-01 18:00:00","notes":"Poda das folhas secas"}'

# Listar execuções de outubro
curl "http://localhost:8080/api/v1/maintenance-logs?performed_from=2026-10-01%2000:00:00&performed_to=2026-10-31%2023:59:59"   -H "Authorization: Bearer <access_token>"

# Editar execução
curl -X PUT http://localhost:8080/api/v1/maintenance-logs/<id>   -H "Authorization: Bearer <access_token>"   -H "Content-Type: application/json"   -d '{"plant_id":"<plant_id>","type_id":"<type_id>","performed_at":"2026-10-05 09:00:00","notes":"Atrasou um pouco"}'

# Excluir execução
curl -X DELETE http://localhost:8080/api/v1/maintenance-logs/<id>   -H "Authorization: Bearer <access_token>"

# Manutenções de uma planta
curl http://localhost:8080/api/v1/plants/<id>/maintenance-schedules   -H "Authorization: Bearer <access_token>"
curl http://localhost:8080/api/v1/plants/<id>/maintenance-logs   -H "Authorization: Bearer <access_token>"
```
