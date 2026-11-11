<!--
Título do PR: <tipo>(<escopo>): <descrição>   ex.: fix(fcc): corrige seletor da listagem
Para preview → main use: release: <resumo>
Base: branches curtas → preview | somente preview → main
-->

## Resumo
<!-- Em 1-2 frases: o que este PR faz. -->

## Motivação / Contexto
<!-- Por que a mudança é necessária? Link de issue, erro observado em log, site que mudou etc. -->

## Mudanças
-
-

## Como testar
<!-- Comandos e resultado esperado. -->
```bash
mvn -B test
```

## Impacto
- [ ] Banco / `ConcursoEntity` (ddl-auto=update em produção)
- [ ] Deduplicação (hash fonte+url)
- [ ] Novas/alteradas variáveis de ambiente (documentadas no `application.yml`, README e secrets do workflow)
- [ ] Workflow GitHub Actions
- [ ] Formato das notificações (e-mail/Telegram)
- [ ] Nenhum dos anteriores

## Checklist
- [ ] Base correta (`preview`, ou `main` apenas se for `preview → main`)
- [ ] `mvn -B test` passa
- [ ] Sem segredos/credenciais no código
- [ ] `CONTEXT.md` / `docs/` / README atualizados, se aplicável
- [ ] Commits seguem Conventional Commits (ver `docs/git-workflow.md`)

## Issues relacionadas
<!-- Closes #123 -->
