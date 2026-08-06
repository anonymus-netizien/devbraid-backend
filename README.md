# DevBraid Backend

Spring Boot 4.1.0 backend for the DevBraid platform — capturing **why** code changes happen.

## Prerequisites

- **Java 21**
- **Maven 3.8+** (system Maven — the `./mvnw` wrapper is currently broken, see Testing)
- **Docker & Docker Compose** (required — infra stack AND the Testcontainers test suite)
- **Git**

## Quick Start

```bash
# Clone and configure
git clone https://github.com/anonymus-netizien/devbraid-backend.git
cd devbraid-backend
cp .env.example .env   # Edit with your credentials

# Start infrastructure + app
docker compose up -d --build

# Or run locally
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
| Database   | PostgreSQL 18 (Flyway migrations V1–V20)  |
| Cache      | Redis 7 (OTP, rate limiting, GitHub App tokens) |
| Auth       | JWT (HMAC256) + OTP (email-based)         |
| AI         | OpenAI (optional, graceful degradation)   |
| Encryption | AES-256-GCM for GitHub PATs               |
| Testing    | JUnit 5 + Mockito + WireMock + Testcontainers (~400 tests) |

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
| Auth    | `/api/v1/auth`                                | register, login, logout, refresh, me, profile, password, otp/*    |
| GitHub  | `/api/v1/github`                              | connect, disconnect, status, repos, repos/{owner}/{repo}/branches |
| Threads | `/api/v1/threads`                             | CRUD + refresh, analyze, brief, publish                           |
| Notes   | `/api/v1/threads/{id}/notes`, `/api/v1/notes` | CRUD (thread-scoped + global list)                                |
| Briefs  | `/api/v1/briefs`                              | list, get by ID                                                   |

See `docs/PROJECT_DOCUMENTATION.md` for full API documentation with request/response examples.

## Database

20 Flyway migrations (V1–V9, V12–V20; V10 & V11 skipped):

| Table                | Purpose                                                  |
|----------------------|----------------------------------------------------------|
| `users`              | User accounts (UUID v7 PK, bcrypt password)              |
| `refresh_tokens`     | JWT refresh tokens (hash, revoked flag)                  |
| `github_connections` | Encrypted PATs (AES-256-GCM, per-connection IV)          |
| `change_threads`     | Workspaces with JSONB commits/diffs/risk reports         |
| `decision_notes`     | Why-decisions (context, rationale, alternatives, impact) |
| `change_briefs`      | AI-generated markdown briefs (1:1 with threads)          |

> V7–V9 and V12–V20 add thread snapshots/file comments/events, audit logs, indexing, GitHub App tables + webhook jobs (V19) + OAuth identities (V20), PR reviews, API keys — see `memory.md` §4.2 for the full schema.

## Configuration

| Profile | DDL      | CORS                     | JWT         | DB Pool |
|---------|----------|--------------------------|-------------|---------|
| `dev`   | update   | localhost:3000,5173,4200 | dev default | 5       |
| `stage` | validate | staging.devbraid.com     | required    | 15      |
| `prod`  | validate | app.devbraid.com         | required    | 25      |

## Testing

```bash
mvn test                                   # full suite (~400 tests) — requires Docker (Testcontainers)
mvn test -Dtest=WebhookJobDurableStateIntegrationTest   # single class
```

> Note: use system Maven — the `./mvnw` wrapper is broken (missing `.mvn/wrapper/maven-wrapper.properties`, restore is P2).
> Since 2026-08-05 the suite includes PG-backed Testcontainers tests (`WebhookJobDurableStateIntegrationTest`, `postgres:18-alpine` + `redis:7-alpine`) — a running Docker daemon is required.

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
- Sensitive header redaction in logs
- Ownership checks (`findByIdAndUserId`) on all resources

## Project Docs

- `docs/PROJECT_DOCUMENTATION.md` — Complete consolidated documentation
- `docs/reports/2026-07-29-e2e-test-report.md` — E2E test report
- `.env.example` — All environment variables documented
