# DevBraid Backend — API Documentation

> **Version:** v2.0 (five-step flow, Clerk auth)
> **Base URL (dev):** `http://localhost:8080`
> **Framework:** Spring Boot 4.1.0 / Java 21
> **Last Updated:** 2026-08-06
>
> **v2.0 (2026-08-06):** Authentication moved to **Clerk** (identity, signup, email OTP, JWT issuance
> handled externally). The backend verifies Clerk session tokens against Clerk's JWKS and mirrors users
> locally. The old self-hosted auth (register/login/OTP/refresh tokens/Redis rate limiting) is **gone**.
> Database is **Neon Postgres**; hosting is **Render**.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Request/Response Envelope](#2-requestresponse-envelope)
3. [Authentication (Clerk)](#3-authentication-clerk)
4. [Endpoints](#4-endpoints)
5. [Entities & Database Schema](#5-entities--database-schema)
6. [DTO Reference](#6-dto-reference)
7. [Exception Handling](#7-exception-handling)
8. [Security Configuration](#8-security-configuration)
9. [Environment Profiles & Deployment](#9-environment-profiles--deployment)
10. [Docker Setup](#10-docker-setup)

---

## 1. Architecture Overview

```
┌──────────────┐   ┌────────────────────────┐   ┌───────────────────────────┐
│    Client    │──▶│        Clerk           │   │     Spring Boot Backend    │
│  (Frontend)  │   │  signup / sign-in      │   │  (Render free web service) │
│              │   │  email OTP, JWTs       │   │                            │
└──────────────┘   └────────────────────────┘   │  ClerkJwtFilter ──▶ Security│
        │                                       │         │                   │
        │  Authorization: Bearer <Clerk JWT>    │         │                   │
        └──────────────────────────────────────▶│  Controllers ──▶ Services   │
                                                │         │                   │
                                                │         ▼                   │
                                                │    PostgreSQL (Neon, free)  │
                                                └─────────────────────────────┘
```

Request flow:

```
Client → LoggingFilter → ClerkJwtFilter → SecurityConfig
      → Controller → Service → Repository → Neon Postgres
      → ApiResponse → Client
```

### Filter Chain Order

| Order | Filter                    | Responsibility                                                        |
|-------|---------------------------|-----------------------------------------------------------------------|
| 1     | `LoggingFilter`           | Log method, URI, headers (redacted), host, query params, timestamp    |
| 2     | `ClerkJwtFilter`          | Verify Bearer JWT against Clerk JWKS, mirror user, set SecurityContext |
| 3     | `SecurityConfig` chain    | CSRF (disabled), CORS, stateless sessions, authorization rules        |

### Outbound Services

| Service     | Used for                                                          |
|-------------|-------------------------------------------------------------------|
| **Clerk**   | JWT verification via `GET {CLERK_JWKS_URL}` (RS256 keys, cached 1h) |
| **GitHub**  | PAT validation, repo/branch listing, diff fetch, PR comments      |
| **AI**      | OpenAI / Groq / OpenRouter (optional, graceful degradation)       |

---

## 2. Request/Response Envelope

All responses use `ApiResponse<T>`:

### Success

```json
{
  "success": true,
  "message": "Human-readable message",
  "data": { }
}
```

### Error

```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

The HTTP status lives in the response headers — it is not duplicated in the body.

### Validation Error

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "email": "Email is required"
  }
}
```

### HTTP Status Summary

| Status                      | Usage                                                                                       |
|-----------------------------|---------------------------------------------------------------------------------------------|
| `200 OK`                    | Successful operations                                                                       |
| `201 CREATED`               | Resource creation (thread, note)                                                            |
| `400 BAD_REQUEST`           | Validation errors, malformed body, missing query parameter, illegal arguments               |
| `401 UNAUTHORIZED`          | Missing/invalid/expired Clerk token; invalid GitHub token                                   |
| `403 FORBIDDEN`             | GitHub PAT lacks the required scope for the operation                                       |
| `404 NOT_FOUND`             | Resource not found or not owned; GitHub resource not found                                  |
| `409 CONFLICT`              | GitHub already connected; data integrity conflicts                                          |
| `429 TOO_MANY_REQUESTS`     | GitHub API rate limit exceeded                                                              |
| `500 INTERNAL_SERVER_ERROR` | Unhandled runtime exceptions                                                                |

> **v2.0:** the auth-specific statuses are gone — `401` for login failure, `410 GONE` for expired OTP,
> `429` for auth rate limits no longer apply. Clerk owns those flows and returns its own errors to the
> frontend before the API is ever called.

---

## 3. Authentication (Clerk)

### 3.1 Flow

```
┌───────────────────┐       ┌──────────────────┐        ┌───────────────────┐
│     Frontend      │       │      Clerk       │        │ Spring Boot API    │
└─────────┬─────────┘       └────────┬─────────┘        └─────────┬─────────┘
          │  sign-in (email OTP)     │                             │
          ├──────────────────────────▶│                             │
          │  session token (JWT)     │                             │
          │◀──────────────────────────┤                             │
          │                           │                             │
          │  GET /api/v1/user/profile │                             │
          │  Authorization: Bearer ▸──┼────────────────────────────▶│
          │                           │  1. Fetch JWKS (cached 1h) │
          │                           │  2. Verify RS256 signature │
          │                           │  3. Upsert local user by   │
          │                           │     clerk_id (first call)  │
          │◀──────────────────────────┼─────────────────────────────│
```

### 3.2 Setup (one-time, per environment)

1. Create a free app at [dashboard.clerk.com](https://dashboard.clerk.com).
2. Set two backend env vars:
   - `CLERK_JWKS_URL` = `https://<your-instance>.clerk.accounts/.well-known/jwks.json`
   - `CLERK_ISSUER` = `https://<your-instance>.clerk.accounts`
3. **Session token customization** (Clerk dashboard → Sessions → Customize session token): add the
   claims `email` and `fullName` (or `first_name` + `last_name`) so the backend can mirror your profile.
   The backend requires `email`; without it the request is rejected with `401`.

### 3.3 Token claims used by the backend

| Claim         | Required | Purpose                                |
|---------------|----------|----------------------------------------|
| `sub`         | yes      | Clerk user id — becomes `users.clerk_id` |
| `email`       | yes      | Local user email                       |
| `fullName`    | no       | Display name (falls back to `first_name`/`last_name`) |
| `first_name`  | no       | Display name fallback                  |
| `last_name`   | no       | Display name fallback                  |

### 3.4 User mirroring

The `users` table is **not** the identity store — Clerk is. The backend upserts a local row keyed by
`clerk_id` on the first authenticated request. Deleting the Clerk user does not cascade to local data;
threads/notes/briefs belong to the local user id.

---

## 4. Endpoints

All endpoints except the OpenAPI/Swagger UI paths require:

```
Authorization: Bearer <clerk-session-token>
```

### 4.1 User Profile

**GET /api/v1/user/profile** — returns the authenticated user's profile.

**Success Response:** `200 OK`

```json
{
  "success": true,
  "message": "User profile retrieved",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "fullName": "John Doe",
    "email": "john@example.com",
    "role": "DEVELOPER",
    "createdAt": "2026-07-24T10:00:00Z"
  }
}
```

**PUT /api/v1/user/profile** — updates the profile. Body: `UpdateProfileRequest` — `{ "fullName": "Jane Roe" }`.
Null/blank fields are left unchanged.

**Error Responses:** `401` missing/invalid token · `400` validation failure

---

### 4.2 GitHub Connection

**POST /api/v1/github/connect** — connects a GitHub Personal Access Token.

```json
{
  "personalAccessToken": "github_pat_..."
}
```

The token is validated against the GitHub API and stored encrypted (AES-256-GCM) in the DB.

- `200 OK` — `{ "connected": true, "valid": true, "githubUsername": "octocat", "connectedAt": "..." }`
- `409 CONFLICT` — already connected (use `disconnect` first)
- `401 UNAUTHORIZED` — token invalid/expired on GitHub's side

**DELETE /api/v1/github/disconnect** — removes the stored connection. `200 OK`.

**GET /api/v1/github/status** — `{ "connected": true|false, "githubUsername": "..." }`.

**GET /api/v1/github/repos** — lists the user's repositories. Optional query overload:
`?owner=<owner>&repo=<name>` returns just that repo.

**GET /api/v1/github/branches?owner=<owner>&repo=<name>** — lists branches for a repository.

**GET /api/v1/github/repos/{owner}/{repo}/branches** — path overload of the above.

---

### 4.3 Change Threads

A thread captures a GitHub diff (`baseBranch...headBranch`) for one repository.

**POST /api/v1/threads** — creates a thread by fetching the diff from GitHub using the connected PAT.

```json
{
  "repositoryFullName": "owner/repo",
  "headBranch": "feature-branch",
  "baseBranch": "main",
  "title": "Refactor auth",
  "description": "Optional"
}
```

- `201 Created` — thread with `commits[]` and `changedFiles[]` (with patches)
- `400 BAD_REQUEST` — repo format not `owner/repo`
- `404 NOT_FOUND` — repo/branches not found on GitHub; GitHub not connected
- `401 UNAUTHORIZED` — GitHub token invalid/expired

**GET /api/v1/threads** — paginated list of the user's threads (notes batched to avoid N+1).

**GET /api/v1/threads/{id}** — single thread with its notes.

**PUT /api/v1/threads/{id}** — update title/description/status. `404` if not owned.

**DELETE /api/v1/threads/{id}** — deletes the thread. `404` if not owned.

**POST /api/v1/threads/{id}/refresh** — re-fetches the diff from GitHub and updates commits/files.

**POST /api/v1/threads/{id}/analyze** — runs risk analysis: deterministic rule flags, commit-message
analysis, test-coverage gap detection, auto-suggested decision notes, and (when an AI key is configured)
an AI summary. Without AI the report still returns with `"aiAnalyzed": false`.

**GET /api/v1/threads/{id}/brief** — returns the latest generated brief for the thread.

**POST /api/v1/threads/{id}/brief** — generates a Markdown change brief (AI with citation markers, or the
template fallback when no AI key is configured).

**POST /api/v1/threads/{id}/publish?prNumber=<n>** — posts the thread's brief as a comment on pull
request `#n` of the thread's repository. `prNumber` is required.

- `200 OK` — `PublishResponse` with the comment URL
- `400 BAD_REQUEST` — `prNumber` missing
- `403 FORBIDDEN` — the connected PAT lacks Issues/PR-comment write scope
- `404 NOT_FOUND` — PR not found
- `401 UNAUTHORIZED` — GitHub token invalid/expired

---

### 4.4 Decision Notes

A note captures a decision ("why") anchored to a `COMMIT`, `FILE` or `THREAD`.

**POST /api/v1/threads/{threadId}/notes** — creates a note.

```json
{
  "context": "COMMIT",
  "contextRef": "a1b2c3d4",
  "decision": "Keep PAT-only connect flow",
  "rationale": "Slims the surface to five steps",
  "alternatives": "OAuth app install",
  "impact": "Lower maintenance"
}
```

`context` is required; `contextRef` is required for `COMMIT` (sha) and `FILE` (path). Returns `201 Created`.

**GET /api/v1/threads/{threadId}/notes** — all notes for a thread (newest first).

**GET /api/v1/notes** — paginated list of the user's notes across all threads.

**PUT /api/v1/threads/{threadId}/notes/{noteId}** — updates non-null fields. `404` if not owned.

**DELETE /api/v1/threads/{threadId}/notes/{noteId}** — deletes the note. `404` if not owned.

---

### 4.5 Change Briefs

**GET /api/v1/briefs** — paginated list of the user's generated briefs.

**GET /api/v1/briefs/{id}** — a brief by ID, including the full Markdown content.

---

### 4.6 OpenAPI / Swagger (dev & stage only)

| Endpoint                | Purpose                                   |
|-------------------------|-------------------------------------------|
| `GET /v3/api-docs`      | OpenAPI 3.1 JSON spec (disabled in prod)  |
| `GET /swagger-ui.html`  | Interactive Swagger UI (disabled in prod) |

---

## 5. Entities & Database Schema

6 Flyway migrations (V1–V7). All PKs default to `uuidv7()` (PostgreSQL 18 built-in — Neon supports PG 18).

### 5.1 ERD

```
┌──────────────────────┐       ┌──────────────────────────┐
│        users         │       │     github_connections   │
├──────────────────────┤       ├──────────────────────────┤
│ id (PK, UUID)        │◀──────│ id (PK, UUID)            │
│ email (UNIQUE)       │  1:1  │ user_id (FK → users.id)  │
│ clerk_id (UNIQUE)    │       │ encrypted_pat (BYTEA)    │
│ full_name            │       │ iv (BYTEA)               │
│ created_at           │       │ github_username          │
└──────────────────────┘       └──────────────────────────┘
        │ 1:N
        ▼
┌───────────────────────────────┐
│      change_threads           │
├───────────────────────────────┤
│ id (PK, UUID)                 │
│ user_id (FK → users.id)       │
│ repository_full_name          │
│ head_branch / base_branch     │
│ status / source               │
│ commits / changed_files (JSONB)│
│ risk_level / risk_report      │
└──────────┬────────────────────┘
           │ 1:N
   ┌───────┴────────┐        ┌───────────────────────┐
   ▼                │        │     change_briefs     │
decision_notes      │        ├───────────────────────┤
(id, thread_id,     │        │ id (PK)               │
 author_id,         │        │ thread_id (FK, UNIQUE)│
 context,           │        │ content (TEXT)        │
 context_ref,       │        │ published_at,         │
 decision,          │        │ publish_url           │
 rationale, ...)    │        └───────────────────────┘
                    │
                    └────────▶ change_threads.id (brief 1:1)
```

### 5.2 Migration history

| Migration | Purpose                                                        |
|-----------|----------------------------------------------------------------|
| V1        | `users` (email, full_name, password_hash — dropped in V7)      |
| V2        | `refresh_tokens` (created, dropped in V7)                      |
| V3        | `github_connections` (encrypted PAT, IV)                       |
| V4        | `change_threads` (commits/diffs JSONB)                         |
| V5        | `decision_notes`                                               |
| V6        | `change_briefs`                                                |
| V7        | **Clerk switch**: add `clerk_id` (unique, NOT NULL), drop `password_hash`, drop `refresh_tokens` |

> **v2.0 schema changes:** `users` no longer stores passwords; identity is `clerk_id`. `ddl-auto: validate`
> in stage/prod confirms the schema matches the entities exactly.

---

## 6. DTO Reference

### 6.1 Request DTOs

#### `UpdateProfileRequest`

| Field      | Type   | Validation               | Description            |
|------------|--------|--------------------------|------------------------|
| `fullName` | String | Optional, max 100 chars  | New display name       |

#### `CreateThreadRequest`

| Field                | Type   | Validation                 | Description               |
|----------------------|--------|----------------------------|---------------------------|
| `repositoryFullName` | String | Required, `owner/repo`     | GitHub repository         |
| `headBranch`         | String | Required                   | Feature branch            |
| `baseBranch`         | String | Optional (default `main`)  | Base branch               |
| `title`              | String | Required                   | Thread title              |
| `description`        | String | Optional                   | Thread description        |

#### `CreateNoteRequest`

| Field         | Type   | Validation                        | Description                     |
|---------------|--------|-----------------------------------|---------------------------------|
| `context`     | String | Required: `COMMIT`/`FILE`/`THREAD`| Anchor type                     |
| `contextRef`  | String | Required for `COMMIT`/`FILE`      | Commit sha or file path         |
| `decision`    | String | Required                          | The decision                    |
| `rationale`   | String | Required                          | Why the decision was made       |
| `alternatives`| String | Optional                          | Alternatives considered         |
| `impact`      | String | Optional                          | Expected impact                 |

### 6.2 Response DTOs

#### `UserProfileResponse`

| Field       | Type           | Description                              |
|-------------|----------------|------------------------------------------|
| `id`        | UUID           | Local user id                            |
| `fullName`  | String         | Display name                             |
| `email`     | String         | Email (mirrored from Clerk)              |
| `role`      | String         | Always `DEVELOPER`                       |
| `createdAt` | OffsetDateTime | Local row creation time                  |

#### `LoginResponse` — **removed in v2.0**

No login endpoint exists; tokens come from Clerk.

---

## 7. Exception Handling

All handled by `GlobalExceptionHandler` (`@RestControllerAdvice`).

| Exception                         | HTTP Status             | Log Level |
|-----------------------------------|-------------------------|-----------|
| `UserNotFoundException`           | `404 NOT_FOUND`         | WARN      |
| `GitHubAlreadyConnectedException` | `409 CONFLICT`          | WARN      |
| `GitHubNotConnectedException`     | `404 NOT_FOUND`         | WARN      |
| `GitHubTokenInvalidException`     | `401 UNAUTHORIZED`      | WARN      |
| `GitHubTokenExpiredException`     | `401 UNAUTHORIZED`      | WARN      |
| `GitHubNotFoundException`         | `404 NOT_FOUND`         | WARN      |
| `GitHubForbiddenException`        | `403 FORBIDDEN`         | WARN      |
| `GitHubRateLimitException`        | `429 TOO_MANY_REQUESTS` | WARN      |
| `ThreadNotFoundException`         | `404 NOT_FOUND`         | WARN      |
| `NoteNotFoundException`           | `404 NOT_FOUND`         | WARN      |
| `BriefNotFoundException`          | `404 NOT_FOUND`         | WARN      |
| `AccessDeniedException`           | `403 FORBIDDEN`         | WARN      |
| `IllegalArgumentException`        | `400 BAD_REQUEST`       | WARN      |
| `MethodArgumentNotValidException` | `400 BAD_REQUEST`       | WARN      |
| `MissingServletRequestParameterException` | `400 BAD_REQUEST` | WARN |
| `MethodArgumentTypeMismatchException` | `400 BAD_REQUEST`    | WARN      |
| `HttpMessageNotReadableException` | `400 BAD_REQUEST`       | WARN      |
| `NoResourceFoundException`        | `404 NOT_FOUND`         | WARN      |
| `DataIntegrityViolationException` | `409 CONFLICT`          | WARN      |
| `RuntimeException` / `Exception`  | `500 INTERNAL_SERVER_ERROR` | ERROR  |

---

## 8. Security Configuration

```
HttpSecurity:
├── CSRF: DISABLED (stateless JWT auth, no cookies)
├── CORS: enabled (origins per profile)
├── Session: STATELESS
├── Authorization:
│   ├── PERMIT ALL: /v3/api-docs/**, /swagger-ui/**, /swagger-ui.html
│   └── AUTHENTICATED: everything else
└── ClerkJwtFilter BEFORE UsernamePasswordAuthenticationFilter
```

- Unauthenticated requests → JSON `401` `{"success":false,"message":"Unauthenticated","data":null}`
  (not Spring's default 403).
- Clerk tokens are RS256 JWTs verified against the JWKS URL with 1-hour key caching.
- GitHub PATs encrypted with AES-256-GCM, per-connection random IV (`ENCRYPTION_KEY` env var).
- Ownership checks (`findByIdAndUserId`) on all thread/note/brief lookups.
- Sensitive request headers redacted in logs.

---

## 9. Environment Profiles & Deployment

### 9.1 Profiles

| Profile         | File                     | Purpose                                   |
|-----------------|--------------------------|-------------------------------------------|
| `dev` (default) | `application-dev.yaml`   | Local development (Docker Postgres)       |
| `stage`         | `application-stage.yaml` | Pre-production testing                    |
| `prod`          | `application-prod.yaml`  | Production (Render + Neon)                |

### 9.2 Key differences

| Setting         | dev                       | stage                     | prod                      |
|-----------------|---------------------------|---------------------------|---------------------------|
| JPA DDL         | `update`                  | `validate`                | `validate`                |
| SQL Logging     | `DEBUG`                   | `WARN`                    | `WARN`                    |
| Pool Size       | 5                         | 10                        | 10                        |
| Database        | local Docker (5433)       | Neon (env, required)      | Neon (env, required)      |
| CORS            | `localhost:3000,5173,4200`| `staging.devbraid.com`    | `app.devbraid.com`        |
| Clerk JWKS      | env (`CLERK_JWKS_URL`)    | env (required)            | env (required)            |
| Encryption key  | dev default (insecure)    | `STAGE_ENCRYPTION_KEY` (required) | `PROD_ENCRYPTION_KEY` (required) |
| Swagger UI      | Enabled                   | Enabled                   | Disabled                  |
| Server port     | `DEV_SERVER_PORT:8080`    | `PORT`                    | `PORT` (Render sets it)   |
| Actuator        | —                         | health, info, metrics     | health, info (no details) |

> **Fails fast:** `prod` requires `PROD_DB_URL`, `PROD_DB_USERNAME`, `PROD_DB_PASSWORD`,
> `PROD_ENCRYPTION_KEY`, `CLERK_JWKS_URL` — the app refuses to start without them.
> No Spring `context-path` is set — controllers map `/api/v1/...` themselves.

### 9.3 Deployment (Render free web service)

1. Push to `main` — GitHub Actions runs tests, then calls the Render Deploy Hook.
2. Render builds the `Dockerfile` (multi-stage Maven → JRE) and starts with profile `prod`.
3. Required Render env vars: `SPRING_PROFILES_ACTIVE=prod`, `CLERK_JWKS_URL`, `CLERK_ISSUER`,
   `PROD_DB_URL`, `PROD_DB_USERNAME`, `PROD_DB_PASSWORD`, `PROD_ENCRYPTION_KEY`,
   `PROD_CORS_ORIGINS=https://<vercel-app>.vercel.app`.
4. Render health check: `GET /actuator/health` on the web service port.

### 9.4 Database (Neon free)

- Neon is serverless PostgreSQL — use the **pooled** connection string in the JDBC URL.
- Set `sslmode=require` in the URL.
- Free tier auto-suspends idle computes; first request after idle may take a few seconds to wake.

---

## 10. Docker Setup

### Services

| Service    | Internal Port | External Port | Image                   |
|------------|---------------|---------------|-------------------------|
| `postgres` | 5432          | 5433          | `postgres:18-alpine`    |
| `app`      | 8080          | 8080          | Built from `Dockerfile` |
| `pgadmin`  | 80            | 5050          | `dpage/pgadmin4:latest` |

> **v2.0:** Redis removed — auth no longer uses it.

### Quick Start

```bash
cp .env.example .env      # fill CLERK_JWKS_URL / CLERK_ISSUER / DEV_*
docker compose up -d --build
```

Backend: `http://localhost:8080` · Swagger: `http://localhost:8080/swagger-ui.html` · pgAdmin: `http://localhost:5050`

### Manual smoke test

```bash
# Get a Clerk session token from your frontend (or the Clerk Dashboard JWT inspector),
# then:
curl -s http://localhost:8080/api/v1/user/profile \
  -H "Authorization: Bearer <clerk-session-token>"
```

---

## Appendix: Test Status

```
211 tests, all green
├── UserService (Clerk-backed)        6
├── ClerkJwtVerifier (WireMock JWKS)  3
├── GitHub module (WireMock + unit)   47
├── Change Thread module              30+
├── Analysis / Brief / AI             60+
├── OpenAPI contract + smoke          4
└── GlobalExceptionHandler            10
```
