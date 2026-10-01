// Package datetime define o formato de data e hora ("AAAA-MM-DD HH:mm:ss")
// usado pela API em campos como prazos e execuções de manutenção.
//
// Como o formato não traz fuso, os valores são interpretados e formatados no
// fuso da aplicação, configurado uma vez na inicialização com SetLocation.
package datetime

import (
	"errors"
	"fmt"
	"strconv"
	"sync/atomic"
	"time"

	// Embute a base de fusos horários no binário, para que time.LoadLocation
	// funcione mesmo em ambientes sem zoneinfo (Windows, imagens mínimas).
	_ "time/tzdata"
)

// Layout é o formato aceito e retornado pela API.
const Layout = "2006-01-02 15:04:05"

// ErrInvalidFormat indica um texto fora do formato Layout.
var ErrInvalidFormat = errors.New("data inválida: use o formato AAAA-MM-DD HH:mm:ss")

var location atomic.Pointer[time.Location]

func init() {
	location.Store(time.UTC)
}

// SetLocation define o fuso usado para interpretar e formatar os valores.
// Deve ser chamada na inicialização da aplicação; o padrão é UTC.
func SetLocation(loc *time.Location) {
	location.Store(loc)
}

// Location retorna o fuso configurado com SetLocation.
func Location() *time.Location {
	return location.Load()
}

// LocalDateTime é um instante serializado em JSON como "AAAA-MM-DD HH:mm:ss"
// no fuso da aplicação.
type LocalDateTime struct {
	time.Time
}

// New cria um LocalDateTime a partir de t.
func New(t time.Time) LocalDateTime {
	return LocalDateTime{Time: t}
}

// Parse interpreta text no formato Layout, no fuso da aplicação.
func Parse(text string) (LocalDateTime, error) {
	t, err := time.ParseInLocation(Layout, text, Location())
	if err != nil {
		return LocalDateTime{}, fmt.Errorf("%w: %q", ErrInvalidFormat, text)
	}
	return LocalDateTime{Time: t}, nil
}

// String formata o valor no formato Layout, no fuso da aplicação.
func (d LocalDateTime) String() string {
	return d.In(Location()).Format(Layout)
}

// MarshalJSON implementa json.Marshaler.
func (d LocalDateTime) MarshalJSON() ([]byte, error) {
	return []byte(strconv.Quote(d.String())), nil
}

// UnmarshalJSON implementa json.Unmarshaler. Um null em um campo ponteiro
// não chega aqui: o encoding/json apenas deixa o ponteiro nil.
func (d *LocalDateTime) UnmarshalJSON(data []byte) error {
	text, err := strconv.Unquote(string(data))
	if err != nil {
		return fmt.Errorf("%w: %s", ErrInvalidFormat, data)
	}

	parsed, err := Parse(text)
	if err != nil {
		return err
	}

	*d = parsed
	return nil
}
