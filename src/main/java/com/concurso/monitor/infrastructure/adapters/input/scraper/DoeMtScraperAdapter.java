package com.concurso.monitor.infrastructure.adapters.input.scraper;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "scraper.doe-mt.enabled", havingValue = "true", matchIfMissing = true)
public class DoeMtScraperAdapter implements ScraperInputPort {

    private record DoeResponseDto(List<ItensDoeResponseDto> itens) {}
    private record ItensDoeResponseDto(int id, int paginas) {}

    private final RestClient restClient;
    private static final Logger log = LoggerFactory.getLogger(DoeMtScraperAdapter.class);
    private static final String PORTAL_URL = "https://www.iomat.mt.gov.br/";

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)(https?://|www\\.)[\\w.-]+\\.[a-z]{2,}(/[\\w.-]*)*");
    private static final List<String> BANCAS_CONHECIDAS = List.of(
            "Selecon", "Cebraspe", "FGV", "FCC", "IBFC", "UFMT", "Quadrix", "Vunesp", "Fundatec"
    );

    @Value("${scraper.doe-mt.keywords:Edital de Concurso Público,Aviso de Concurso,Banca Examinadora,processo seletivo}")
    private String keywordsStr;
    @Value("${scraper.doe-mt.ignore-keywords:MTPREV,Conselho de Previdência,jeton,vacância,aposentadoria,licitação,pregão,dispensa de licitação}")
    private String ignoreKeywordsStr;

    public DoeMtScraperAdapter(RestClient.Builder restClientBuilder) {
        restClient = restClientBuilder.build();
    }

    @Override
    public String identificador() { return "DOE-MT"; }

    @Override
    public List<Concurso> raspar() {
        List<Concurso> resultado = new ArrayList<>();

        try {
            String dataHoje = LocalDate.now().toString();

            DoeResponseDto response = localizarPdfDoDia(dataHoje);

            if(response == null || response.itens() == null || response.itens().isEmpty()) {
                log.info("[DOE-MT] Nenhuma edição encontrada para a data de hoje ({}).", dataHoje);
                return Collections.emptyList();
            }

            List<String> listaUrlPdfs = response.itens().stream()
                    .map(item -> PORTAL_URL + "portal/edicoes/download/%s".formatted(item.id()))
                    .toList();

            for (String urlPdf : listaUrlPdfs) {
                processarPdf(urlPdf, resultado);
            }

            if (!resultado.isEmpty()){
                log.info("[DOE-MT] {} trecho(s) relevante(s) encontrado(s).", resultado.size());
            }
        } catch (Exception e) {
            log.error("[DOE-MT] Erro ao processar: {}", e.getMessage(), e);
        }
        return resultado;
    }

    private DoeResponseDto localizarPdfDoDia(String dataHoje) {
        String apiUrl = "https://www.iomat.mt.gov.br/apifront/portal/edicoes/edicoes_from_data/%s.json?&subtheme=false"
                .formatted(dataHoje);

        log.info("[DOE-MT] Buscando edições na API: {}", apiUrl);

        return restClient.get()
                .uri(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .retrieve()
                .body(new ParameterizedTypeReference<DoeResponseDto>() {});
    }

    private void processarPdf(String pdfUrl, List<Concurso> resultado) throws Exception {
        log.info("[DOE-MT] Processando PDF: {}", pdfUrl);
        String texto = extrairTextoDoPdf(pdfUrl);

        List<String> keywords = List.of(keywordsStr.split(","));
        List<String> ignoreKeywords = List.of(ignoreKeywordsStr.split(","));

        String[] paragrafos = texto.split("\\n{2,}");

        for (String paragrafo : paragrafos) {
            String p = paragrafo.trim();

            // Filtro de Relevância e Falsos Positivos
            boolean contemAlvo = keywords.stream().anyMatch(kw -> p.toLowerCase().contains(kw.toLowerCase().trim()));
            boolean contemLixo = ignoreKeywords.stream().anyMatch(kw -> p.toLowerCase().contains(kw.toLowerCase().trim()));

            // Exige no mínimo 100 caracteres, pois editais reais têm parágrafos longos
            if (!contemAlvo || contemLixo || p.length() < 100) continue;

            // Extração de Informações
            String linkInscricao = extrairLink(p);
            String banca = extrairBanca(p);
            String titulo = extrairTitulo(p);

            // Se encontrou um link no texto, usa ele; caso contrário, mantém o link do PDF do Diário
            String urlFinal = (linkInscricao != null) ? linkInscricao : pdfUrl;

            // Persistência
            String hash = Concurso.gerarHash("DOE-MT", urlFinal + "::" + titulo);
            resultado.add(new Concurso(
                    null,
                    titulo,
                    banca,
                    urlFinal,
                    "Mato Grosso",
                    LocalDateTime.now(),
                    "DOE-MT",
                    hash
            ));
        }
    }

    private String extrairLink(String texto) {
        Matcher matcher = URL_PATTERN.matcher(texto);
        while (matcher.find()) {
            String url = matcher.group();

            // Evita capturar o link do próprio diário oficial se ele for citado no texto
            if (url.contains("iomat.mt.gov.br")) continue;

            // Garante que o link será clicável na notificação
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }
            return url;
        }
        return null; // Retorna null se não achar nenhum link de banca
    }

    private String extrairBanca(String texto) {
        String textoLower = texto.toLowerCase();
        for (String banca : BANCAS_CONHECIDAS) {
            if (textoLower.contains(banca.toLowerCase())) {
                return banca;
            }
        }
        return "Órgão Local / Não identificada";
    }

    private String extrairTitulo(String texto) {
        // Busca a primeira linha que pareça ser o título real do edital
        for (String linha : texto.split("\\n")) {
            String l = linha.trim().toUpperCase();
            if (l.contains("EDITAL") || l.contains("PREFEITURA") || l.contains("CÂMARA") || l.contains("CONCURSO PÚBLICO")) {
                return l.length() > 300 ? l.substring(0, 297) + "..." : l;
            }
        }

        // se não achar palavras-chave de título, pega a primeira linha do parágrafo
        String tituloFallback = texto.lines().findFirst().orElse(texto).trim();
        return tituloFallback.length() > 300 ? tituloFallback.substring(0, 297) + "..." : tituloFallback;
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
