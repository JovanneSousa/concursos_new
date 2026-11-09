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
@ConditionalOnProperty(name = "scraper.fcc.enabled", havingValue = "true", matchIfMissing = true)
public class FccScraperAdapter implements ScraperInputPort {

    private static final Logger log = LoggerFactory.getLogger(FccScraperAdapter.class);
    private static final String URL = "https://www.concursosfcc.com.br/";
    private static final String BASE_URL = "https://www.fcc.org.br";

    @Override
    public String identificador() { return "FCC"; }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();
        try {
            log.info("[FCC] Iniciando scraping em {}", URL);
            Document doc = Jsoup.connect(URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10_000)
                    .get();

            // 1. Encontra o cabeçalho "Inscrições abertas"
            Element cabecalhoInscricoes = doc.selectFirst("div.rotuloTopico1:contains(Inscrições abertas)");

            if (cabecalhoInscricoes != null) {
                // 2. Seleciona o div imediatamente após o cabeçalho que contém a lista
                Element containerLista = cabecalhoInscricoes.nextElementSibling();

                if (containerLista != null && containerLista.hasClass("textoPreto")) {
                    // 3. Itera sobre cada tag <a> dentro desse container
                    for (Element link : containerLista.select("a.textoPreto")) {
                        String titulo = link.text().trim();

                        // Busca o span com o cargo logo em seguida (opcional, pode ser anexado ao título)
                        Element spanCargo = link.nextElementSibling();
                        if (spanCargo != null && spanCargo.tagName().equals("br")) {
                            spanCargo = spanCargo.nextElementSibling();
                        }
                        if (spanCargo != null && spanCargo.hasClass("textoCinza")) {
                            titulo = titulo + " - " + spanCargo.text().trim();
                        }

                        String href = link.attr("href");
                        String linkCompleto = href.startsWith("http") ? href : BASE_URL + href;

                        if (titulo.isBlank() || linkCompleto.isBlank()) continue;

                        String hash = Concurso.gerarHash("FCC", linkCompleto);
                        resultado.add(new Concurso(null, titulo, "FCC", linkCompleto,
                                "Nacional", LocalDateTime.now(), "FCC", hash));
                    }
                }
            } else {
                log.warn("[FCC] Não foi encontrado o bloco de 'Inscrições abertas' na página.");
            }
            log.info("[FCC] {} concurso(s) encontrado(s).", resultado.size());
        } catch (Exception e) {
            log.error("[FCC] Erro ao raspar: {}", e.getMessage(), e);
        }
        return resultado;
    }
}
