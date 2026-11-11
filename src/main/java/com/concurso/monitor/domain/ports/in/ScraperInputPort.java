package com.concurso.monitor.domain.ports.in;

import com.concurso.monitor.domain.model.Concurso;
import java.util.List;

public interface ScraperInputPort {
    /**
     * Identificador único desta fonte de scraping (ex: "FGV", "CEBRASPE", "DOU", "DOE-MT").
     */
    String identificador();

    /**
     * Executa o scraping e retorna a lista de concursos encontrados.
     * Nunca deve lançar exceção para fora; deve retornar lista vazia em caso de erro.
     */
    List<Concurso> raspar();

    /**
     * Indica se o concurso é da área de TI, usando as informações específicas da fonte
     * (ex.: cargos na página do concurso). Cada scraper deve sobrescrever e aplicar o filtro
     * dentro de {@link #raspar()}, retornando apenas concursos de TI.
     * Implementação padrão: sem filtro próprio (true); o caso de uso ainda descarta
     * os que não forem de TI pelo título.
     */
    default boolean ehTecnologia(Concurso concurso) {
        return true;
    }
}
