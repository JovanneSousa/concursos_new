package com.concurso.monitor.infrastructure.scheduler;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ProcessarNovosConcursosUseCase;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@EnableScheduling
public class ScrapingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScrapingScheduler.class);

    private final List<ScraperInputPort> scrapers;
    private final ProcessarNovosConcursosUseCase useCase;

    @Value("${scraper.executar-no-startup:false}")
    private boolean executarNoStartup;

    public ScrapingScheduler(List<ScraperInputPort> scrapers,
                             ProcessarNovosConcursosUseCase useCase) {
        this.scrapers = scrapers;
        this.useCase = useCase;
        log.info("ScrapingScheduler inicializado com {} scraper(s) ativos.", scrapers.size());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (executarNoStartup) {
            log.info("Disparo imediato configurado (scraper.executar-no-startup=true). Iniciando scraping...");
            executarTodos();
        }
    }

    /**
     * Executa todos os scrapers na frequência configurada em application.yml.
     * Valor padrão: a cada 3 horas.
     */
    @Scheduled(cron = "${scraper.cron:0 0 */3 * * *}")
    public void executarTodos() {
        log.info("=== Ciclo de scraping iniciado ===");
        List<Concurso> todos = new ArrayList<>();

        for (ScraperInputPort scraper : scrapers) {
            try {
                log.info("Executando scraper: [{}]", scraper.identificador());
                List<Concurso> encontrados = scraper.raspar();
                todos.addAll(encontrados);
                log.info("[{}] → {} candidato(s) retornado(s).", scraper.identificador(), encontrados.size());
            } catch (Exception e) {
                log.error("Erro inesperado no scraper [{}]: {}", scraper.identificador(), e.getMessage(), e);
            }
        }

        log.info("Total de candidatos coletados: {}. Iniciando processamento...", todos.size());
        useCase.executar(todos);
        log.info("=== Ciclo de scraping concluído ===");
    }
}
