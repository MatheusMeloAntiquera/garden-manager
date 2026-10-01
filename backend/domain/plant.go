package domain

import (
	"time"

	"github.com/google/uuid"
)

// Plant representa uma planta que o usuário tem. A espécie e o ambiente são
// opcionais, mas toda planta precisa de apelido ou de espécie.
type Plant struct {
	ID            uuid.UUID
	UserID        uuid.UUID
	SpeciesID     *uuid.UUID
	EnvironmentID *uuid.UUID
	Nickname      *string
	Notes         *string
	Active        bool

	// Species e Environment só são preenchidos nas leituras do repositório
	// (que fazem join); ao criar ou atualizar, apenas os IDs são usados.
	Species     *PlantSpecies
	Environment *PlantEnvironment

	CreatedAt time.Time
	UpdatedAt time.Time
}

// PlantSpecies é o resumo da espécie de uma planta.
type PlantSpecies struct {
	ID             uuid.UUID
	ScientificName string
	CommonName     *string // nome popular principal
}

// PlantEnvironment é o resumo do ambiente de uma planta.
type PlantEnvironment struct {
	ID   uuid.UUID
	Name string
}
