package com.concurso.monitor.infrastructure.adapters.input.scraper;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "scraper.doe-mt.enabled", havingValue = "true", matchIfMissing = true)
public class DoeMtScraperAdapter implements ScraperInputPort {

    private static final Logger log = LoggerFactory.getLogger(DoeMtScraperAdapter.class);

    // Página de índice do DOE-MT onde ficam as edições diárias
    private static final String PORTAL_URL = "https://www.iomat.mt.gov.br/";

    @Value("${scraper.doe-mt.keywords:Edital de Concurso Público,Aviso de Concurso,Banca Examinadora,processo seletivo}")
    private String keywordsStr;

    @Override
    public String identificador() { return "DOE-MT"; }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();
        try {
            String pdfUrl = localizarPdfDoDia();
            if (pdfUrl == null) {
                log.warn("[DOE-MT] Não foi possível localizar o PDF da edição do dia.");
                return resultado;
            }

            log.info("[DOE-MT] Processando PDF: {}", pdfUrl);
            String texto = extrairTextoDoPdf(pdfUrl);
            List<String> keywords = List.of(keywordsStr.split(","));

            String[] paragrafos = texto.split("\\n{2,}");
            for (String paragrafo : paragrafos) {
                String p = paragrafo.trim();
                boolean relevante = keywords.stream()
                        .anyMatch(kw -> p.toLowerCase().contains(kw.toLowerCase().trim()));
                if (!relevante || p.length() < 30) continue;

                // Título: primeira linha do parágrafo, truncada em 300 chars
                String titulo = p.lines().findFirst().orElse(p).trim();
                if (titulo.length() > 300) titulo = titulo.substring(0, 297) + "...";

                String hash = Concurso.gerarHash("DOE-MT", pdfUrl + "::" + titulo);
                resultado.add(new Concurso(null, titulo, "DOE-MT", pdfUrl,
                        "Mato Grosso", LocalDateTime.now(), "DOE-MT", hash));
            }
            log.info("[DOE-MT] {} trecho(s) relevante(s) encontrado(s).", resultado.size());
        } catch (Exception e) {
            log.error("[DOE-MT] Erro ao processar: {}", e.getMessage(), e);
        }
        return resultado;
    }

    private String localizarPdfDoDia() {
        try {
            Document doc = Jsoup.connect(PORTAL_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10_000)
                    .get();

            // Procura o link mais recente de PDF do diário
            for (Element a : doc.select("a[href]")) {
                String href = a.attr("abs:href").toLowerCase();
                if (href.contains(".pdf") && (href.contains("diario") || href.contains("iomat") || href.contains("edicao"))) {
                    return a.attr("abs:href");
                }
            }
        } catch (Exception e) {
            log.error("[DOE-MT] Erro ao buscar link do PDF: {}", e.getMessage(), e);
        }
        return null;
    }

    private String extrairTextoDoPdf(String pdfUrl) throws Exception {
        byte[] pdfBytes;
        try (InputStream is = URI.create(pdfUrl).toURL().openStream()) {
            pdfBytes = is.readAllBytes();
        }
        try (PDDocument pdfDocument = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(pdfDocument);
        }
    }
}
