package com.concurso.monitor.infrastructure.adapters.input.scraper;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import com.concurso.monitor.domain.service.FiltroTecnologiaService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "scraper.quadrix.enabled", havingValue = "true", matchIfMissing = true)
public class QuadrixScraperAdapter implements ScraperInputPort {

    private static final Logger log = LoggerFactory.getLogger(QuadrixScraperAdapter.class);

    private static final String BASE_URL = "https://quadrix.org.br";
    private static final String URL_ABERTOS = "https://quadrix.org.br/index/abertos/";

    private final FiltroTecnologiaService filtroTecnologiaService;

    public QuadrixScraperAdapter(FiltroTecnologiaService filtroTecnologiaService) {
        this.filtroTecnologiaService = filtroTecnologiaService;
    }

    @Override
    public String identificador() {
        return "QUADRIX";
    }

    /**
     * Um concurso da Quadrix é de TI se o título OU algum dos cargos listados
     * na aba "Vagas" da página do concurso for de Tecnologia da Informação.
     */
    @Override
    public boolean ehTecnologia(Concurso concurso) {
        if (filtroTecnologiaService.isTecnologia(concurso.titulo(), null)) {
            return true;
        }
        try {
            return CargosSelecaoNetExtrator.buscar(concurso.linkEdital()).stream()
                    .anyMatch(cargo -> filtroTecnologiaService.isTecnologia(cargo, null));
        } catch (Exception e) {
            log.warn("[QUADRIX] Falha ao buscar cargos de '{}': {}", concurso.linkEdital(), e.getMessage());
            return false;
        }
    }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();
        Set<String> hashesVistos = new HashSet<>();
        int analisados = 0;
        try {
            log.info("[QUADRIX] Iniciando scraping em {}", URL_ABERTOS);
            Document doc = Jsoup.connect(URL_ABERTOS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .timeout(15_000)
                    .get();

            // Na página /index/abertos/ cada concurso fica em uma <li> com classe item ou tabela com td.col-2
            Elements itens = doc.select("li.item, div.dados, td.col-2");
            for (Element item : itens) {
                Element linkEl = item.selectFirst("h3 a, a[href*='/informacoes/']");
                if (linkEl == null) {
                    continue;
                }

                String titulo = linkEl.text().trim();
                String href = linkEl.attr("href");

                if (titulo.isBlank() || href.isBlank() || !href.contains("/informacoes/")) {
                    continue;
                }

                String linkCompleto = href.startsWith("http") ? href : BASE_URL + href;

                // Tenta extrair a região do título se houver sigla (ex: CRECI-ES, CRO-PR, etc.)
                String regiao = "Nacional";
                if (titulo.contains("-")) {
                    String[] partes = titulo.split("-");
                    String ultimaParte = partes[partes.length - 1].replaceAll("[^A-Za-z]", "").trim().toUpperCase();
                    if (ultimaParte.length() == 2) {
                        regiao = ultimaParte;
                    }
                }

                String hash = Concurso.gerarHash("QUADRIX", linkCompleto);
                if (hashesVistos.add(hash)) {
                    analisados++;
                    Concurso concurso = new Concurso(
                            null,
                            titulo,
                            "Quadrix",
                            linkCompleto,
                            regiao,
                            LocalDateTime.now(),
                            "QUADRIX",
                            hash
                    );
                    // Retorna somente os concursos de TI (título ou cargos)
                    if (ehTecnologia(concurso)) {
                        resultado.add(concurso.comTecnologia(true));
                    }
                }
            }

            log.info("[QUADRIX] {} concurso(s) analisado(s), {} de TI.", analisados, resultado.size());
        } catch (Exception e) {
            log.error("[QUADRIX] Erro ao raspar: {}", e.getMessage(), e);
        }
        return resultado;
    }
}
