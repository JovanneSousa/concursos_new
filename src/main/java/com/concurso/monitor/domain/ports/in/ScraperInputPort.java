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
}
