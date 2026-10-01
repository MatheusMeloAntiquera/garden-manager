-- Catálogo de procedimentos de manutenção (rega, poda, adubação...). É
-- populado pela migration 000009 e só é lido pela API.
CREATE TABLE maintenance_types (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- set_updated_at() foi criada na migration 000001.
CREATE TRIGGER maintenance_types_set_updated_at
    BEFORE UPDATE ON maintenance_types
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
