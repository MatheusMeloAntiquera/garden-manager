package password

import "testing"

func TestHashAndVerify(t *testing.T) {
	hasher := NewHasher()

	hash, err := hasher.Hash("Sup3r$ecret")
	if err != nil {
		t.Fatalf("Hash retornou erro: %v", err)
	}

	ok, err := hasher.Verify("Sup3r$ecret", hash)
	if err != nil {
		t.Fatalf("Verify retornou erro: %v", err)
	}
	if !ok {
		t.Fatal("esperava que a senha correta fosse válida")
	}

	ok, err = hasher.Verify("senha-errada", hash)
	if err != nil {
		t.Fatalf("Verify retornou erro: %v", err)
	}
	if ok {
		t.Fatal("esperava que a senha errada fosse inválida")
	}
}

func TestHashGeneratesDifferentSaltsAndHashes(t *testing.T) {
	hasher := NewHasher()

	hash1, err := hasher.Hash("Sup3r$ecret")
	if err != nil {
		t.Fatalf("Hash retornou erro: %v", err)
	}

	hash2, err := hasher.Hash("Sup3r$ecret")
	if err != nil {
		t.Fatalf("Hash retornou erro: %v", err)
	}

	if hash1 == hash2 {
		t.Fatal("esperava hashes diferentes para a mesma senha (salts diferentes)")
	}
}

func TestVerifyRejectsInvalidHashFormat(t *testing.T) {
	hasher := NewHasher()

	if _, err := hasher.Verify("qualquer", "hash-invalido"); err == nil {
		t.Fatal("esperava erro para um hash em formato inválido")
	}
}
