-- Usada na busca do catálogo por nome popular, sem diferenciar acentos.
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE species (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    scientific_name  VARCHAR(150) NOT NULL UNIQUE,
    family           VARCHAR(100) NOT NULL,
    category         VARCHAR(20) NOT NULL CHECK (category IN
                         ('folhagem', 'suculenta', 'flor', 'arvore', 'erva', 'hortalica', 'frutifera', 'grama')),
    gbif_key         BIGINT NULL, -- chave do táxon no GBIF, para conferir/atualizar o nome científico
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- set_updated_at() foi criada na migration 000001.
CREATE TRIGGER species_set_updated_at
    BEFORE UPDATE ON species
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Um nome popular pode valer para várias espécies (ex.: caliandra-vermelha) e
-- uma espécie pode ter vários nomes, por isso ficam em tabela separada.
CREATE TABLE species_common_names (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    species_id  UUID NOT NULL REFERENCES species(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    is_primary  BOOLEAN NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX species_common_names_species_id_name_key
    ON species_common_names (species_id, lower(name));

-- No máximo um nome principal por espécie.
CREATE UNIQUE INDEX species_common_names_one_primary_key
    ON species_common_names (species_id) WHERE is_primary;
