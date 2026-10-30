-- Schema de referência do módulo concursos-news
-- Execute manualmente ou use o ddl-auto=update do Hibernate

CREATE TABLE IF NOT EXISTS concurso (
    id                  VARCHAR(36) PRIMARY KEY,
    titulo              VARCHAR(500) NOT NULL,
    banca               VARCHAR(200) NOT NULL,
    link_edital         VARCHAR(2000) NOT NULL,
    regiao              VARCHAR(200),
    data_criacao        TIMESTAMP NOT NULL,
    fonte               VARCHAR(100) NOT NULL,
    hash_deduplicacao   VARCHAR(64) NOT NULL UNIQUE,
    prioritario         BOOLEAN NOT NULL DEFAULT FALSE,
    tecnologia          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_hash_deduplicacao ON concurso (hash_deduplicacao);
CREATE INDEX IF NOT EXISTS idx_prioritario ON concurso (prioritario);
CREATE INDEX IF NOT EXISTS idx_tecnologia ON concurso (tecnologia);
CREATE INDEX IF NOT EXISTS idx_fonte ON concurso (fonte);
CREATE INDEX IF NOT EXISTS idx_data_criacao ON concurso (data_criacao DESC);
