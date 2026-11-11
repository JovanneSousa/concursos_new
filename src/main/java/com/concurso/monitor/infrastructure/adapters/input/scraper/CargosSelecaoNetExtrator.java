package com.concurso.monitor.infrastructure.adapters.input.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrai os cargos da página de um concurso hospedado na plataforma "selecao.net.br"
 * (usada por Quadrix e Selecon, ex.: /informacoes/3078/).
 * <p>
 * Ordem de extração:
 * 1. Tabela de vagas ("Vagas"): células {@code td.cargo}.
 * 2. Fallback (quando a tabela ainda não foi publicada): parágrafos do texto de
 * "Informações Gerais" que mencionam "cargo".
 */
final class CargosSelecaoNetExtrator {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private CargosSelecaoNetExtrator() {
    }

    static List<String> buscar(String urlConcurso) throws IOException {
        Document doc = Jsoup.connect(urlConcurso)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                .timeout(15_000)
                .get();
        return extrair(doc);
    }

    static List<String> extrair(Document doc) {
        List<String> cargos = new ArrayList<>();
        for (Element celula : doc.select("td.cargo")) {
            String texto = celula.text().trim();
            if (!texto.isBlank()) {
                cargos.add(texto);
            }
        }
        if (!cargos.isEmpty()) {
            return cargos;
        }

        for (Element paragrafo : doc.select("div.texto p")) {
            String texto = paragrafo.text().trim();
            if (texto.toLowerCase().contains("cargo")) {
                cargos.add(texto);
            }
        }
        return cargos;
    }
}
