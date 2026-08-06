# DevBraid Backend

Spring Boot 4.1.0 backend for the DevBraid platform — capturing **why** code changes happen along a
five-step flow:

**Connect → Capture → Analyze → Reason → Publish**

A developer connects a GitHub repo (PAT), creates a Change Thread to capture commits/diffs + decision
notes, runs deterministic risk analysis, generates an AI change brief, and publishes it to a GitHub PR
after human approval. Identity and auth are handled by **Clerk**; data lives in **Neon Postgres**; the API
deploys to **Render**.

## Tech Stack

| Component    | Technology                                       |
|--------------|--------------------------------------------------|
| Runtime      | Java 21 + Spring Boot 4.1.0                    |
| Identity     | [Clerk](https://clerk.com) (email OTP auth, JWKS JWTs) |
| Database     | Neon Postgres (free serverless PG, Flyway V1–V7) |
| AI           | OpenAI / Groq / OpenRouter (optional, graceful degradation) |
| Encryption   | AES-256-GCM for GitHub PATs                    |
| Testing      | JUnit 5 + Mockito + WireMock (211 tests)       |
| CI/CD        | GitHub Actions → Render (webhook deploy)       |
| Hosting      | Render (web service), Neon (DB), Clerk (auth)  |

## Quick Start

```bash
# Prereqs: Java 21, Docker & Docker Compose (mvnw wrapper is broken — use system Maven)

git clone https://github.com/anonymus-netizien/devbraid-backend.git
cd devbraid-backend
cp .env.example .env   # Set CLERK_JWKS_URL + CLERK_ISSUER (see .env.example)
docker compose up -d --build
```

**Backend:** `http://localhost:8080` · **Swagger UI:** `http://localhost:8080/swagger-ui.html` ·
**pgAdmin:** `http://localhost:5050`

> No Clerk account? Create a free instance at dashboard.clerk.com and point `CLERK_JWKS_URL` at its
> JWKS endpoint. Identity, signup and email OTP all live in Clerk — the backend just verifies tokens.

## Modules

```
com.devbraid
├── user/          Clerk user mirror, profile
├── github/        GitHub PAT connection, repo/branch listing, PR comments
├── changethread/  Change Threads + Decision Notes CRUD
├── brief/         Change Brief generation + publishing
├── analysis/      Deterministic risk analysis + AI enhancement
├── ai/            AI provider abstraction (OpenAI/Groq/OpenRouter)
├── security/      ClerkJwtFilter (JWKS verification), SecurityConfig, CORS
├── config/        Jackson, scheduling, REST client
└── common/        ApiResponse envelope, GlobalExceptionHandler, LoggingFilter
```

## API

Full API documentation with request/response examples, auth details and entity schema lives in
**[`API.md`](API.md)**. Run the app locally to browse the live OpenAPI spec at
`http://localhost:8080/swagger-ui.html` (dev/stage only).

### Endpoints at a glance

| Module  | Base Path                                 | Actions                                                     |
| ------- | ------------------------------------------ | ----------------------------------------------------------- |
| User    | `/api/v1/user`                             | profile (GET/PUT)                                            |
| GitHub  | `/api/v1/github`                           | connect, disconnect, status, repos, branches               |
| Threads | `/api/v1/threads`                          | CRUD + refresh, analyze, brief (GET/POST), publish          |
| Notes   | `/api/v1/threads/{id}/notes`, `/api/v1/notes` | CRUD (thread-scoped + global list)                    |
| Briefs  | `/api/v1/briefs`                          | list, get by ID                                              |

Every endpoint requires `Authorization: Bearer <clerk-session-token>`.

## Configuration

| Profile         | DDL      | CORS                     | DB                    | Clerk | Encryption            |
| --------------- | -------- | ------------------------ | --------------------- | ----- | --------------------- |
| `dev`  (default) | update  | `localhost:3000,5173,4200` | local Docker (5433)  | env   | dev default (insecure) |
| `stage`        | validate | `staging.devbraid.com`   | Neon (env, required)  | env   | `STAGE_ENCRYPTION_KEY` |
| `prod`         | validate | `app.devbraid.com`       | Neon (env, required)  | env   | `PROD_ENCRYPTION_KEY` |

- **prod fails fast** without `PROD_DB_URL`, `PROD_DB_USERNAME`, `PROD_DB_PASSWORD`,
  `PROD_ENCRYPTION_KEY`, `CLERK_JWKS_URL`.
- `server.port` defaults to `${PORT:8080}` so Render can inject its own port.
- No Spring `context-path` — controllers own `/api/v1/...` in every environment.
- All secrets: `.env` (dev) or Render dashboard (stage/prod). See `.env.example`.

## Deployment (Render)

1. Push to `main`.
2. GitHub Actions (`ci.yml`) runs tests; `deploy.yml` triggers the Render Deploy Hook on success.
3. Render builds the `Dockerfile`, starts with `SPRING_PROFILES_ACTIVE=prod`.
4. Health check: `GET /actuator/health`.

## Testing

```bash
mvn test                              # 211 tests (all green)
mvn test -Dtest=ClerkJwtVerifierTest # single class
```

## Docker

```bash
docker compose up -d --build   # build and start all
docker compose down -v          # stop + remove data
```

## Security

- Clerk-issued JWTs verified against Clerk JWKS (RS256) — no passwords stored server-side.
- JSON `401` on unauthenticated requests (not Spring's default 403).
- AES-256-GCM PAT encryption with per-record IVs.
- Ownership checks (`findByIdAndUserId`) on all resources.
- Sensitive header redaction in logs.

## Project Docs

- [`API.md`](API.md) — Full API documentation (endpoints, Clerk auth, entities, deployment)
- `.env.example` — All environment variables, per profile