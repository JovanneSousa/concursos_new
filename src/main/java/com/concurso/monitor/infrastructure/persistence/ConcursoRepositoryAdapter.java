package com.concurso.monitor.infrastructure.persistence;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.out.ConcursoRepository;
import org.springframework.stereotype.Component;

@Component
public class ConcursoRepositoryAdapter implements ConcursoRepository {

    private final ConcursoJpaRepository jpaRepository;

    public ConcursoRepositoryAdapter(ConcursoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existePorHash(String hash) {
        return jpaRepository.existsByHashDeduplicacao(hash);
    }

    @Override
    public void salvar(Concurso concurso) {
        ConcursoEntity entity = new ConcursoEntity(
                concurso.titulo(),
                concurso.banca(),
                concurso.linkEdital(),
                concurso.regiao(),
                concurso.dataCriacao(),
                concurso.fonte(),
                concurso.hashDeduplicacao(),
                concurso.prioritario(),
                concurso.tecnologia()
        );
        jpaRepository.save(entity);
    }
}
