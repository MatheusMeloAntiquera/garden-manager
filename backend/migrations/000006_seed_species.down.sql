-- O catálogo só é populado por esta migration, então removê-lo por completo é seguro
-- (os nomes populares saem junto, por ON DELETE CASCADE).
DELETE FROM species;
