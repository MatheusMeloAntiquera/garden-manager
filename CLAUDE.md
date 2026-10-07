# Garden Manager

## Decisões

- Quando surgir uma decisão que o pedido não cobre, pergunte antes de implementar, mesmo em modo automático. Exemplos: regra de negócio, comportamento visível na API, campo ou coluna nova, código de status HTTP, valor padrão ou herança de dados, restrição de edição ou exclusão.
- Agrupe as dúvidas numa única pergunta, com uma opção recomendada, antes de começar a codar.
- Detalhes de implementação que seguem os padrões do código (nomes, estrutura de pacotes, queries, testes, documentação) podem ser feitos sem perguntar.

## Entidades do domínio (backend)

- As entidades em `backend/domain` têm só os dados do domínio. Não adicione nelas campos criados em tempo de execução ou calculados no SELECT da query, como contagens, somas ou status derivados, mesmo que uma tela ou endpoint precise deles.
- Exceção: referências a chaves estrangeiras podem vir hidratadas na entidade. Exemplo: `Species` e `Environment` em `domain.Plant`, preenchidos pelo join de `species_id` e `environment_id`.
- Quando uma leitura precisar de um dado calculado, crie um modelo de leitura no pacote da feature que embute a entidade e acrescenta o campo. O exemplo é `environment.EnvironmentView`, que embute `domain.Environment` e acrescenta `PlantCount`.
  - Os métodos de escrita do repositório (`Create`, `Update`) e o `FindByID` usado por outros pacotes continuam com a entidade.
  - As leituras para a API (por exemplo `FindViewByID` e `List`) devolvem o modelo de leitura, com a subquery na lista de colunas e um scan próprio.
  - O service monta a resposta a partir do modelo de leitura. Depois de uma escrita, ele relê pelo método de leitura para responder com o dado calculado.

## Design do mobile

- Antes de commitar qualquer alteração em `mobile/`, compare o design do canvas (cópia versionada em `mobile/design/`) com o código do app: telas, textos, componentes, estados e fluxos.
- Se houver diferença, ajuste o canvas para refletir o app e atualize `mobile/design/` junto. O link do canvas e a regra de sincronização estão em `mobile/design/README.md`: o canvas publicado e a pasta andam sempre juntos.
- Telas e elementos de fases futuras, ainda sem implementação no app, ficam no canvas como design futuro e não contam como diferença.
- Faça os ajustes no mesmo trabalho que gerou a diferença, antes do commit.
