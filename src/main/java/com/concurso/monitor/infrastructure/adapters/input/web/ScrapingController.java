package com.concurso.monitor.infrastructure.adapters.input.web;

import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import com.concurso.monitor.infrastructure.persistence.ConcursoEntity;
import com.concurso.monitor.infrastructure.persistence.ConcursoJpaRepository;
import com.concurso.monitor.infrastructure.scheduler.ScrapingScheduler;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
public class ScrapingController {

    private final ScrapingScheduler scrapingScheduler;
    private final ConcursoJpaRepository concursoJpaRepository;
    private final List<ScraperInputPort> scrapers;

    public ScrapingController(ScrapingScheduler scrapingScheduler,
                              ConcursoJpaRepository concursoJpaRepository,
                              List<ScraperInputPort> scrapers) {
        this.scrapingScheduler = scrapingScheduler;
        this.concursoJpaRepository = concursoJpaRepository;
        this.scrapers = scrapers;
    }

    /**
     * Dispara um ciclo manual de scraping em segundo plano.
     */
    @PostMapping("/scraping/executar")
    public ResponseEntity<Map<String, Object>> dispararScrapingManual() {
        CompletableFuture.runAsync(scrapingScheduler::executarTodos);

        return ResponseEntity.accepted().body(Map.of(
                "status", "INICIADO",
                "mensagem", "Ciclo de scraping disparado em segundo plano. Verifique os logs e o endpoint /api/v1/concursos.",
                "scrapersAtivos", scrapers.stream().map(ScraperInputPort::identificador).toList()
        ));
    }

    /**
     * Retorna a lista de scrapers registrados e ativos na aplicação.
     */
    @GetMapping("/scrapers")
    public ResponseEntity<Map<String, Object>> listarScrapers() {
        List<String> ativos = scrapers.stream()
                .map(ScraperInputPort::identificador)
                .toList();

        return ResponseEntity.ok(Map.of(
                "totalAtivos", ativos.size(),
                "scrapers", ativos
        ));
    }

    /**
     * Retorna todos os concursos armazenados no banco, ordenados do mais recente para o mais antigo.
     * Suporta filtros por fonte/banca, região, prioridade e se é vaga de tecnologia.
     */
    @GetMapping("/concursos")
    public ResponseEntity<List<ConcursoEntity>> listarConcursos(
            @RequestParam(required = false) String fonte,
            @RequestParam(required = false) String regiao,
            @RequestParam(required = false) Boolean prioritario,
            @RequestParam(required = false) Boolean tecnologia
    ) {
        List<ConcursoEntity> concursos = concursoJpaRepository.findAll(Sort.by(Sort.Direction.DESC, "dataCriacao"));

        if (fonte != null && !fonte.isBlank()) {
            concursos = concursos.stream()
                    .filter(c -> fonte.equalsIgnoreCase(c.getFonte()))
                    .toList();
        }

        if (regiao != null && !regiao.isBlank()) {
            concursos = concursos.stream()
                    .filter(c -> c.getRegiao() != null && c.getRegiao().toLowerCase().contains(regiao.toLowerCase()))
                    .toList();
        }

        if (prioritario != null) {
            concursos = concursos.stream()
                    .filter(c -> c.isPrioritario() == prioritario)
                    .toList();
        }

        if (tecnologia != null) {
            concursos = concursos.stream()
                    .filter(c -> c.isTecnologia() == tecnologia)
                    .toList();
        }

        return ResponseEntity.ok(concursos);
    }
}
