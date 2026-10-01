# Design das telas

Protótipo das telas do app, editado no canvas do Claude:
https://claude.ai/artifact/4kgsLHx4mM5JphFshLgoLS

Esta pasta é a cópia versionada do canvas. Cada tela é um arquivo `.dc.html`
e o `canvas.json` guarda a posição e o título de cada tela no canvas.

## Paleta "Musgo"

| Token | Cor | Uso |
|---|---|---|
| Primária | `#2E5E45` | Botões, FAB, links |
| Container primário | `#CDE6D3` | Chip ativo, item ativo da navegação |
| Texto sobre container | `#0F2F1E` | Texto em containers |
| Fundo | `#F4F6F1` | Fundo das telas |
| Superfície | `#FFFFFF` | Cartões e campos |
| Superfície variante | `#E7EDE4` | Chips, avatares |
| Texto | `#18211B` | Títulos e corpo |
| Texto secundário | `#4E5B52` | Legendas, rótulos |
| Contorno | `#C3CCC2` | Bordas de campos |
| Água | `#2F5F8A` / `#D8E6F3` | Rega |
| Pendente | `#8A5A00` / `#FBE8C4` | Manutenção que vence em breve |
| Atrasada | `#A1281F` / `#F9DEDA` | Prazo vencido, ações de exclusão |

Fonte: Manrope (Google Fonts). Base: Material 3 / Jetpack Compose.

## Paleta escura

Variante derivada da Musgo para o tema escuro do app (ainda não desenhada no canvas). Todos os pares de texto e fundo têm contraste mínimo de 4,5:1.

| Token | Cor |
|---|---|
| Primária | `#8FD3A8` (texto sobre ela: `#0F2F1E`) |
| Container primário | `#1F4331` (texto: `#CDE6D3`) |
| Fundo | `#101611` |
| Superfície | `#17201A` |
| Superfície variante / container secundário | `#26332A` |
| Texto | `#E1E7DF` |
| Texto secundário | `#B4C0B5` |
| Contorno | `#6B7A6F` |
| Água | `#9CC4E8` / `#1E3A55` |
| Pendente | `#F2C46E` / `#4A3300` |
| Erro / atrasada | `#FFB4AB` / `#5C1A14` |

Os valores de código ficam em `mobile/app/.../core/designsystem/Color.kt`.

## Sincronização

O canvas e esta pasta devem andar juntos: ao alterar o canvas, copie os
arquivos para cá e faça commit; ao alterar os arquivos daqui, publique-os de
volta no mesmo link.
