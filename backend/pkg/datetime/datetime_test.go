package datetime

import (
	"encoding/json"
	"errors"
	"testing"
	"time"
)

func useLocation(t *testing.T, name string) *time.Location {
	t.Helper()

	loc, err := time.LoadLocation(name)
	if err != nil {
		t.Fatalf("carregando fuso %s: %v", name, err)
	}

	previous := Location()
	SetLocation(loc)
	t.Cleanup(func() { SetLocation(previous) })

	return loc
}

func TestJSONRoundTrip(t *testing.T) {
	loc := useLocation(t, "America/Sao_Paulo")

	var got struct {
		At LocalDateTime `json:"at"`
	}
	if err := json.Unmarshal([]byte(`{"at":"2026-10-05 08:30:15"}`), &got); err != nil {
		t.Fatalf("Unmarshal retornou erro: %v", err)
	}

	want := time.Date(2026, time.October, 5, 8, 30, 15, 0, loc)
	if !got.At.Equal(want) {
		t.Errorf("obteve %v, esperava %v", got.At.Time, want)
	}

	// O mesmo instante, vindo do banco em UTC, sai no fuso da aplicação.
	out, err := json.Marshal(struct {
		At LocalDateTime `json:"at"`
	}{At: New(want.UTC())})
	if err != nil {
		t.Fatalf("Marshal retornou erro: %v", err)
	}
	if string(out) != `{"at":"2026-10-05 08:30:15"}` {
		t.Errorf("obteve %s", out)
	}
}

func TestUnmarshalRejectsInvalidFormats(t *testing.T) {
	useLocation(t, "America/Sao_Paulo")

	inputs := []string{
		`"2026-10-05"`,
		`"2026-10-05T08:30:00"`,
		`"2026-10-05 08:30"`,
		`"05/10/2026 08:30:00"`,
		`"2026-02-30 08:30:00"`,
		`""`,
		`123`,
	}

	for _, input := range inputs {
		t.Run(input, func(t *testing.T) {
			var d LocalDateTime
			err := json.Unmarshal([]byte(input), &d)
			if !errors.Is(err, ErrInvalidFormat) {
				t.Errorf("esperava ErrInvalidFormat, obteve: %v", err)
			}
		})
	}
}

func TestNullLeavesPointerNil(t *testing.T) {
	var got struct {
		At *LocalDateTime `json:"at"`
	}
	if err := json.Unmarshal([]byte(`{"at":null}`), &got); err != nil {
		t.Fatalf("Unmarshal retornou erro: %v", err)
	}
	if got.At != nil {
		t.Errorf("esperava nil, obteve %v", got.At)
	}
}

func TestParseUsesConfiguredLocation(t *testing.T) {
	useLocation(t, "UTC")
	utc, err := Parse("2026-10-05 08:00:00")
	if err != nil {
		t.Fatalf("Parse retornou erro: %v", err)
	}

	useLocation(t, "America/Sao_Paulo")
	saoPaulo, err := Parse("2026-10-05 08:00:00")
	if err != nil {
		t.Fatalf("Parse retornou erro: %v", err)
	}

	if diff := saoPaulo.Sub(utc.Time); diff != 3*time.Hour {
		t.Errorf("esperava 3h de diferença entre os fusos, obteve %v", diff)
	}
}
