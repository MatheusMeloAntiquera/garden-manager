package pagination

import (
	"net/http/httptest"
	"testing"
)

func TestParse(t *testing.T) {
	tests := []struct {
		name    string
		query   string
		want    Params
		wantErr bool
	}{
		{name: "sem parâmetros", query: "", want: Params{}},
		{name: "page e page_size", query: "?page=3&page_size=50", want: Params{Page: 3, PageSize: 50}},
		{name: "page não numérico", query: "?page=abc", wantErr: true},
		{name: "page zero", query: "?page=0", wantErr: true},
		{name: "page_size acima do máximo", query: "?page_size=101", wantErr: true},
		{name: "page_size zero", query: "?page_size=0", wantErr: true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := Parse(httptest.NewRequest("GET", "/"+tt.query, nil))
			if (err != nil) != tt.wantErr {
				t.Fatalf("erro = %v, esperava erro: %v", err, tt.wantErr)
			}
			if got != tt.want {
				t.Errorf("obteve %+v, esperava %+v", got, tt.want)
			}
		})
	}
}

func TestNormalizeAndOffset(t *testing.T) {
	got := Params{}.Normalize()
	if got.Page != 1 || got.PageSize != DefaultPageSize {
		t.Errorf("padrão: obteve %+v", got)
	}

	got = Params{Page: 3, PageSize: 500}.Normalize()
	if got.PageSize != MaxPageSize {
		t.Errorf("esperava page_size limitado a %d, obteve %d", MaxPageSize, got.PageSize)
	}
	if got.Offset() != 2*MaxPageSize {
		t.Errorf("esperava offset %d, obteve %d", 2*MaxPageSize, got.Offset())
	}
}
