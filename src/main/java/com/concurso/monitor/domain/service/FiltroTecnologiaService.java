package com.concurso.monitor.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class FiltroTecnologiaService {

    private static final Logger log = LoggerFactory.getLogger(FiltroTecnologiaService.class);

    // Expressão regular com limites de palavra (\b) para siglas curtas
    // Evita falsos positivos como "GRATIFICACAO", "CURITIBA", "PARTICIPACAO"
    private static final Pattern SIGLAS_TI_PATTERN = Pattern.compile(
            "(?:\\b(TI|TIC|DBA)\\b|\\bT\\.I\\b|\\bT\\.I\\.(?:\\W|$)|\\bT\\.I\\.C\\.(?:\\W|$))",
            Pattern.CASE_INSENSITIVE
    );

    // Termos e expressões compostas que caracterizam a área de Tecnologia da Informação
    private static final List<String> TERMOS_PADRAO_TI = List.of(
            "TECNOLOGIA DA INFORMACAO",
            "ANALISTA DE SISTEMAS",
            "DESENVOLVEDOR",
            "DESENVOLVIMENTO DE SOFTWARE",
            "DESENVOLVIMENTO DE SISTEMAS",
            "ENGENHARIA DE SOFTWARE",
            "ENGENHEIRO DE SOFTWARE",
            "CIENCIA DA COMPUTACAO",
            "CIENTISTA DA COMPUTACAO",
            "ENGENHARIA DA COMPUTACAO",
            "ENGENHEIRO DA COMPUTACAO",
            "SISTEMAS DE INFORMACAO",
            "PROGRAMADOR",
            "PROGRAMACAO",
            "ANALISTA DE TI",
            "TECNICO DE TI",
            "TECNICO EM TI",
            "TECNICO EM INFORMATICA",
            "TECNICO DE INFORMATICA",
            "ANALISTA DE INFORMATICA",
            "INFORMATICA",
            "BANCO DE DADOS",
            "ADMINISTRADOR DE BANCO DE DADOS",
            "SEGURANCA DA INFORMACAO",
            "CIBERSEGURANCA",
            "SEGURANCA CIBERNETICA",
            "INFRAESTRUTURA DE TI",
            "REDES DE COMPUTADORES",
            "ANALISTA DE REDES",
            "ADMINISTRADOR DE REDES",
            "DEVOPS",
            "CIENCIA DE DADOS",
            "CIENTISTA DE DADOS",
            "ENGENHARIA DE DADOS",
            "ENGENHEIRO DE DADOS",
            "ANALISTA DE DADOS",
            "INTELIGENCIA ARTIFICIAL",
            "ARQUITETO DE SOFTWARE",
            "ARQUITETO DE SOLUCOES",
            "FRONTEND",
            "BACKEND",
            "FULL STACK",
            "WEB MASTER",
            "SUPORTE TECNICO EM TI"
    );

    private final boolean habilitado;
    private final List<String> termosCompletos;

    public FiltroTecnologiaService(
            @Value("${monitor.filtro-tecnologia.habilitado:true}") boolean habilitado,
            @Value("${monitor.filtro-tecnologia.termos-customizados:}") String termosCustomizadosConfig
    ) {
        this.habilitado = habilitado;

        Set<String> termosSet = new LinkedHashSet<>(TERMOS_PADRAO_TI);
        if (termosCustomizadosConfig != null && !termosCustomizadosConfig.isBlank()) {
            Arrays.stream(termosCustomizadosConfig.split(","))
                    .map(String::trim)
                    .map(this::normalizar)
                    .filter(s -> !s.isBlank())
                    .forEach(termosSet::add);
        }

        this.termosCompletos = new ArrayList<>(termosSet);
        log.info("FiltroTecnologiaService configurado. Habilitado: {}, Total de termos mapeados: {}",
                this.habilitado, this.termosCompletos.size());
    }

    /**
     * Verifica se o concurso possui relação com a área de Tecnologia da Informação (TI).
     */
    public boolean isTecnologia(String titulo, String informacoesAdicionais) {
        if (!habilitado) {
            // Se o filtro estiver desabilitado, considera todos como elegíveis
            return true;
        }

        String texto = (titulo != null ? titulo : "") + " " + (informacoesAdicionais != null ? informacoesAdicionais : "");
        String textoNormalizado = normalizar(texto);

        // 1. Verifica siglas delimitadas (TI, T.I., TIC, DBA)
        if (SIGLAS_TI_PATTERN.matcher(textoNormalizado).find()) {
            return true;
        }

        // 2. Verifica expressões e termos compostos
        for (String termo : termosCompletos) {
            if (textoNormalizado.contains(termo)) {
                return true;
            }
        }

        return false;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();
    }
}
