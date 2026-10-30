package com.concurso.monitor.infrastructure.adapters.input.scraper;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "scraper.fgv.enabled", havingValue = "true", matchIfMissing = true)
public class FgvScraperAdapter implements ScraperInputPort {

    private static final Logger log = LoggerFactory.getLogger(FgvScraperAdapter.class);
    private static final String URL = "https://conhecimento.fgv.br/concursos";
    private static final String BASE_URL = "https://conhecimento.fgv.br";

    @Override
    public String identificador() { return "FGV"; }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();
        try {
            log.info("[FGV] Iniciando scraping em {}", URL);
            Document doc = Jsoup.connect(URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10_000)
                    .get();

            for (Element row : doc.select("div.view-content div.views-row")) {
                Element link = row.selectFirst("h3.field-content a");
                if (link == null) continue;

                String titulo = link.text().trim();
                String href = link.attr("href");
                String linkCompleto = href.startsWith("http") ? href : BASE_URL + href;

                if (titulo.isBlank() || linkCompleto.isBlank()) continue;

                String hash = Concurso.gerarHash("FGV", linkCompleto);
                resultado.add(new Concurso(null, titulo, "FGV", linkCompleto,
                        "Nacional", LocalDateTime.now(), "FGV", hash));
            }
            log.info("[FGV] {} concurso(s) encontrado(s).", resultado.size());
        } catch (Exception e) {
            log.error("[FGV] Erro ao raspar: {}", e.getMessage(), e);
        }
        return resultado;
    }
}
