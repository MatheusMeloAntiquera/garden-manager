-- Manutenções planejadas (agendamentos). Ao registrar a execução, o
-- agendamento é excluído e a execução fica em maintenance_logs.
CREATE TABLE maintenance_schedules (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plant_id    UUID NOT NULL REFERENCES plants(id) ON DELETE CASCADE, -- excluir a planta apaga as manutenções dela
    type_id     UUID NOT NULL REFERENCES maintenance_types(id) ON DELETE RESTRICT,
    due_at      TIMESTAMPTZ NOT NULL, -- prazo para execução
    notes       TEXT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX maintenance_schedules_user_id_due_at_idx ON maintenance_schedules (user_id, due_at);
CREATE INDEX maintenance_schedules_plant_id_idx ON maintenance_schedules (plant_id);

-- set_updated_at() foi criada na migration 000001.
CREATE TRIGGER maintenance_schedules_set_updated_at
    BEFORE UPDATE ON maintenance_schedules
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
