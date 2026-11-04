package com.concurso.monitor.infrastructure.adapters.input.scraper;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "scraper.dou.enabled", havingValue = "true", matchIfMissing = true)
public class DouScraperAdapter implements ScraperInputPort {

    private static final Logger log = LoggerFactory.getLogger(DouScraperAdapter.class);
    private static final String BASE_URL = "https://www.in.gov.br/consulta/-/buscar/dou";

    @Value("${scraper.dou.keywords:Edital de Concurso Público}")
    private String keywords;

    @Override
    public String identificador() { return "DOU"; }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();
        try {
            String query = URLEncoder.encode("\"" + keywords, StandardCharsets.UTF_8);
            String url = BASE_URL + "?q=" + query + "&s=todos&exactDate=dia&sortType=0";

            log.info("[DOU] Buscando: {}", url);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10_000)
                    .get();

            // O DOU retorna resultados em elementos com classe .resultado ou article
            for (Element item : doc.select("article.resultado-item, div.resultado-item")) {
                Element linkEl = item.selectFirst("a[href]");
                Element titleEl = item.selectFirst("h5, h4, .title");

                if (linkEl == null) continue;

                String titulo = titleEl != null ? titleEl.text().trim() : linkEl.text().trim();
                String href = linkEl.attr("abs:href");

                if (titulo.isBlank() || href.isBlank()) continue;

                String hash = Concurso.gerarHash("DOU", href);
                resultado.add(new Concurso(null, titulo, "DOU", href,
                        "Nacional", LocalDateTime.now(), "DOU", hash));
            }
            log.info("[DOU] {} resultado(s) encontrado(s).", resultado.size());
        } catch (Exception e) {
            log.error("[DOU] Erro ao raspar: {}", e.getMessage(), e);
        }
        return resultado;
    }
}
