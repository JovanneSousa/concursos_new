package com.concurso.monitor.infrastructure.adapters.input.scraper;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "scraper.cebraspe.enabled", havingValue = "true", matchIfMissing = true)
public class CebraspeScraperAdapter implements ScraperInputPort {
    private record CebraspeEventoDto(String eventoNomeAbreviado, String eventoURL, String eventoStatus) {
        private Concurso toConcurso() {
            String linkCompleto = BASE_URL + this.eventoURL;
            return new Concurso(
                    null,
                    this.eventoNomeAbreviado.trim(),
                    "Cebraspe",
                    linkCompleto,
                    "Nacional",
                    LocalDateTime.now(),
                    "CEBRASPE",
                    Concurso.gerarHash("CEBRASPE", linkCompleto)
            );
        }
    }

    private record Fase(String faseEvento, List<CebraspeEventoDto> eventos) {
        private boolean novo() {
            return "Inscrições Abertas".equals(this.faseEvento()) || "Novos".equals(this.faseEvento());
        }
    }

    private static final Logger log = LoggerFactory.getLogger(CebraspeScraperAdapter.class);
    private static final String BASE_URL = "https://www.cebraspe.org.br/concursos/";
    private static final String API_URL = "https://apis.cebraspe.org.br/cebraspe/eventos/tipo/concursos/";

    private final RestClient restClient;

    public CebraspeScraperAdapter(RestClient.Builder restClientBuilder) {
        restClient = restClientBuilder.build();
    }

    @Override
    public String identificador() { return "CEBRASPE"; }

    private String montarRequestUrl() {
        return API_URL + "?_=" + System.currentTimeMillis();
    }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();
        try {
            var requestUrl = montarRequestUrl();
            log.info("[CEBRASPE] Iniciando scraping em {}", requestUrl);
            List<Fase> fase = restClient
                    .get()
                    .uri(requestUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Fase>>() {});

            if(fase == null || fase.isEmpty()) return Collections.emptyList();

            List<Concurso> eventos = fase.stream()
                    .filter(Fase::novo)
                    .filter(f -> f.eventos() != null)
                    .flatMap(f -> f.eventos.stream())
                    .map(CebraspeEventoDto::toConcurso)
                    .toList();

            resultado.addAll(eventos);

            log.info("[CEBRASPE] {} concurso(s) encontrado(s).", resultado.size());
        } catch (Exception e) {
            log.error("[CEBRASPE] Erro ao raspar: {}", e.getMessage(), e);
        }
        return resultado;
    }
}
