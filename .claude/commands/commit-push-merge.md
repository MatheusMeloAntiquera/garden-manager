---
description: Commit, push do branch, merge no master e exclusão do branch, com confirmação antes de cada etapa ou tudo de uma vez
argument-hint: "[dica opcional para a mensagem do commit]"
allowed-tools: Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git branch:*), Bash(git rev-parse:*), Bash(git add:*), Bash(git commit:*), Bash(git push:*), Bash(git checkout:*), Bash(git pull:*), Bash(git merge:*), Bash(git fetch:*), AskUserQuestion
---

Conduza o fluxo abaixo **uma etapa por vez**, no modo escolhido na etapa "Modo de execução":

- **Confirmar cada etapa:** antes de cada etapa, pergunte ao usuário com a ferramenta AskUserQuestion, com as opções "Sim" e "Não". Se a resposta for "Não" (ou qualquer coisa que não seja um "sim" claro), **pare imediatamente**: não execute mais nada e informe em uma linha até onde o fluxo chegou.
- **Automático:** não faça as perguntas das etapas 1 a 4; execute cada etapa como se a resposta fosse "Sim".

Nos dois modos, as paradas de segurança continuam valendo: branch `master`, nada a fazer, push rejeitado, `pull --ff-only` falhando e conflito no merge sempre interrompem o fluxo, com a explicação do problema.

Dica do usuário para a mensagem do commit (pode estar vazia): $ARGUMENTS

## 0. Verificações (sem perguntar nada)

1. Rode `git branch --show-current`, `git status --short` e `git log --oneline -5`.
2. Se o branch atual for `master`, **pare**: explique que o fluxo precisa de um branch de trabalho e não faça nada.
3. Se não houver alterações nem commits à frente de `origin/master`, avise que não há nada a fazer e pare.

## Modo de execução

1. Se houver alterações não commitadas, mostre antes um resumo (`git status --short` e `git diff --stat`) e a mensagem de commit proposta (regras na etapa 1), para que o usuário decida já sabendo o que será commitado.
2. Pergunte: **"Executar todas as etapas sem confirmar (commit, push do branch, merge e push do master, e exclusão do branch local e remoto)?"**, com as opções "Sim" (modo automático) e "Não" (confirmar cada etapa).

## 1. Commit

1. Se houver alterações não commitadas, proponha a mensagem de commit (se já mostrada no "Modo de execução", use a mesma):
   - padrão do repositório: Conventional Commits em português, com escopo (ex.: `feat(backend): adiciona cadastro de ambientes`), corpo explicando o quê e o porquê;
   - use a dica em `$ARGUMENTS`, se houver;
   - termine a mensagem com as linhas de atribuição exigidas pela sessão.
2. Pergunte: **"Quer commitar estas alterações com esta mensagem?"**
   - Não → pare.
   - Sim → `git add -A` e `git commit`. Confira com `git log --oneline -1`.
3. Se não houver alterações pendentes, mas o branch tiver commits ainda não mesclados, diga isso e pule para a etapa 2 (sem perguntar sobre commit).

## 2. Push do branch

1. Pergunte: **"Quer fazer o push do branch `<branch>` para o origin?"**
   - Não → pare.
   - Sim → `git push -u origin <branch>`. Se o push for rejeitado, mostre o erro e pare (nunca use `--force`).

## 3. Merge no master

1. Pergunte: **"Quer fazer o merge de `<branch>` no master e o push do master?"**
   - Não → pare.
   - Sim:
     1. `git checkout master`
     2. `git pull --ff-only origin master` (se falhar, pare e explique)
     3. `git merge --no-ff --no-edit <branch>` — mesmo padrão dos merges anteriores (`Merge branch '<branch>'`). Se houver conflito, **não resolva sozinho**: rode `git merge --abort`, volte para `<branch>` e explique os arquivos em conflito.
     4. `git push origin master`
     5. Confira com `git log --oneline -3` e `git status -sb`.

## 4. Apagar o branch

Só chegue aqui se o merge e o push do master da etapa 3 deram certo. Nesta etapa, um "Não" pula apenas aquela exclusão (não encerra o fluxo): as duas perguntas são independentes. No modo automático, os dois branches são apagados.

1. Pergunte: **"Quer apagar o branch local `<branch>`?"**
   - Não → mantenha o branch local e siga para a próxima pergunta.
   - Sim → `git branch -d <branch>` (nunca `-D`). Se o git recusar, mostre o erro e mantenha o branch.
2. Pergunte: **"Quer apagar o branch remoto `origin/<branch>`?"**
   - Não → mantenha o branch remoto.
   - Sim → `git push origin --delete <branch>`. Se falhar, mostre o erro.

## 5. Resumo

Informe em poucas linhas o que foi feito (modo usado, hash do commit, push do branch, hash do merge, push do master e quais branches foram apagados ou mantidos).
