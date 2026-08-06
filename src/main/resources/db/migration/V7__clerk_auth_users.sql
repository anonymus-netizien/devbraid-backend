-- Clerk-backed users: identity now lives in Clerk (email OTP auth).
-- The local users table becomes a mirror keyed by clerk_id; no password storage.
ALTER TABLE users
    ADD COLUMN clerk_id VARCHAR(255);

UPDATE users SET clerk_id = 'legacy-' || id WHERE clerk_id IS NULL;

ALTER TABLE users
    ALTER COLUMN clerk_id SET NOT NULL;

ALTER TABLE users
    DROP COLUMN password_hash;

DROP TABLE refresh_tokens;
