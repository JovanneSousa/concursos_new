package com.concurso.monitor.infrastructure.adapters.input.scraper;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "scraper.ibfc.enabled", havingValue = "true", matchIfMissing = true)
public class IbfcScraperAdapter implements ScraperInputPort {

    private static final Logger log = LoggerFactory.getLogger(IbfcScraperAdapter.class);

    private static final String BASE_URL = "https://concursos.ibfc.org.br";
    private static final String URL_ABERTOS = "https://concursos.ibfc.org.br/index/abertos/";

    @Override
    public String identificador() {
        return "IBFC";
    }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();
        try {
            log.info("[IBFC] Iniciando scraping em {}", URL_ABERTOS);
            Document doc = Jsoup.connect(URL_ABERTOS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .timeout(15_000)
                    .get();

            Elements itens = doc.select("a.lista-concurso, a[href*='/informacoes/']");
            for (Element item : itens) {
                String href = item.attr("href");
                if (href == null || href.isBlank() || !href.contains("/informacoes/")) {
                    continue;
                }

                String linkCompleto = href.startsWith("http") ? href : BASE_URL + href;

                Element textoClienteEl = item.selectFirst(".texto_cliente");
                Element textoEditalEl = item.selectFirst(".texto_edital");

                String titulo = "";
                if (textoClienteEl != null) {
                    titulo = textoClienteEl.text().trim();
                }
                if (titulo.isBlank()) {
                    titulo = item.text().trim();
                }

                if (textoEditalEl != null) {
                    String editalInfo = textoEditalEl.text().trim();
                    if (!editalInfo.isBlank() && !titulo.contains(editalInfo)) {
                        titulo = titulo + " (" + editalInfo + ")";
                    }
                }

                if (titulo.isBlank()) {
                    continue;
                }

                // Evita duplicatas dentro da mesma página
                String hash = Concurso.gerarHash("IBFC", linkCompleto);
                boolean jaAdicionado = resultado.stream().anyMatch(c -> c.hashDeduplicacao().equals(hash));
                if (!jaAdicionado) {
                    resultado.add(new Concurso(
                            null,
                            titulo,
                            "IBFC",
                            linkCompleto,
                            "Nacional",
                            LocalDateTime.now(),
                            "IBFC",
                            hash
                    ));
                }
            }

            log.info("[IBFC] {} concurso(s) encontrado(s).", resultado.size());
        } catch (Exception e) {
            log.error("[IBFC] Erro ao raspar: {}", e.getMessage(), e);
        }
        return resultado;
    }
}
