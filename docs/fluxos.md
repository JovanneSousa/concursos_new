# Fluxos do sistema

## 1. Ciclo de scraping (fluxo principal)
```mermaid
sequenceDiagram
    participant T as Gatilho (Cron / Startup / REST)
    participant S as ScrapingScheduler
    participant SC as ScraperInputPort[*]
    participant UC as ProcessarNovosConcursosUseCase
    participant DB as ConcursoRepository
    participant N as NotificacaoOutputPort (Composite)
    T->>S: executarTodos()
    loop cada scraper habilitado
        S->>SC: raspar()
        SC-->>S: List<Concurso> (ou exceção logada e ignorada)
    end
    S->>UC: executar(todos)
    UC->>UC: classifica prioritario + tecnologia, ordena prioritários primeiro
    loop cada concurso
        UC->>DB: existePorHash(hash)
        alt já existe
            UC->>UC: ignora
        else novo
            UC->>DB: salvar()
            opt tecnologia == true
                UC->>UC: adiciona ao lote de notificação
            end
        end
    end
    UC->>N: enviarNotificacoes(lote)
```

## 2. Gatilhos
| Gatilho | Onde | Detalhe |
|---|---|---|
| Cron | `@Scheduled(cron="${scraper.cron}")` | default a cada 3h (app contínuo) |
| Startup | `@EventListener(ApplicationReadyEvent)` | se `scraper.executar-no-startup=true` |
| Encerramento | mesmo listener | se `scraper.finalizar-apos-execucao=true` → `SpringApplication.exit` + `System.exit` |
| REST | `POST /api/v1/scraping/executar` | dispara em background |
| GitHub Actions | `maven-publish.yml` | cron 6h UTC, push em `main`, manual; usa startup+finalizar |

## 3. Modo GitHub Actions (produção)
```mermaid
flowchart LR
    A[cron 6h / push main / manual] --> B[checkout + JDK 21]
    B --> C["mvn clean package -DskipTests"]
    C --> D["java -jar target/*.jar (secrets como env)"]
    D --> E[Ready → 1 ciclo de scraping]
    E --> F[System.exit]
```

## 4. Classificação de um concurso
```mermaid
flowchart TD
    C[Concurso candidato] --> P{"prioritario já true<br/>ou UF ∈ estados prioritários?"}
    P --> TI{"tecnologia já true<br/>ou FiltroTecnologiaService.isTecnologia(titulo, regiao)?"}
    TI --> H{hash existe no banco?}
    H -- sim --> X[ignora]
    H -- não --> S[salva]
    S --> T{tecnologia?}
    T -- sim --> N[notifica]
    T -- não --> A[apenas arquivado]
```
Identificação de UF (`EstadoPrioritarioService.identificarUf`): região de 2 letras → nome por extenso (maior primeiro) → sigla isolada com regex `\b`.

## 5. Notificação
```mermaid
flowchart TD
    UC[Use case] --> CP[CompositeNotificationAdapter @Primary]
    CP -->|try/catch por canal| EM[EmailNotificationAdapter]
    CP -->|try/catch por canal| TG[TelegramNotificationAdapter]
    EM --> E1["E-mail 1: prioritários"]
    EM --> E2["E-mail 2: demais UFs/nacional"]
    TG --> M["Mensagem individual [CONCURSO TI]"]
```
- E-mail: HTML agrupado por UF → banca, com fallback texto. Máx. 2 e-mails por ciclo.
- Composite exclui a si mesmo da lista de adapters (evita recursão).

## 6. Scrapers por tipo
- **HTML (Jsoup):** FGV, Cebraspe, FCC, IBFC, Quadrix, Selecon, DOU (busca parametrizada por `keywords`).
- **PDF (PDFBox):** DOE-MT — baixa PDF diário do IOMAT, extrai texto, procura `keywords`.
- **Stub:** GazetaMt (não registrado como bean).
Contrato: `ScraperInputPort { String identificador(); List<Concurso> raspar(); }`.

## 7. Modelo de dados (`concurso`)
Campos: `id`(UUID/36), `titulo`(500), `banca`(200), `link_edital`(2000), `regiao`, `data_criacao`, `fonte`, `hash_deduplicacao` (único, usado para dedupe), `prioritario`, `tecnologia`. Ver `schema.sql` e `ConcursoEntity`.
