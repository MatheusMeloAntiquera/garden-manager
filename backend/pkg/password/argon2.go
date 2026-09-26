// Package password implementa o hashing seguro de senhas com argon2id.
package password

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"strings"

	"golang.org/x/crypto/argon2"
)

// Parâmetros do argon2id recomendados pela OWASP para uso em servidores
// com memória disponível moderada. Ficam guardados junto do hash, então
// podem ser ajustados no futuro sem invalidar hashes já gravados.
const (
	saltLength  = 16
	keyLength   = 32
	memoryKiB   = 64 * 1024 // 64 MiB
	iterations  = 3
	parallelism = 2
)

// Hasher gera e verifica hashes de senha.
type Hasher interface {
	// Hash gera o hash argon2id de uma senha em texto puro, no formato PHC.
	Hash(password string) (string, error)
	// Verify compara uma senha em texto puro com um hash previamente gerado
	// por Hash. Retorna true se a senha corresponder ao hash.
	Verify(password, encodedHash string) (bool, error)
}

type argon2Hasher struct{}

// NewHasher cria um Hasher baseado em argon2id.
func NewHasher() Hasher {
	return argon2Hasher{}
}

func (argon2Hasher) Hash(password string) (string, error) {
	salt := make([]byte, saltLength)
	if _, err := rand.Read(salt); err != nil {
		return "", fmt.Errorf("gerando salt: %w", err)
	}

	hash := argon2.IDKey([]byte(password), salt, iterations, memoryKiB, parallelism, keyLength)

	encodedSalt := base64.RawStdEncoding.EncodeToString(salt)
	encodedHash := base64.RawStdEncoding.EncodeToString(hash)

	// Formato PHC: $argon2id$v=19$m=<memoria>,t=<iteracoes>,p=<paralelismo>$<salt>$<hash>
	return fmt.Sprintf(
		"$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s",
		argon2.Version, memoryKiB, iterations, parallelism, encodedSalt, encodedHash,
	), nil
}

func (argon2Hasher) Verify(password, encodedHash string) (bool, error) {
	version, memory, time, parallel, salt, hash, err := decodeHash(encodedHash)
	if err != nil {
		return false, err
	}
	if version != argon2.Version {
		return false, fmt.Errorf("versão do argon2 incompatível: %d", version)
	}

	candidateHash := argon2.IDKey([]byte(password), salt, time, memory, parallel, uint32(len(hash)))

	// Comparação em tempo constante para não vazar informação por timing.
	return subtle.ConstantTimeCompare(hash, candidateHash) == 1, nil
}

func decodeHash(encodedHash string) (version int, memory, time uint32, parallel uint8, salt, hash []byte, err error) {
	parts := strings.Split(encodedHash, "$")
	if len(parts) != 6 || parts[1] != "argon2id" {
		return 0, 0, 0, 0, nil, nil, fmt.Errorf("formato de hash inválido")
	}

	if _, err = fmt.Sscanf(parts[2], "v=%d", &version); err != nil {
		return 0, 0, 0, 0, nil, nil, fmt.Errorf("lendo versão do hash: %w", err)
	}

	if _, err = fmt.Sscanf(parts[3], "m=%d,t=%d,p=%d", &memory, &time, &parallel); err != nil {
		return 0, 0, 0, 0, nil, nil, fmt.Errorf("lendo parâmetros do hash: %w", err)
	}

	salt, err = base64.RawStdEncoding.DecodeString(parts[4])
	if err != nil {
		return 0, 0, 0, 0, nil, nil, fmt.Errorf("decodificando salt: %w", err)
	}

	hash, err = base64.RawStdEncoding.DecodeString(parts[5])
	if err != nil {
		return 0, 0, 0, 0, nil, nil, fmt.Errorf("decodificando hash: %w", err)
	}

	return version, memory, time, parallel, salt, hash, nil
}
