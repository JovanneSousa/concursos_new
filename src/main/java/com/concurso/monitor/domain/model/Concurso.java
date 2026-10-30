package com.concurso.monitor.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

public record Concurso(
        String id,
        String titulo,
        String banca,
        String linkEdital,
        String regiao,
        LocalDateTime dataCriacao,
        String fonte,
        String hashDeduplicacao,
        boolean prioritario,
        boolean tecnologia
) {
    public Concurso(
            String id,
            String titulo,
            String banca,
            String linkEdital,
            String regiao,
            LocalDateTime dataCriacao,
            String fonte,
            String hashDeduplicacao,
            boolean prioritario
    ) {
        this(id, titulo, banca, linkEdital, regiao, dataCriacao, fonte, hashDeduplicacao, prioritario, false);
    }

    public Concurso(
            String id,
            String titulo,
            String banca,
            String linkEdital,
            String regiao,
            LocalDateTime dataCriacao,
            String fonte,
            String hashDeduplicacao
    ) {
        this(id, titulo, banca, linkEdital, regiao, dataCriacao, fonte, hashDeduplicacao, false, false);
    }

    public Concurso comPrioridade(boolean prioritario) {
        return new Concurso(
                this.id,
                this.titulo,
                this.banca,
                this.linkEdital,
                this.regiao,
                this.dataCriacao,
                this.fonte,
                this.hashDeduplicacao,
                prioritario,
                this.tecnologia
        );
    }

    public Concurso comTecnologia(boolean tecnologia) {
        return new Concurso(
                this.id,
                this.titulo,
                this.banca,
                this.linkEdital,
                this.regiao,
                this.dataCriacao,
                this.fonte,
                this.hashDeduplicacao,
                this.prioritario,
                tecnologia
        );
    }

    public static String gerarHash(String fonte, String url) {
        try {
            var digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest((fonte + "::" + url).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 não disponível", e);
        }
    }
}
