package com.concurso.monitor.domain.ports.out;

import com.concurso.monitor.domain.model.Concurso;

public interface ConcursoRepository {
    boolean existePorHash(String hash);
    void salvar(Concurso concurso);
}
