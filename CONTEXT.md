# CONTEXT.md — Concursos News

> Ponto de entrada para agentes de IA e devs. Leia este arquivo ANTES de alterar qualquer código.
> Detalhes: [docs/fluxos.md](docs/fluxos.md) · [docs/git-workflow.md](docs/git-workflow.md) · [README.md](README.md) (setup/env vars para humanos).

## 1. O que é
Monitor de editais de concursos públicos. Raspa sites de bancas e diários oficiais, deduplica no PostgreSQL, filtra por Tecnologia da Informação (TI) e notifica por e-mail (HTML agrupado) e Telegram.

## 2. Stack
Java 21 · Spring Boot 3.3.5 · Maven · Spring Data JPA + PostgreSQL · Jsoup 1.17.2 · Apache PDFBox 3.0.2 · JavaMailSender · Telegram Bot API (RestClient) · JUnit 5 + H2 (testes).

## 3. Arquitetura (Hexagonal / Ports & Adapters)
Pacote raiz: `com.concurso.monitor`

| Camada | Caminho | Conteúdo |
|---|---|---|
| Domínio | `domain/model` | `Concurso` (record imutável, `gerarHash`, `comPrioridade`, `comTecnologia`) |
| Portas de entrada | `domain/ports/in` | `ScraperInputPort` (interface), `ProcessarNovosConcursosUseCase` (**caso de uso, é um `@Service`**) |
| Portas de saída | `domain/ports/out` | `ConcursoRepository`, `NotificacaoOutputPort` |
| Serviços de domínio | `domain/service` | `EstadoPrioritarioService` (UF/prioridade), `FiltroTecnologiaService` (é TI?) |
| Adapters de entrada | `infrastructure/adapters/input/scraper` | 1 classe por fonte (`*ScraperAdapter`) |
| | `infrastructure/adapters/input/web` | `ScrapingController` (REST) |
| | `infrastructure/scheduler` | `ScrapingScheduler` (cron/startup) |
| Adapters de saída | `infrastructure/adapters/output/notification` | `CompositeNotificationAdapter` (`@Primary`), `Email…`, `Telegram…` |
| | `infrastructure/persistence` | `ConcursoEntity`, `ConcursoJpaRepository`, `ConcursoRepositoryAdapter` |

**Regra de dependência:** `domain` NÃO importa nada de `infrastructure`. Infra depende do domínio, nunca o contrário.

## 4. Fluxo principal (resumo)
```
Scheduler (cron | startup | POST /api/v1/scraping/executar)
  → para cada ScraperInputPort habilitado: raspar() → List<Concurso>   (erro em um scraper não derruba os outros)
  → ProcessarNovosConcursosUseCase.executar(todos)
      1. classifica prioritario (UF) e tecnologia (TI); ordena prioritários primeiro
      2. existePorHash(MD5(fonte::url))? sim → ignora
      3. não → salva SEMPRE no banco; se tecnologia==true → entra no lote de notificação
      4. NotificacaoOutputPort.enviarNotificacoes(lote) → Composite → Email + Telegram
```
Diagramas e detalhes por scraper em [docs/fluxos.md](docs/fluxos.md).

## 5. Fontes (scrapers)
| Identificador | Classe | Tipo | Flag `scraper.<x>.enabled` |
|---|---|---|---|
| FGV | `FgvScraperAdapter` | HTML | `fgv` |
| Cebraspe | `CebraspeScraperAdapter` | HTML | `cebraspe` |
| FCC | `FccScraperAdapter` | HTML | `fcc` |
| IBFC | `IbfcScraperAdapter` | HTML | `ibfc` |
| Quadrix | `QuadrixScraperAdapter` | HTML | `quadrix` |
| Selecon | `SeleconScraperAdapter` | HTML | `selecon` |
| DOU | `DouScraperAdapter` | HTML (busca in.gov.br, `keywords`) | `dou` |
| DOE-MT | `DoeMtScraperAdapter` | PDF (PDFBox, IOMAT, `keywords`) | `doe-mt` |
| GazetaMt | `GazetaMtScraperAdapter` | **STUB** — sem `@Component`, retorna lista vazia, não é executado | — |

Todos usam `@Component` + `@ConditionalOnProperty(name="scraper.<x>.enabled", havingValue="true", matchIfMissing=true)`.

### Como adicionar um scraper
1. Criar `XxxScraperAdapter implements ScraperInputPort` em `adapters/input/scraper` com `@Component` + `@ConditionalOnProperty`.
2. `identificador()` retorna nome único; `raspar()` retorna `List<Concurso>` com `hashDeduplicacao = Concurso.gerarHash(fonte, url)`.
3. Adicionar `scraper.xxx.enabled: ${SCRAPER_XXX_ENABLED:true}` em `application.yml`.
4. Atualizar tabela acima, README e `docs/fluxos.md`. Nenhuma outra classe precisa mudar (o scheduler injeta `List<ScraperInputPort>`).

## 6. Regras de negócio críticas (NÃO quebrar)
- **Deduplicação:** hash = `MD5(fonte + "::" + url)`. Mudar fonte/URL/algoritmo faz concursos antigos serem reenviados como novos.
- **Salvar tudo, notificar só TI:** concursos não-TI são persistidos (para não reprocessar) mas não notificam. Com `monitor.filtro-tecnologia.habilitado=false` a lógica do filtro deve ser revisada no use case antes de assumir comportamento.
- **Prioridade:** `EstadoPrioritarioService.identificarUf` — 1) região com 2 letras, 2) nome por extenso (ordenado por tamanho: "MATO GROSSO DO SUL" antes de "MATO GROSSO"), 3) sigla isolada (`SE` exige contexto `- SE`, `/SE`, `(SE)`). Default: `MT,SC,PR,DF,GO`.
- **Notificação resiliente:** `CompositeNotificationAdapter` captura exceção por canal; falha de e-mail não impede Telegram. Máx. 2 e-mails por ciclo (prioritários / demais).
- **Erro isolado:** exceção em um scraper ou em um concurso é logada e o ciclo continua.

## 7. Configuração (`src/main/resources/application.yml`)
Tudo via env var com default. Principais:
`DATABASE_URL/USER/PASSWORD`, `EMAIL_HOST/PORT/USERNAME/PASSWORD`, `NOTIFICATION_EMAIL_TO`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `MONITOR_ESTADOS_PRIORITARIOS`, `MONITOR_FILTRO_TI_HABILITADO`, `MONITOR_TERMOS_TI_CUSTOMIZADOS`, `SCRAPER_CRON` (default `0 0 */3 * * *`), `SCRAPER_EXECUTE_ON_STARTUP` (default false), `SCRAPER_FINALIZAR_APOS_EXECUCAO` (default false), `SCRAPER_<FONTE>_ENABLED`, `SCRAPER_DOU_KEYWORDS`, `SCRAPER_DOE_MT_KEYWORDS`.
Schema: `ddl-auto: update` (Hibernate); `schema.sql` é apenas referência. **Não há migrations** — mudanças em `ConcursoEntity` afetam o banco de produção.

## 8. Modos de execução
| Modo | Como |
|---|---|
| Local contínuo | `mvn spring-boot:run` (cron interno) |
| GitHub Actions (produção atual) | `.github/workflows/maven-publish.yml`: cron `0 */6 * * *` UTC + `push` em `main` + manual; builda com `-DskipTests`, roda `java -jar` com `SCRAPER_EXECUTE_ON_STARTUP=true` e `SCRAPER_FINALIZAR_APOS_EXECUCAO=true` (o app executa 1 ciclo e encerra via `System.exit`). Secrets no repositório. |
| API manual | `POST /api/v1/scraping/executar` (async) |

> ⚠️ Cada push/merge em `main` dispara um ciclo **real** (banco de produção + e-mails/Telegram). Por isso `main` só recebe merge de `preview`.

## 9. API REST (`ScrapingController`, base `/api/v1`)
`POST /scraping/executar` · `GET /scrapers` · `GET /concursos?fonte=&regiao=&prioritario=&tecnologia=` (filtros em memória sobre `findAll`; retorna `ConcursoEntity` direto — sem DTO, sem paginação).

## 10. Testes
`mvn test` (perfil `application-test.yml`, H2). Cobertos: use case, `EstadoPrioritarioService`, `FiltroTecnologiaService`, Composite/Email/Telegram. **Scrapers NÃO têm testes** (dependem de sites externos) e o controller também não.
Convenções: JUnit 5 + Mockito; nome `<Classe>Test`; espelhar o pacote de produção.

## 11. Armadilhas conhecidas / dívida técnica
- Scrapers quebram quando o HTML do site muda (histórico: fix FCC, Cebraspe, DOE-MT). Ao corrigir, validar seletor contra o HTML atual e logar quantidade de candidatos.
- `GazetaMtScraperAdapter` é stub incompleto.
- README cita 8 fontes ativas; o diagrama Mermaid não inclui Selecon.
- Controller acessa `ConcursoJpaRepository` diretamente (viola a hexagonal) e filtra em memória.
- `ScrapingScheduler` mistura startup-run + `System.exit` (necessário para o GitHub Actions); não remover sem ajustar o workflow.
- Workflow não roda testes (`-DskipTests`) e só dispara em `main`.
- `Concurso` tem construtores sobrecarregados (8, 9 e 10 args) — prefira o canônico.
- Arquivos em UTF-8; textos/logs em português. Em PowerShell use `-Encoding UTF8` ao ler.

## 12. Convenções para agentes de IA
- Idioma: código (classes/métodos/campos de domínio) e commits/PRs em **português**, seguindo o existente (`raspar`, `enviarNotificacoes`).
- Mudanças mínimas e focadas; não refatore fora do escopo.
- Não commitar segredos nem `target/`/`.idea/`.
- Antes de concluir: `mvn -B test` deve passar.
- Siga [docs/git-workflow.md](docs/git-workflow.md) (branch a partir de `preview`, PR para `preview`, **nunca** commitar/push direto em `main`).
- Ao mudar comportamento, atualize este arquivo, `docs/fluxos.md` e o README.

## 13. Onde olhar primeiro por tipo de tarefa
| Tarefa | Arquivos |
|---|---|
| Scraper quebrado | `adapters/input/scraper/<Fonte>ScraperAdapter.java` |
| Regra de TI | `FiltroTecnologiaService` + `FiltroTecnologiaServiceTest` |
| Regra de estado/prioridade | `EstadoPrioritarioService` + teste |
| Formato de e-mail / Telegram | `EmailNotificationAdapter`, `TelegramNotificationAdapter` |
| Duplicação / novos vs. conhecidos | `ProcessarNovosConcursosUseCase`, `Concurso.gerarHash` |
| Agendamento / execução no Actions | `ScrapingScheduler`, `maven-publish.yml`, `application.yml` |
| Endpoints | `ScrapingController` |
| Banco | `ConcursoEntity`, `ConcursoRepositoryAdapter`, `schema.sql` |
