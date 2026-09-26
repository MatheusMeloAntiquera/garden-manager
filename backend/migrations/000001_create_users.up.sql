CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(100) NOT NULL,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password           TEXT NULL, -- hash argon2id; nulo para contas criadas apenas via OAuth (futuro)
    login_attempts     INTEGER NOT NULL DEFAULT 0,
    blocked            BOOLEAN NOT NULL DEFAULT false,
    active             BOOLEAN NOT NULL DEFAULT true,
    email_verified_at  TIMESTAMPTZ NULL, -- preparação para verificação de e-mail e login via OAuth (futuro)
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
