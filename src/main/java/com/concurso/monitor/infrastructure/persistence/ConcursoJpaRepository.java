package com.concurso.monitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConcursoJpaRepository extends JpaRepository<ConcursoEntity, String> {
    boolean existsByHashDeduplicacao(String hashDeduplicacao);
}
