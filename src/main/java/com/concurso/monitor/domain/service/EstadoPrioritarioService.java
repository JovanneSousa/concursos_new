package com.concurso.monitor.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EstadoPrioritarioService {

    private static final Logger log = LoggerFactory.getLogger(EstadoPrioritarioService.class);

    // Lista ordenada por tamanho decrescente para que "PARANA" seja testado antes de "PARA",
    // "MATO GROSSO DO SUL" antes de "MATO GROSSO", etc.
    private static final List<Map.Entry<String, String>> NOMES_ESTADOS_ORDENADOS;

    static {
        Map<String, String> mapa = new HashMap<>();
        mapa.put("MATO GROSSO DO SUL", "MS");
        mapa.put("MATO GROSSO", "MT");
        mapa.put("RIO GRANDE DO NORTE", "RN");
        mapa.put("RIO GRANDE DO SUL", "RS");
        mapa.put("RIO DE JANEIRO", "RJ");
        mapa.put("DISTRITO FEDERAL", "DF");
        mapa.put("ESPIRITO SANTO", "ES");
        mapa.put("SANTA CATARINA", "SC");
        mapa.put("MINAS GERAIS", "MG");
        mapa.put("PARAIBA", "PB");
        mapa.put("PARANA", "PR");
        mapa.put("PERNAMBUCO", "PE");
        mapa.put("AMAZONAS", "AM");
        mapa.put("MARANHAO", "MA");
        mapa.put("RONDONIA", "RO");
        mapa.put("TOCANTINS", "TO");
        mapa.put("ALAGOAS", "AL");
        mapa.put("BRASILIA", "DF");
        mapa.put("SERGIPE", "SE");
        mapa.put("RORAIMA", "RR");
        mapa.put("GOIAS", "GO");
        mapa.put("AMAPA", "AP");
        mapa.put("BAHIA", "BA");
        mapa.put("CEARA", "CE");
        mapa.put("PIAUI", "PI");
        mapa.put("ACRE", "AC");
        mapa.put("PARA", "PA");
        mapa.put("SAO PAULO", "SP");

        NOMES_ESTADOS_ORDENADOS = mapa.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getKey().length(), e1.getKey().length()))
                .toList();
    }

    private static final Pattern UF_PATTERN = Pattern.compile(
            "\\b(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Set<String> ufsPrioritarias;

    public EstadoPrioritarioService(
            @Value("${monitor.estados-prioritarios:MT,SC,PR,DF,GO}") String estadosConfig
    ) {
        this.ufsPrioritarias = Arrays.stream(estadosConfig.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        log.info("EstadoPrioritarioService inicializado. UFs prioritárias configuradas: {}", this.ufsPrioritarias);
    }

    public boolean isPrioritario(String regiao, String titulo) {
        String uf = identificarUf(regiao, titulo);
        return uf != null && ufsPrioritarias.contains(uf.toUpperCase());
    }

    public String identificarUf(String regiao, String titulo) {
        // 1. Se a região informada for diretamente uma sigla ou nome conhecido
        if (regiao != null && !regiao.equalsIgnoreCase("Nacional")) {
            String regiaoLimpa = regiao.trim().toUpperCase();
            if (regiaoLimpa.length() == 2) {
                return regiaoLimpa;
            }
        }

        String textoCombinado = (regiao != null ? regiao : "") + " " + (titulo != null ? titulo : "");
        String textoNormalizado = normalizar(textoCombinado);

        // 2. Procura por nomes por extenso (ex: "Mato Grosso", "Paraná") com ordenação por comprimento
        for (Map.Entry<String, String> entry : NOMES_ESTADOS_ORDENADOS) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b");
            if (pattern.matcher(textoNormalizado).find()) {
                return entry.getValue();
            }
        }

        // 3. Procura siglas de UF isoladas com boundary \b (ex: "- MT", "SC", "(PR)")
        Matcher matcher = UF_PATTERN.matcher(textoCombinado);
        while (matcher.find()) {
            String sigla = matcher.group(1).toUpperCase();
            // Evita falsos positivos comuns em títulos como "SE" (conjunção se)
            if ("SE".equals(sigla) && !textoCombinado.contains("- SE") && !textoCombinado.contains("/SE") && !textoCombinado.contains("(SE)")) {
                continue;
            }
            return sigla;
        }

        return null;
    }

    public Set<String> getUfsPrioritarias() {
        return Collections.unmodifiableSet(ufsPrioritarias);
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();
    }
}
