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

## Sincronização

O canvas e esta pasta devem andar juntos: ao alterar o canvas, copie os
arquivos para cá e faça commit; ao alterar os arquivos daqui, publique-os de
volta no mesmo link.
