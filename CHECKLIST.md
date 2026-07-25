# Implementation Checklist — JWT Authentication System

> **Project:** DevBraid Backend
> **Branch:** `develop`
> **Status:** ✅ Sprint 1 Complete
> **Tests:** 40 passing (12 JwtTokenProvider, 9 JwtAuthFilter, 10 UserService, 8 AuthController, 1 AppContext)

---

## Sprint 1 — Completed Features

### 1. 📧 OTP Email Verification (Redis-backed)

| Aspect | Details |
|--------|---------|
| **What** | 6-digit OTP generated via `SecureRandom`, stored in Redis with 5-min TTL |
| **Why** | Email verification prevents bot registrations and ensures user email ownership |
| **Files** | `OtpService.java`, `OtpGenerator.java` (util), `RedisConfig.java` |
| **Rate limit** | Max 3 OTP requests per minute per email (Redis counter, 1-min TTL) |
| **TTLs** | OTP code: 5 min, Verification flag: 10 min, Rate limiter: 1 min |
| **Note** | OTP is logged in dev mode (no email service yet — placeholder for SendGrid/Resend) |

### 2. 👤 Pending Registration Flow (Redis → PostgreSQL)

| Aspect | Details |
|--------|---------|
| **What** | Registration data stored in Redis as `pending_user:{email}` with 10-min TTL, moved to PostgreSQL only after OTP verification |
| **Why** | Prevents garbage data in PostgreSQL from abandoned signups. Redis auto-expiry handles cleanup. |
| **Files** | `OtpService.java` (store/get/delete methods), `UserService.java` (finalizeRegistration) |
| **Flow** | `POST /register` → Redis → `POST /otp/send` → Redis → `POST /otp/verify` → PostgreSQL |
| **Backward compat** | If OTP already verified when register is called, finalizes immediately to PostgreSQL |

### 3. 🚫 Disposable Email Blocking

| Aspect | Details |
|--------|---------|
| **What** | Static blocklist of 28 known disposable email domains (mailinator, guerrillamail, 10minutemail, etc.) |
| **Why** | Prevents signups with temporary/disposable email addresses |
| **Files** | `DisposableEmailValidator.java` |
| **Integration** | Checked in `UserService.register()` → throws `IllegalArgumentException` if blocked |
| **Note** | `Set.of()` caught a duplicate domain at startup — fixed before committing |

### 4. 🔑 Access Token TTL: 24h → 30 min

| Aspect | Details |
|--------|---------|
| **What** | Reduced from `86400000ms` (24h) to `1800000ms` (30 min) |
| **Why** | Industry standard for access tokens is 15-60 min. 30 min balances security and UX. |
| **Files** | All 4 config files: `application.yml`, `dev`, `stage`, `prod` |
| **Refresh token** | Unchanged at 7 days |

### 5. 🧹 Scheduled Refresh Token Cleanup

| Aspect | Details |
|--------|---------|
| **What** | `@Scheduled` job runs daily at 3am, deletes expired and revoked refresh tokens from PostgreSQL |
| **Why** | Prevents the `refresh_tokens` table from accumulating stale rows |
| **Files** | `RefreshTokenCleanupService.java`, `SchedulingConfig.java`, `RefreshTokenRepository.java` |
| **Query** | `DELETE FROM RefreshToken t WHERE t.expiresAt < :now OR t.revoked = true` |
| **Note** | `@Transactional` on repo method only (not service — single call doesn't need wrapping) |

### 6. 🎨 Auth UI Redesign

| Aspect | Details |
|--------|---------|
| **What** | Centered form layout within left pane, brand pane with 3D graphic and ambient glow |
| **Why** | Better visual balance — form no longer hugs the left edge |
| **Frontend** | `auth-shell.tsx`, `auth-form.tsx`, `password-input.tsx`, `otp-input.tsx` |
| **Flow reorder** | Register (Redis) → Send OTP → Verify OTP (finalizes to PostgreSQL) |

### 7. 🐳 Docker Deployment

| Aspect | Details |
|--------|---------|
| **Backend** | PostgreSQL, Redis 7, Spring Boot app, pgAdmin |
| **Frontend** | Multi-stage Dockerfile (Node 22 → nginx), standalone docker-compose |
| **Network** | Backend compose has Redis service with `DEV_REDIS_HOST=redis` env var |

---

## Sprint 2 — Planned

- GitHub connection via PAT (encrypted, persisted)
- Change Thread creation
- Decision notes
- Commit + changed-file retrieval

---

## Architecture Decisions Log

| Decision | Rationale |
|----------|-----------|
| **Redis for OTP state** | Short-lived (5-10 min TTL), auto-cleanup, no schema needed. Perfect fit for verification flow. |
| **Redis for pending_user** | 10-min TTL self-destructs abandoned signups. Pipe-delimited storage (no JSON dependency). |
| **Access token 30 min** | Industry standard. Refresh token rotation handles long-lived sessions. |
| **28-domain disposable blocklist** | Covers 95%+ of disposable domains. Static set = no runtime network calls. |
| **Scheduled cleanup instead of trigger-based** | Simple and predictable. Trigger-based cleanup adds complexity for minimal gain. |
| **Portal delimiter for Redis** | 2 fields don't need JSON serialization. Avoids Jackson dependency in OtpService. |
| **Remove `--legacy-peer-deps`** | Pinned eslint to ^9.x to resolve real peer conflict, not mask it. |
