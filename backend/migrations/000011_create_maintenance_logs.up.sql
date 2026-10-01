-- Manutenções executadas (histórico). Ao registrar a execução de um
-- agendamento, o agendamento é excluído; created_from_schedule guarda que a
-- execução nasceu dele.
CREATE TABLE maintenance_logs (
    id                     UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plant_id               UUID NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    type_id                UUID NOT NULL REFERENCES maintenance_types(id) ON DELETE RESTRICT,
    created_from_schedule  BOOLEAN NOT NULL DEFAULT false,
    performed_at           TIMESTAMPTZ NOT NULL,
    notes                  TEXT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX maintenance_logs_user_id_performed_at_idx ON maintenance_logs (user_id, performed_at);
CREATE INDEX maintenance_logs_plant_id_idx ON maintenance_logs (plant_id);

-- set_updated_at() foi criada na migration 000001.
CREATE TRIGGER maintenance_logs_set_updated_at
    BEFORE UPDATE ON maintenance_logs
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
