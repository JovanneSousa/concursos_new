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
@ConditionalOnProperty(name = "scraper.cebraspe.enabled", havingValue = "true", matchIfMissing = true)
public class CebraspeScraperAdapter implements ScraperInputPort {

    private static final Logger log = LoggerFactory.getLogger(CebraspeScraperAdapter.class);
    private static final String URL = "https://www.cebraspe.org.br/concursos";
    private static final String BASE_URL = "https://www.cebraspe.org.br";

    @Override
    public String identificador() { return "CEBRASPE"; }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();
        try {
            log.info("[CEBRASPE] Iniciando scraping em {}", URL);
            Document doc = Jsoup.connect(URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10_000)
                    .get();

            for (Element row : doc.select("div.views-row")) {
                Element link = row.selectFirst("a");
                if (link == null) continue;

                String titulo = link.text().trim();
                String href = link.attr("href");
                String linkCompleto = href.startsWith("http") ? href : BASE_URL + href;

                if (titulo.isBlank() || linkCompleto.isBlank()) continue;

                String hash = Concurso.gerarHash("CEBRASPE", linkCompleto);
                resultado.add(new Concurso(null, titulo, "Cebraspe", linkCompleto,
                        "Nacional", LocalDateTime.now(), "CEBRASPE", hash));
            }
            log.info("[CEBRASPE] {} concurso(s) encontrado(s).", resultado.size());
        } catch (Exception e) {
            log.error("[CEBRASPE] Erro ao raspar: {}", e.getMessage(), e);
        }
        return resultado;
    }
}
