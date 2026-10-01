package species

import (
	"context"
	"errors"
	"testing"

	"github.com/google/uuid"

	"github.com/matheusantiquera/garden-manager/backend/domain"
	"github.com/matheusantiquera/garden-manager/backend/pkg/pagination"
)

// fakeRepository registra os argumentos recebidos por List, para conferir o
// que o service repassa ao repositório.
type fakeRepository struct {
	species []domain.Species

	gotQuery, gotCategory string
	gotLimit, gotOffset   int
}

func (f *fakeRepository) List(_ context.Context, query, category string, limit, offset int) ([]domain.Species, int, error) {
	f.gotQuery, f.gotCategory, f.gotLimit, f.gotOffset = query, category, limit, offset
	return f.species, len(f.species), nil
}

func (f *fakeRepository) FindByID(_ context.Context, id uuid.UUID) (domain.Species, error) {
	for _, s := range f.species {
		if s.ID == id {
			return s, nil
		}
	}
	return domain.Species{}, ErrSpeciesNotFound
}

func newFakeRepository() *fakeRepository {
	return &fakeRepository{species: []domain.Species{{
		ID:             uuid.New(),
		ScientificName: "Calliandra tweedii",
		Family:         "Fabaceae",
		Category:       "flor",
		CommonNames: []domain.CommonName{
			{Name: "caliandra-vermelha", IsPrimary: true},
			{Name: "esponjinha-vermelha"},
		},
	}}}
}

func TestListRejectsInvalidCategory(t *testing.T) {
	svc := NewService(newFakeRepository())

	_, err := svc.List(context.Background(), ListInput{Category: "mineral"})
	if !errors.Is(err, ErrInvalidCategory) {
		t.Fatalf("esperava ErrInvalidCategory, obteve: %v", err)
	}
}

func TestListNormalizesFiltersAndPagination(t *testing.T) {
	repo := newFakeRepository()
	svc := NewService(repo)

	list, err := svc.List(context.Background(), ListInput{
		Params:   pagination.Params{Page: 3, PageSize: 10},
		Query:    "  esponjinha ",
		Category: "flor",
	})
	if err != nil {
		t.Fatalf("List retornou erro: %v", err)
	}

	if repo.gotQuery != "esponjinha" || repo.gotCategory != "flor" {
		t.Errorf("filtros repassados: query=%q category=%q", repo.gotQuery, repo.gotCategory)
	}
	if repo.gotLimit != 10 || repo.gotOffset != 20 {
		t.Errorf("esperava limit=10 offset=20, obteve limit=%d offset=%d", repo.gotLimit, repo.gotOffset)
	}
	if list.Page != 3 || list.PageSize != 10 || list.Total != 1 {
		t.Errorf("resposta inesperada: %+v", list)
	}
}

func TestListDefaultsPagination(t *testing.T) {
	repo := newFakeRepository()

	if _, err := NewService(repo).List(context.Background(), ListInput{}); err != nil {
		t.Fatalf("List retornou erro: %v", err)
	}

	if repo.gotLimit != pagination.DefaultPageSize || repo.gotOffset != 0 {
		t.Errorf("esperava limit=%d offset=0, obteve limit=%d offset=%d", pagination.DefaultPageSize, repo.gotLimit, repo.gotOffset)
	}
}

func TestResponseExposesPrimaryAndAllCommonNames(t *testing.T) {
	repo := newFakeRepository()
	svc := NewService(repo)

	got, err := svc.Get(context.Background(), repo.species[0].ID)
	if err != nil {
		t.Fatalf("Get retornou erro: %v", err)
	}

	if got.CommonName == nil || *got.CommonName != "caliandra-vermelha" {
		t.Errorf("esperava common_name principal, obteve %v", got.CommonName)
	}
	if len(got.CommonNames) != 2 {
		t.Errorf("esperava 2 nomes populares, obteve %v", got.CommonNames)
	}
}

func TestGetUnknownSpecies(t *testing.T) {
	svc := NewService(newFakeRepository())

	_, err := svc.Get(context.Background(), uuid.New())
	if !errors.Is(err, ErrSpeciesNotFound) {
		t.Fatalf("esperava ErrSpeciesNotFound, obteve: %v", err)
	}
}

func TestEscapeLike(t *testing.T) {
	if got := escapeLike(`50%_\`); got != `50\%\_\\` {
		t.Errorf("escapeLike = %q", got)
	}
}
