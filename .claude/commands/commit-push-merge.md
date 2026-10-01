---
description: Commit, push do branch e merge no master, com confirmação antes de cada etapa
argument-hint: "[dica opcional para a mensagem do commit]"
allowed-tools: Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git branch:*), Bash(git rev-parse:*), Bash(git add:*), Bash(git commit:*), Bash(git push:*), Bash(git checkout:*), Bash(git pull:*), Bash(git merge:*), Bash(git fetch:*), AskUserQuestion
---

Conduza o fluxo abaixo **uma etapa por vez**. Antes de cada etapa, pergunte ao usuário com a ferramenta AskUserQuestion, com as opções "Sim" e "Não". Se a resposta for "Não" (ou qualquer coisa que não seja um "sim" claro), **pare imediatamente**: não execute mais nada e informe em uma linha até onde o fluxo chegou.

Dica do usuário para a mensagem do commit (pode estar vazia): $ARGUMENTS

## 0. Verificações (sem perguntar nada)

1. Rode `git branch --show-current`, `git status --short` e `git log --oneline -5`.
2. Se o branch atual for `master`, **pare**: explique que o fluxo precisa de um branch de trabalho e não faça nada.
3. Se não houver alterações nem commits à frente de `origin/master`, avise que não há nada a fazer e pare.

## 1. Commit

1. Se houver alterações não commitadas, mostre um resumo (`git status --short` e `git diff --stat`) e proponha a mensagem de commit:
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

## 4. Resumo

Informe em poucas linhas o que foi feito (hash do commit, push do branch, hash do merge e push do master). Não apague o branch; o usuário decide isso depois.