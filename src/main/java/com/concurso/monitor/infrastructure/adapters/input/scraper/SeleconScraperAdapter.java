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
@ConditionalOnProperty(name = "scraper.selecon.enabled", havingValue = "true", matchIfMissing = true)
public class SeleconScraperAdapter implements ScraperInputPort {

    private static final Logger log = LoggerFactory.getLogger(SeleconScraperAdapter.class);
    private static final String URL = "https://selecon.org.br/concursos/";

    @Override
    public String identificador() {
        return "SELECON";
    }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();
        try {
            log.info("[SELECON] Iniciando scraping em {}", URL);
            Document doc = Jsoup.connect(URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .timeout(15_000)
                    .get();

            Elements cards = doc.select("div.jet-listing-grid__item");
            for (Element card : cards) {
                // Localiza o botão/link para a página de detalhes do concurso
                Element linkEl = card.selectFirst("a.elementor-button, a[href*='informacoes'], a[href*='selecao.net.br'], a[href*='selecon']");
                if (linkEl == null) {
                    continue;
                }

                String linkCompleto = linkEl.attr("abs:href");
                if (linkCompleto.isBlank() || linkCompleto.equals(URL)) {
                    continue;
                }

                String titulo = "";
                String edital = "";
                String uf = "MT"; // Selecon atua fortemente no MT

                Elements campos = card.select(".jet-listing-dynamic-field__content");
                for (Element campo : campos) {
                    String texto = campo.text().trim();
                    if (texto.toLowerCase().startsWith("edital:")) {
                        edital = texto.replaceFirst("(?i)edital:", "").trim();
                    } else if (texto.toLowerCase().startsWith("uf:")) {
                        uf = texto.replaceFirst("(?i)uf:", "").trim().toUpperCase();
                    } else if (texto.toLowerCase().contains("concurso")
                            || texto.toLowerCase().contains("prefeitura")
                            || texto.toLowerCase().contains("câmara")
                            || texto.toLowerCase().contains("camara")
                            || texto.toLowerCase().contains("processo seletivo")
                            || texto.toLowerCase().contains("secretaria")) {
                        if (titulo.isBlank() || titulo.length() < texto.length()) {
                            titulo = texto;
                        }
                    }
                }

                if (titulo.isBlank() && !campos.isEmpty()) {
                    titulo = campos.get(0).text().trim();
                }

                if (titulo.isBlank()) {
                    continue;
                }

                if (!edital.isBlank() && !titulo.contains(edital)) {
                    titulo = titulo + " (Edital " + edital + ")";
                }

                String hash = Concurso.gerarHash("SELECON", linkCompleto);
                boolean jaAdicionado = resultado.stream().anyMatch(c -> c.hashDeduplicacao().equals(hash));
                if (!jaAdicionado) {
                    resultado.add(new Concurso(
                            null,
                            titulo,
                            "Instituto Selecon",
                            linkCompleto,
                            uf,
                            LocalDateTime.now(),
                            "SELECON",
                            hash
                    ));
                }
            }

            log.info("[SELECON] {} concurso(s) encontrado(s).", resultado.size());
        } catch (Exception e) {
            log.error("[SELECON] Erro ao raspar: {}", e.getMessage(), e);
        }
        return resultado;
    }
}
