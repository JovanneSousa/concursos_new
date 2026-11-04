package com.concurso.monitor.infrastructure.adapters.input.scraper;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.in.ScraperInputPort;

import java.util.List;

public class GazetaMtScraperAdapter implements ScraperInputPort {
    @Override
    public String identificador() {
        return "GazetaMt";
    }

    @Override
    public List<Concurso> raspar() {
        return List.of();
    }
}
