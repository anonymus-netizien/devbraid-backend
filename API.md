# DevBraid Backend — API Documentation

> **Version:** v1.1 (Sprint 1)
> **Base URL:** `http://localhost:8080`
> **Default Profile:** `dev`
> **Framework:** Spring Boot 4.1.0 / Java 17
> **Last Updated:** 2026-07-25
> **Sprint 1 Changes:** OTP email verification (Redis), pending_user flow, access token 24h→30m, disposable email blocking, refresh token scheduled cleanup

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Request/Response Envelope](#2-requestresponse-envelope)
3. [Authentication Flow](#3-authentication-flow)
4. [Endpoints](#4-endpoints)
5. [Entities & Database Schema](#5-entities--database-schema)
6. [DTO Reference](#6-dto-reference)
7. [Exception Handling](#7-exception-handling)
8. [Security Configuration](#8-security-configuration)
9. [Environment Profiles](#9-environment-profiles)
10. [Docker Setup](#10-docker-setup)

---

## 1. Architecture Overview

```
┌─────────────┐     ┌──────────────────────────────────────┐     ┌──────────┐
│   Client    │────▶│         Spring Boot Backend          │────▶│PostgreSQL│
│ (Frontend / │     │                                      │     │  (5433)  │
│  Postman)   │     │  LoggingFilter ──▶ JwtAuthFilter ──▶│     │          │
└─────────────┘     │         │                             │     └──────────┘
                    │    ┌────┴────┐                        │     ┌──────────┐
                    │    │Security │                        │────▶│  Redis   │
                    │    │ Config  │                        │     │  (6379)  │
                    │    └─────────┘                        │     └──────────┘
                    │         │                             │
                    │    ┌────▼────┐                        │
                    │    │ Auth    │                        │
                    │    │Controller│                       │
                    │    └────┬────┘                        │
                    │         │                             │
                    │    ┌────▼────┐                        │
                    │    │  User   │                        │
                    │    │ Service │──▶ UserRepository      │
                    │    │         │──▶ RefreshTokenRepo    │
                    │    │         │──▶ OtpService (Redis)  │
                    │    └────┬────┘                        │
                    │         │                             │
                    │    ┌────▼────┐                        │
                    │    │  JWT    │                        │
                    │    │ Provider│                        │
                    │    └─────────┘                        │
                    └──────────────────────────────────────┘

Redis Usage:
  otp:{email}          → OTP code, TTL 5 min
  otp_verified:{email} → "true" after OTP verification, TTL 10 min
  otp_rate:{email}     → Rate limit counter, TTL 1 min, max 3 req/min
  pending_user:{email} → Pending registration (pw_hash||name), TTL 10 min

Request Flow:
  Client → LoggingFilter → JwtAuthenticationFilter → SecurityConfig
        → AuthController → UserService → Repositories → Database/Redis
        → ApiResponse → Client
```

### Filter Chain Order

| Order | Filter | Responsibility |
|-------|--------|----------------|
| 1 | `LoggingFilter` | Log method, URI, headers, host, query params, timestamp |
| 2 | `JwtAuthenticationFilter` | Extract Bearer token, verify JWT, set `SecurityContextHolder` |
| 3 | `SecurityConfig` chain | CSRF (disabled), CORS, session management (stateless), authorization rules |

---

## 2. Request/Response Envelope

All API responses follow a consistent envelope via `ApiResponse<T>`:

### Success Response

```json
{
  "success": true,
  "message": "Human-readable message",
  "data": { ... }
}
```

### Error Response

```json
{
  "success": false,
  "message": "Error description",
  "data": 409
}
```

The `data` field in error responses carries the HTTP status code as metadata.

### HTTP Status Summary

| Status | Usage |
|--------|-------|
| `200 OK` | Successful operations (login, profile, refresh, logout, OTP) |
| `201 CREATED` | Resource creation (register) |
| `400 BAD_REQUEST` | Validation errors, illegal arguments, disposable email |
| `401 UNAUTHORIZED` | Invalid credentials, revoked token |
| `404 NOT FOUND` | User not found |
| `409 CONFLICT` | Duplicate email during registration |
| `410 GONE` | OTP expired |
| `429 TOO_MANY_REQUESTS` | OTP rate limit exceeded |
| `500 INTERNAL_SERVER_ERROR` | Unhandled runtime exceptions |

---

## 3. Authentication Flow

### 3.1 Registration Flow (with OTP)

```
┌─────────────────────────────────────────────────────────────────────┐
│                    REGISTRATION FLOW (SPRINT 1)                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  FRONTEND                    BACKEND                       REDIS    │
│  ────────                    ──────                       ─────     │
│                                                                      │
│  1. User fills form                                                  │
│     (name, email, password)                                          │
│       │                                                              │
│       ├── POST /auth/register ──────►                                │
│       │                              │                              │
│       │                   ┌──────────▼──────────┐                    │
│       │                   │ Check disposable    │                    │
│       │                   │ email → reject if   │                    │
│       │                   │ mailinator, etc.   │                    │
│       │                   └──────────┬──────────┘                    │
│       │                              │                              │
│       │                   ┌──────────▼──────────┐                    │
│       │                   │ Hash password       │                    │
│       │                   │ Store in Redis as   │────► pending_user  │
│       │                   │ pending_user:{email}│     TTL: 10 min   │
│       │                   └─────────────────────┘                    │
│       │                                                              │
│       ├── POST /auth/otp/send ──────►                                │
│       │                   ┌──────────▼──────────┐                    │
│       │                   │ Generate 6-digit OTP │────► otp:{email} │
│       │                   │ Store in Redis      │     TTL: 5 min    │
│       │                   │ Rate limit check    │────► otp_rate:     │
│       │                   └─────────────────────┘                    │
│       │                                                              │
│  2. User enters OTP                                                  │
│       │                                                              │
│       ├── POST /auth/otp/verify ──────►                              │
│       │                   ┌──────────▼──────────┐                    │
│       │                   │ Verify OTP from Redis                    │
│       │                   │ If match:                                │
│       │                   │  • Delete otp:{email}                    │
│       │                   │  • Set otp_verified:{email}              │
│       │                   │  • Move pending_user → PostgreSQL        │
│       │                   │     (create User, save to DB)            │
│       │                   │  • Delete pending_user from Redis        │
│       │                   │  • Delete otp_verified:{email}           │
│       │                   └─────────────────────┘                    │
│       │                                                              │
│  3. User logs in                                                     │
│       │                                                              │
│       ├── POST /auth/login ──────►  Returns JWT pair                 │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

> **Key decision:** Registration data (password, name) is stored in Redis as `pending_user:{email}` with a 10-minute TTL. Only after OTP verification is the data moved to PostgreSQL. This prevents garbage data from abandoned signups.
>
> **Disposable email blocking:** If the email domain is in the blocklist (28 known disposable domains), registration is rejected immediately.

### 3.2 Token Lifecycle

```
LOGIN ───────────────▶ LoginResponse
                          │
                     ┌────┴────┐
                     │         │
                     ▼         ▼
                Access      Refresh
                Token       Token
                (30 min)    (7 days)
                     │         │
                     │    ┌────┴────┐
                     │    │         │
                     │    ▼         ▼
                     │  REFRESH   LOGOUT
                     │  (rotate)  (revoke)
                     │    │         │
                     │    ▼         ▼
                     │  New pair   Token
                     │  issued     revoked in DB
                     │
                     ▼
                /me (authenticated)
                Returns profile
```

> **Sprint 1 change:** Access token reduced from 24h to 30 minutes (industry standard). Daily `@Scheduled` cleanup purges expired and revoked refresh tokens from the database at 3am.

### 3.3 Refresh Token Rotation

```
1. Client sends refresh token → POST /api/v1/auth/refresh
2. Server verifies JWT signature + "type":"refresh" claim
3. Server looks up SHA-256(token_hash) in refresh_tokens table
4. Server checks revoked flag:
   - If revoked → 401 RefreshTokenRevokedException (token theft detected!)
   - If valid → proceed
5. Server marks current token as revoked (rotation)
6. Server creates new access + refresh token pair
7. Server persists new refresh token hash in DB
8. Server returns new LoginResponse
```

### 3.4 Token Revocation Paths

| Action | What happens |
|--------|--------------|
| **Logout** | Client sends refresh token → hash → find in DB → mark revoked |
| **Refresh** | Old token is marked revoked (rotation). New token is persisted. |
| **Stolen token reuse** | If a rotated token is presented, it's found as revoked → 401 |
| **Cleanup** | `RefreshTokenCleanupService` runs daily at 3am → deletes all expired/revoked tokens |

---

## 4. Endpoints

### 4.1 Send OTP

Generates a 6-digit OTP and stores it in Redis with a 5-minute TTL. Rate-limited to 3 requests per minute per email.

```
POST /api/v1/auth/otp/send
Content-Type: application/json
```

**Request Body:** `OtpSendRequest`
```json
{
  "email": "john@example.com"
}
```

**Success Response:** `200 OK`
```json
{
  "success": true,
  "message": "OTP sent",
  "data": {
    "email": "john@example.com"
  }
}
```

**Error Responses:**
- `429 TOO_MANY_REQUESTS` — Rate limit exceeded (3 req/min)
- `400 BAD_REQUEST` — Validation failure

**Backend Flow:**
1. Increment rate limit counter in Redis (`otp_rate:{email}`, TTL 1 min)
2. If count > 3 → throw `OtpRateLimitException`
3. Generate 6-digit OTP via `SecureRandom`
4. Store in Redis: `otp:{email}` = OTP, TTL 5 min
5. Log OTP in dev mode (no email service yet)

---

### 4.2 Verify OTP

Verifies the OTP against the stored value in Redis. On success, finalizes any pending registration by moving it from Redis to PostgreSQL.

```
POST /api/v1/auth/otp/verify
Content-Type: application/json
```

**Request Body:** `OtpVerifyRequest`
```json
{
  "email": "john@example.com",
  "otp": "123456"
}
```

**Validation Rules:**
| Field | Rule |
|-------|------|
| `email` | Required, valid email format |
| `otp` | Required, exactly 6 digits |

**Success Response:** `200 OK`
```json
{
  "success": true,
  "message": "OTP verified",
  "data": {
    "email": "john@example.com",
    "verified": true
  }
}
```

**Error Responses:**
- `400 BAD_REQUEST` — Invalid OTP
- `410 GONE` — OTP expired or was never generated

**Backend Flow:**
1. Look up `otp:{email}` in Redis
2. If null → throw `OtpExpiredException`
3. If mismatch → throw `OtpInvalidException`
4. Delete `otp:{email}`, set `otp_verified:{email}` = "true", TTL 10 min
5. Check for `pending_user:{email}` in Redis — if exists, move to PostgreSQL (finalize registration)
6. Return success

---

### 4.3 Register

Stores registration data in Redis as pending until OTP verification. Checks for disposable email domains.

```
POST /api/v1/auth/register
Content-Type: application/json
```

**Request Body:** `RegisterRequest`
```json
{
  "fullName": "John Doe",
  "email": "john@example.com",
  "password": "SecurePass1!"
}
```

**Validation Rules:**
| Field | Rule |
|-------|------|
| `fullName` | Required, max 80 chars |
| `email` | Required, valid email format, not disposable |
| `password` | Required, 8-72 chars (bcrypt limit) |

**Success Response:** `201 CREATED`
```json
{
  "success": true,
  "message": "Registration successful",
  "data": null
}
```

**Error Responses:**
- `409 CONFLICT` — Email already registered
- `400 BAD_REQUEST` — Validation failure, disposable email

**Backend Flow (Sprint 1):**
1. Check disposable email → reject if blocked domain
2. Check if email already exists in PostgreSQL → throw if duplicate
3. BCrypt-hash the password
4. Store in Redis as `pending_user:{email}` (hash||name), TTL 10 min
5. If OTP already verified (backward compat), finalize immediately to PostgreSQL
6. Return 201 — data stays in Redis until OTP verify finalizes it

---

### 4.4 Login

Authenticates and returns JWT token pair.

```
POST /api/v1/auth/login
Content-Type: application/json
```

**Request Body:** `LoginRequest`
```json
{
  "email": "john@example.com",
  "password": "SecurePass1!"
}
```

**Success Response:** `200 OK`
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "issuedAt": "2026-07-25T10:00:00Z",
    "expiresAt": "2026-07-25T10:30:00Z",
    "fullName": "John Doe",
    "email": "john@example.com",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "role": "DEVELOPER"
  }
}
```

> **Note:** Access token `expiresAt` shows 30 min from issuance (Sprint 1 change from 24h).

**Error Responses:**
- `401 UNAUTHORIZED` — Invalid email or password
- `404 NOT_FOUND` — Email not registered

**Backend Flow:**
1. Find user by email → throw `UserNotFoundException` if not found
2. BCrypt match password → throw `InvalidCredentialsException` if wrong
3. Create access token (30 min) + refresh token (7 days) via `JwtTokenProvider`
4. SHA-256 hash the refresh token, persist to `refresh_tokens` table
5. Return `LoginResponse` with tokens and user info

---

### 4.5 Get Profile (Me)

Returns the authenticated user's profile.

```
GET /api/v1/auth/me
Authorization: Bearer <access_token>
```

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | `Bearer <access_token>` |

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

**Error Responses:**
- `401 UNAUTHORIZED` — Missing or invalid token
- `404 NOT_FOUND` — User not found

---

### 4.6 Refresh Token

Rotates an existing refresh token for a new token pair.

```
POST /api/v1/auth/refresh
Content-Type: application/json
```

**Request Body:** `RefreshTokenRequest`
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Success Response:** `200 OK`
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "issuedAt": "2026-07-25T10:00:00Z",
    "expiresAt": "2026-07-25T10:30:00Z",
    "fullName": "John Doe",
    "email": "john@example.com",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "role": "DEVELOPER"
  }
}
```

**Error Responses:**
- `401 UNAUTHORIZED` — Invalid refresh token, token not found, or token has been revoked
- `400 BAD_REQUEST` — Missing refresh token

---

### 4.7 Logout

Revokes the refresh token so it can no longer be used.

```
POST /api/v1/auth/logout
Content-Type: application/json
```

**Request Body:** `RefreshTokenRequest`
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Success Response:** `200 OK`
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

**Error Responses:**
- `401 UNAUTHORIZED` — Invalid refresh token (not found in DB)

---

## 5. Entities & Database Schema

### 5.1 Entity Relationship Diagram

```
┌──────────────────────┐       ┌────────────────────────────┐
│        users         │       │      refresh_tokens        │
├──────────────────────┤       ├────────────────────────────┤
│ id (PK, UUIDv7)      │◀──────│ id (PK, UUIDv7)            │
│ email (UNIQUE)       │  1:N  │ token_hash VARCHAR(64)     │
│ full_name            │       │ user_id (FK → users.id)    │
│ password_hash        │       │ expires_at TIMESTAMPTZ     │
│ created_at           │       │ revoked BOOLEAN            │
└──────────────────────┘       │ created_at                 │
                               └────────────────────────────┘
```

### 5.2 Redis Keys

| Key Pattern | Value | TTL | Purpose |
|-------------|-------|-----|---------|
| `otp:{email}` | 6-digit code | 5 min | OTP verification code |
| `otp_verified:{email}` | "true" | 10 min | Email verification flag |
| `otp_rate:{email}` | Request count | 1 min | Rate limiter (max 3/min) |
| `pending_user:{email}` | `hash||name` | 10 min | Registration data awaiting OTP |

---

## 6. DTO Reference

### 6.1 Request DTOs

#### `OtpSendRequest`

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `email` | String | `@NotBlank`, `@Email` | Email to send OTP to |

#### `OtpVerifyRequest`

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `email` | String | `@NotBlank`, `@Email` | Email to verify |
| `otp` | String | `@NotBlank`, 6 digits | OTP code |

#### `RegisterRequest`

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `fullName` | String | `@NotBlank`, max 80 | User's display name |
| `email` | String | `@NotBlank`, `@Email` | Login email (unique, not disposable) |
| `password` | String | `@NotBlank`, 8-72 chars | Plaintext password (BCrypt hashed server-side) |

#### `LoginRequest`

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `email` | String | `@NotBlank`, `@Email` | Registered email |
| `password` | String | `@NotBlank` | Plaintext password |

#### `RefreshTokenRequest`

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `refreshToken` | String | `@NotBlank` | JWT refresh token string |

### 6.2 Response DTOs

#### `OtpSendResponse`

| Field | Type | Description |
|-------|------|-------------|
| `email` | String | Email that OTP was sent to |

#### `OtpVerifyResponse`

| Field | Type | Description |
|-------|------|-------------|
| `email` | String | Verified email |
| `verified` | boolean | Always true on success |

#### `LoginResponse`

| Field | Type | Description |
|-------|------|-------------|
| `accessToken` | String | JWT access token (30 min expiry) |
| `refreshToken` | String | JWT refresh token (7 day expiry) |
| `issuedAt` | Instant | Token creation timestamp |
| `expiresAt` | Instant | Access token expiry timestamp |
| `fullName` | String | User's display name |
| `email` | String | User's email |
| `userId` | UUID | User's unique identifier |
| `role` | String | User role (currently always `DEVELOPER`) |

#### `UserProfileResponse`

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | User's unique identifier |
| `fullName` | String | User's display name |
| `email` | String | User's email |
| `role` | String | User role (currently always `DEVELOPER`) |
| `createdAt` | OffsetDateTime | Account creation timestamp |

---

## 7. Exception Handling

All exceptions are handled centrally by `GlobalExceptionHandler` (`@RestControllerAdvice`).

| Exception | HTTP Status | Log Level |
|-----------|-------------|-----------|
| `UserAlreadyExistsException` | `409 CONFLICT` | WARN |
| `UserNotFoundException` | `404 NOT_FOUND` | WARN |
| `InvalidCredentialsException` | `401 UNAUTHORIZED` | WARN |
| `RefreshTokenRevokedException` | `401 UNAUTHORIZED` | WARN |
| `OtpExpiredException` | `410 GONE` | WARN |
| `OtpInvalidException` | `400 BAD_REQUEST` | WARN |
| `OtpRateLimitException` | `429 TOO_MANY_REQUESTS` | WARN |
| `IllegalArgumentException` | `400 BAD_REQUEST` | WARN |
| `MethodArgumentNotValidException` | `400 BAD_REQUEST` | WARN |
| `RuntimeException` | `500 INTERNAL_SERVER_ERROR` | ERROR |
| `Exception` | `500 INTERNAL_SERVER_ERROR` | ERROR |

---

## 8. Security Configuration

### 8.1 Security Filter Chain

```
HttpSecurity Configuration:
├── CSRF: DISABLED (stateless JWT auth, no session cookies)
├── CORS: Enabled (see origins below)
├── Session: STATELESS (no HTTP sessions)
├── Authorization:
│   ├── PERMIT ALL: POST /api/v1/auth/register
│   │                POST /api/v1/auth/login
│   │                POST /api/v1/auth/refresh
│   │                POST /api/v1/auth/logout
│   │                POST /api/v1/auth/otp/send
│   │                POST /api/v1/auth/otp/verify
│   └── AUTHENTICATED: Everything else (including /api/v1/auth/me)
└── Filters:
    └── JwtAuthenticationFilter BEFORE UsernamePasswordAuthenticationFilter
```

### 8.2 JWT Token Structure (Sprint 1)

**Access Token (30 min):**
```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john@example.com",
  "role": "DEVELOPER",
  "type": "access",
  "iat": 1784902054,
  "exp": 1784903854
}
```

**Refresh Token (7 days):**
```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john@example.com",
  "role": "DEVELOPER",
  "type": "refresh",
  "iat": 1784902054,
  "exp": 1785506854
}
```

---

## 9. Environment Profiles

| Profile | File | Purpose |
|---------|------|---------|
| `dev` (default) | `application-dev.yaml` | Local development with Docker PostgreSQL + Redis |
| `stage` | `application-stage.yaml` | Pre-production testing |
| `prod` | `application-prod.yaml` | Production deployment |

### Key Differences Between Profiles

| Setting | dev | stage | prod |
|---------|-----|-------|------|
| JPA DDL | `update` | `validate` | `validate` |
| SQL Logging | `DEBUG` | `WARN` | `WARN` |
| Pool Size | 5 | 15 | 25 |
| Redis | Local (localhost:6379) | Env var | Env var |
| CORS | `localhost:*` | `staging.devbraid.com` | `app.devbraid.com` |
| Swagger UI | Enabled | Enabled | Disabled |
| Access Token | 30 min | 30 min | 30 min |
| Refresh Token | 7 days | 7 days | 7 days |

---

## 10. Docker Setup

### Services

| Service | Internal Port | External Port | Image |
|---------|---------------|---------------|-------|
| `postgres` | 5432 | 5433 | `postgres:18-alpine` |
| `redis` | 6379 | 6379 | `redis:7-alpine` |
| `app` | 8080 | 8080 | Built from `Dockerfile` |
| `pgadmin` | 80 | 5050 | `dpage/pgadmin4:latest` |

### Quick Start

```bash
# 1. Copy and configure environment
cp .env.example .env

# 2. Start all services (PostgreSQL + Redis + App + pgAdmin)
docker compose up --build -d

# 3. Verify
docker compose ps

# 4. Check app logs
docker compose logs -f app
```

### E2E Health Check

```bash
# 1. Register (stores pending in Redis)
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Test User","email":"test@example.com","password":"Password123!"}'

# 2. Send OTP
curl -s -X POST http://localhost:8080/api/v1/auth/otp/send \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com"}'

# 3. Verify OTP (finalizes registration to PostgreSQL)
#    Check app logs for the OTP code: docker logs devbraid-app | grep OTP
curl -s -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","otp":"<OTP_FROM_LOGS>"}'

# 4. Login
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"Password123!"}'

# 5. Access pgAdmin at http://localhost:5050 (admin@devbraid.com / admin)
```

---

## Appendix: New Files (Sprint 1)

| File | Purpose |
|------|---------|
| `OtpService.java` | OTP generation, verification, pending_user storage in Redis |
| `DisposableEmailValidator.java` | Blocks 28 known disposable email domains |
| `RefreshTokenCleanupService.java` | Daily `@Scheduled` purge of expired/revoked tokens |
| `SchedulingConfig.java` | `@EnableScheduling` |
| `OtpGenerator.java` (util) | 6-digit SecureRandom OTP generation |
| `OtpSendRequest.java` / `OtpSendResponse.java` | OTP send DTOs |
| `OtpVerifyRequest.java` / `OtpVerifyResponse.java` | OTP verify DTOs |
| `OtpExpiredException.java` | OTP expired (410 GONE) |
| `OtpInvalidException.java` | Invalid OTP (400 BAD_REQUEST) |
| `OtpRateLimitException.java` | Rate limit exceeded (429 TOO_MANY_REQUESTS) |
| `RedisConfig.java` | `StringRedisTemplate` bean |

## Appendix: Test Status

```
Sprint 1 Final: 40/40 tests PASS
├── JwtTokenProviderTest (12)
├── JwtAuthenticationFilterTest (9)
├── UserServiceTest (10) — updated for Redis-based registration
├── AuthControllerTest (8) — updated for void verifyOtp + finalizeRegistration
└── DevbraidBackendApplicationTests (1) — context loading
```

## Appendix: Changelog (Sprint 1)

| Date | Change | Commit |
|------|--------|--------|
| 2026-07-25 | Pending_user TTL reduced to 10 min | `d48e69a` |
| 2026-07-25 | Redis pending_user flow, 30-min token TTL, disposable email, cleanup job | `48bbdca` |
| 2026-07-25 | Auth UI redesign (centered layout, brand pane) | `9e0608b` |
| 2026-07-25 | Eslint ^9.x peer fix, Dockerfile cleanup | `0fe717a` |
| 2026-07-25 | Redis config fix (app.data.redis → spring.data.redis) | `55b4c97` |
| 2026-07-25 | Feature-based module restructuring | `a642cdb` |
| 2026-07-24 | Redis OTP verification for signup | `bdc00a3` |
| 2026-07-24 | JWT auth, refresh token rotation, logout | Earlier commits |
