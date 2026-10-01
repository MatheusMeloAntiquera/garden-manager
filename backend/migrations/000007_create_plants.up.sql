CREATE TABLE plants (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    species_id      UUID NULL REFERENCES species(id) ON DELETE RESTRICT,
    environment_id  UUID NULL REFERENCES environments(id) ON DELETE SET NULL, -- excluir o ambiente desvincula a planta
    nickname        VARCHAR(100) NULL,
    notes           TEXT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- toda planta precisa de um jeito de ser identificada: apelido ou espécie
    CONSTRAINT plants_nickname_or_species_check CHECK (nickname IS NOT NULL OR species_id IS NOT NULL)
);

CREATE INDEX plants_user_id_idx ON plants (user_id);
CREATE INDEX plants_environment_id_idx ON plants (environment_id);
CREATE INDEX plants_species_id_idx ON plants (species_id);

-- set_updated_at() foi criada na migration 000001.
CREATE TRIGGER plants_set_updated_at
    BEFORE UPDATE ON plants
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
