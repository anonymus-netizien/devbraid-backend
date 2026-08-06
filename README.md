# DevBraid Backend

Spring Boot 4.1.0 backend for the DevBraid platform — capturing **why** code changes happen along a five-step flow:

**Connect → Capture → Analyze → Reason → Publish**

A developer connects a GitHub repo (PAT), creates a Change Thread to capture commits/diffs + decision notes, runs
deterministic risk analysis, generates an AI change brief, and publishes it to a GitHub PR after human approval.

## Prerequisites

- **Java 21**
- **Maven 3.8+**
- **Docker & Docker Compose**
- **Git**

## Quick Start

```bash
# Clone and configure
git clone https://github.com/anonymus-netizien/devbraid-backend.git
cd devbraid-backend
cp .env.example .env   # Edit with your credentials

# Start infrastructure + app
docker compose up -d --build

# Or run locally (the ./mvnw wrapper is broken — use system Maven)
docker compose up -d postgres redis
mvn spring-boot:run
```

**Backend:** `http://localhost:8080` · **pgAdmin:** `http://localhost:5050`

## Tech Stack

| Component  | Technology                                |
|------------|-------------------------------------------|
| Runtime    | Java 21                                   |
| Framework  | Spring Boot 4.1.0                         |
| ORM        | Spring Data JPA + Hibernate               |
| Database   | PostgreSQL 18 (Flyway migrations)         |
| Cache      | Redis 7 (OTP, rate limiting)              |
| Auth       | JWT (HMAC256) + OTP (email-based)         |
| AI         | OpenAI / Groq / OpenRouter (optional, graceful degradation) |
| Encryption | AES-256-GCM for GitHub PATs               |
| Testing    | JUnit 5 + Mockito + WireMock (246 tests)  |

## Modules

```
com.devbraid
├── user/          Auth (register, OTP, login, JWT, refresh tokens)
├── github/        GitHub PAT connection, repo/branch listing
├── changethread/  Change Threads + Decision Notes CRUD
├── brief/         Change Brief generation + GitHub PR publishing
├── analysis/      Deterministic risk analysis + AI enhancement
├── ai/            AI provider abstraction (OpenAI)
├── security/      JWT filter, SecurityConfig, CORS
├── config/        Redis, Jackson, scheduling, REST client
└── common/        ApiResponse envelope, GlobalExceptionHandler, LoggingFilter
```

## API Endpoints

| Module  | Base Path                                     | Methods                                                           |
|---------|-----------------------------------------------|-------------------------------------------------------------------|
| Auth    | `/api/v1/auth`                                | register, login, logout, refresh, otp/*                           |
| User    | `/api/v1/user`                                | profile (GET/PUT), password                                       |
| GitHub  | `/api/v1/github`                              | connect, disconnect, status, repos, repos/{owner}/{repo}/branches |
| Threads | `/api/v1/threads`                             | CRUD + refresh, analyze, brief, publish                           |
| Notes   | `/api/v1/threads/{id}/notes`, `/api/v1/notes` | CRUD (thread-scoped + global list)                                |
| Briefs  | `/api/v1/briefs`                              | list, get by ID                                                   |

See [`API.md`](API.md) for full API documentation with request/response examples, and run the app locally to browse
the live OpenAPI spec at `http://localhost:8080/swagger-ui.html`.

## Database

6 Flyway migrations (V1–V6):

| Table                | Purpose                                                  |
|----------------------|----------------------------------------------------------|
| `users`              | User accounts (UUID v7 PK, bcrypt password)              |
| `refresh_tokens`     | JWT refresh tokens (hash, revoked flag)                  |
| `github_connections` | Encrypted PATs (AES-256-GCM, per-connection IV)          |
| `change_threads`     | Workspaces with JSONB commits/diffs/risk reports         |
| `decision_notes`     | Why-decisions (context, rationale, alternatives, impact) |
| `change_briefs`      | AI-generated markdown briefs (1:1 with threads)          |

## Configuration

| Profile | DDL      | CORS                     | JWT         | Redis               | DB Pool |
|---------|----------|--------------------------|-------------|---------------------|---------|
| `dev`   | update   | localhost:3000,5173,4200 | dev default | localhost defaults  | 5       |
| `stage` | validate | staging.devbraid.com     | env required| env-driven (defaults) | 15    |
| `prod`  | validate | devbraid.com             | env required| env required (fail-fast) | 25 |

Environment notes:

- The DB and Redis credentials come from env vars (`DEV_*`, `STAGE_*`, `PROD_*`) — see `.env.example`. `prod` has **no defaults** for `PROD_DB_URL`/`PROD_REDIS_HOST`/`PROD_JWT_SECRET`: the app fails fast at startup if they are unset.
- No Spring `context-path` is set in any profile — controllers map `/api/v1/...` themselves, so the API is at `/api/v1` in all environments (the old `/api` context-path produced `/api/api/v1` in prod).
- PostgreSQL 18 is required because the migrations use the built-in `uuidv7()` default. Both prod and stage run `ddl-auto: validate`, so the schema must match V1–V6 exactly.

## Testing

```bash
mvn test                           # 246 tests (all green)
mvn test -Dtest=ChangeThreadServiceTest  # Single class
```

## Docker

```bash
docker compose up -d --build    # Build and start all
docker compose ps               # Check status
docker compose logs -f app      # Stream app logs
docker compose down -v          # Stop + remove data
```

## Security

- BCrypt password hashing
- JWT with 30-min access / 7-day refresh tokens
- Refresh token rotation (old deleted on use)
- AES-256-GCM PAT encryption with per-record IVs
- OTP rate limiting (3/min via Redis)
- Login/register rate limiting (Redis: per-user + per-IP, 429 on exceed)
- Sensitive header redaction in logs
- Ownership checks (`findByIdAndUserId`) on all resources
- Unauthenticated requests get a JSON `401` (not the Spring default 403)

## Project Docs

- [`API.md`](API.md) — Full API documentation (endpoints, auth flow, errors)
- `.env.example` — All environment variables documented per profile
