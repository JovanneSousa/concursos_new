package com.concurso.monitor.infrastructure.adapters.input.scraper;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import com.concurso.monitor.domain.service.FiltroTecnologiaService;
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
import java.util.Objects;

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

    private static final String EVENTO_API_URL = "https://apis.cebraspe.org.br/cebraspe/eventos/";

    private record CargoDto(String area) {}

    private record EventoDetalheDto(List<CargoDto> eventoCargos) {}

    private final RestClient restClient;
    private final FiltroTecnologiaService filtroTecnologiaService;

    public CebraspeScraperAdapter(RestClient.Builder restClientBuilder,
                                  FiltroTecnologiaService filtroTecnologiaService) {
        restClient = restClientBuilder.build();
        this.filtroTecnologiaService = filtroTecnologiaService;
    }

    /**
     * Um concurso do Cebraspe é de TI se o título OU algum dos cargos listados
     * na página do concurso (seção "Cargos") for de Tecnologia da Informação.
     */
    @Override
    public boolean ehTecnologia(Concurso concurso) {
        if (filtroTecnologiaService.isTecnologia(concurso.titulo(), null)) {
            return true;
        }
        String slug = concurso.linkEdital().substring(BASE_URL.length());
        return buscarCargos(slug).stream()
                .anyMatch(cargo -> filtroTecnologiaService.isTecnologia(cargo, null));
    }

    private List<String> buscarCargos(String slug) {
        try {
            EventoDetalheDto detalhe = restClient
                    .get()
                    .uri(EVENTO_API_URL + slug)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .retrieve()
                    .body(EventoDetalheDto.class);

            if (detalhe == null || detalhe.eventoCargos() == null) return Collections.emptyList();
            return detalhe.eventoCargos().stream()
                    .map(CargoDto::area)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("[CEBRASPE] Falha ao buscar cargos de '{}': {}", slug, e.getMessage());
            return Collections.emptyList();
        }
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

            // Retorna somente os concursos de TI (título ou cargos)
            eventos.stream()
                    .filter(this::ehTecnologia)
                    .map(c -> c.comTecnologia(true))
                    .forEach(resultado::add);

            log.info("[CEBRASPE] {} concurso(s) analisado(s), {} de TI.", eventos.size(), resultado.size());
        } catch (Exception e) {
            log.error("[CEBRASPE] Erro ao raspar: {}", e.getMessage(), e);
        }
        return resultado;
    }
}
