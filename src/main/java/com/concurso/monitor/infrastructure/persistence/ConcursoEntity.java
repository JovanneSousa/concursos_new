package com.concurso.monitor.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "concurso", indexes = {
        @Index(name = "idx_hash_deduplicacao", columnList = "hash_deduplicacao", unique = true),
        @Index(name = "idx_prioritario", columnList = "prioritario"),
        @Index(name = "idx_tecnologia", columnList = "tecnologia")
})
public class ConcursoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(nullable = false, length = 200)
    private String banca;

    @Column(name = "link_edital", nullable = false, length = 2000)
    private String linkEdital;

    @Column(length = 200)
    private String regiao;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(nullable = false, length = 100)
    private String fonte;

    @Column(name = "hash_deduplicacao", nullable = false, unique = true, length = 64)
    private String hashDeduplicacao;

    @Column(nullable = false)
    private boolean prioritario = false;

    @Column(nullable = false)
    private boolean tecnologia = false;

    public ConcursoEntity() {}

    public ConcursoEntity(String titulo, String banca, String linkEdital, String regiao,
                          LocalDateTime dataCriacao, String fonte, String hashDeduplicacao,
                          boolean prioritario, boolean tecnologia) {
        this.titulo = titulo;
        this.banca = banca;
        this.linkEdital = linkEdital;
        this.regiao = regiao;
        this.dataCriacao = dataCriacao;
        this.fonte = fonte;
        this.hashDeduplicacao = hashDeduplicacao;
        this.prioritario = prioritario;
        this.tecnologia = tecnologia;
    }

    public ConcursoEntity(String titulo, String banca, String linkEdital, String regiao,
                          LocalDateTime dataCriacao, String fonte, String hashDeduplicacao,
                          boolean prioritario) {
        this(titulo, banca, linkEdital, regiao, dataCriacao, fonte, hashDeduplicacao, prioritario, false);
    }

    public ConcursoEntity(String titulo, String banca, String linkEdital, String regiao,
                          LocalDateTime dataCriacao, String fonte, String hashDeduplicacao) {
        this(titulo, banca, linkEdital, regiao, dataCriacao, fonte, hashDeduplicacao, false, false);
    }

    public String getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getBanca() { return banca; }
    public void setBanca(String banca) { this.banca = banca; }
    public String getLinkEdital() { return linkEdital; }
    public void setLinkEdital(String linkEdital) { this.linkEdital = linkEdital; }
    public String getRegiao() { return regiao; }
    public void setRegiao(String regiao) { this.regiao = regiao; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
    public String getFonte() { return fonte; }
    public void setFonte(String fonte) { this.fonte = fonte; }
    public String getHashDeduplicacao() { return hashDeduplicacao; }
    public void setHashDeduplicacao(String hashDeduplicacao) { this.hashDeduplicacao = hashDeduplicacao; }
    public boolean isPrioritario() { return prioritario; }
    public void setPrioritario(boolean prioritario) { this.prioritario = prioritario; }
    public boolean isTecnologia() { return tecnologia; }
    public void setTecnologia(boolean tecnologia) { this.tecnologia = tecnologia; }
}
