package com.concurso.monitor.infrastructure.adapters.output.notification;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.out.NotificacaoOutputPort;
import com.concurso.monitor.domain.service.EstadoPrioritarioService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class EmailNotificationAdapter implements NotificacaoOutputPort {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationAdapter.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mailSender;
    private final String recipientEmail;
    private final EstadoPrioritarioService estadoPrioritarioService;

    public EmailNotificationAdapter(
            JavaMailSender mailSender,
            @Value("${notification.email.to}") String recipientEmail,
            EstadoPrioritarioService estadoPrioritarioService
    ) {
        this.mailSender = mailSender;
        this.recipientEmail = recipientEmail;
        this.estadoPrioritarioService = estadoPrioritarioService;
    }

    @Override
    public void enviarNotificacao(Concurso concurso) {
        if (concurso != null) {
            enviarNotificacoes(List.of(concurso));
        }
    }

    /**
     * Unifica as notificações em no máximo 2 e-mails por ciclo:
     * 1. Um e-mail para concursos de Estados Prioritários (se houver).
     * 2. Um e-mail para concursos dos Demais Estados / Nacional (se houver).
     *
     * Dentro de cada e-mail, os concursos são agrupados com destaque para ESTADO e BANCA.
     */
    @Override
    public void enviarNotificacoes(List<Concurso> concursos) {
        if (concursos == null || concursos.isEmpty()) {
            return;
        }

        List<Concurso> prioritarios = concursos.stream()
                .filter(Concurso::prioritario)
                .toList();

        List<Concurso> demaisEstados = concursos.stream()
                .filter(c -> !c.prioritario())
                .toList();

        if (!prioritarios.isEmpty()) {
            log.info("Preparando e-mail unificado para {} concurso(s) prioritário(s) de TI...", prioritarios.size());
            dispararEmailAgrupado(prioritarios, true);
        }

        if (!demaisEstados.isEmpty()) {
            log.info("Preparando e-mail unificado para {} concurso(s) de TI dos demais estados...", demaisEstados.size());
            dispararEmailAgrupado(demaisEstados, false);
        }
    }

    private void dispararEmailAgrupado(List<Concurso> lista, boolean prioritario) {
        String assunto = prioritario
                ? "⭐ [CONCURSOS TI - ESTADOS PRIORITÁRIOS] " + lista.size() + " Novo(s) Edital(is) Detectado(s)"
                : "💻 [CONCURSOS TI - DEMAIS ESTADOS] " + lista.size() + " Novo(s) Edital(is) Detectado(s)";

        // Agrupamento: Estado (UF) -> Banca -> Lista de Concursos
        Map<String, Map<String, List<Concurso>>> agrupados = agruparPorEstadoEBanca(lista);

        String corpoTexto = gerarCorpoTexto(agrupados, lista.size(), prioritario);
        String corpoHtml = gerarCorpoHtml(agrupados, lista.size(), prioritario);

        try {
            // Tenta enviar via MimeMessage (HTML + multipart texto)
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            if (mimeMessage != null) {
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
                helper.setTo(recipientEmail);
                helper.setSubject(assunto);
                helper.setText(corpoTexto, corpoHtml);
                mailSender.send(mimeMessage);
                log.info("E-mail unificado ({}) enviado com sucesso (HTML) para [{}]",
                        prioritario ? "Prioritários" : "Geral", recipientEmail);
                return;
            }
        } catch (Exception e) {
            log.debug("MimeMessage indisponível ou falhou, recorrendo a SimpleMailMessage: {}", e.getMessage());
        }

        // Fallback resiliente para SimpleMailMessage (garante compatibilidade em testes ou servidores restritos)
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject(assunto);
            message.setText(corpoTexto);
            mailSender.send(message);
            log.info("E-mail unificado ({}) enviado com sucesso (Texto) para [{}]",
                    prioritario ? "Prioritários" : "Geral", recipientEmail);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail unificado para [{}]: {}", recipientEmail, e.getMessage(), e);
            throw new RuntimeException("Erro ao enviar e-mail de notificação: " + e.getMessage(), e);
        }
    }

    private Map<String, Map<String, List<Concurso>>> agruparPorEstadoEBanca(List<Concurso> lista) {
        Map<String, Map<String, List<Concurso>>> resultado = new TreeMap<>();

        for (Concurso c : lista) {
            String uf = extrairUf(c);
            String banca = (c.banca() != null && !c.banca().isBlank()) ? c.banca().trim() : "Banca Não Informada";

            resultado.computeIfAbsent(uf, k -> new TreeMap<>())
                    .computeIfAbsent(banca, k -> new ArrayList<>())
                    .add(c);
        }
        return resultado;
    }

    private String extrairUf(Concurso c) {
        if (estadoPrioritarioService != null) {
            String ufIdentificada = estadoPrioritarioService.identificarUf(c.regiao(), c.titulo());
            if (ufIdentificada != null && !ufIdentificada.isBlank()) {
                return ufIdentificada.toUpperCase();
            }
        }
        if (c.regiao() != null && !c.regiao().isBlank()) {
            return c.regiao().trim().toUpperCase();
        }
        return "NACIONAL / DEMAIS";
    }

    private String gerarCorpoTexto(Map<String, Map<String, List<Concurso>>> agrupados, int total, boolean prioritario) {
        StringBuilder sb = new StringBuilder();
        String tituloSecao = prioritario
                ? "⭐ NOVOS CONCURSOS DE TECNOLOGIA DA INFORMAÇÃO (TI) - ESTADOS PRIORITÁRIOS"
                : "💻 NOVOS CONCURSOS DE TECNOLOGIA DA INFORMAÇÃO (TI) - NACIONAL / DEMAIS ESTADOS";

        sb.append("================================================================================\n");
        sb.append(tituloSecao).append("\n");
        sb.append("================================================================================\n");
        sb.append("Total de novos editais encontrados: ").append(total).append("\n");
        sb.append("Data da varredura: ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER)).append("\n\n");

        for (Map.Entry<String, Map<String, List<Concurso>>> estadoEntry : agrupados.entrySet()) {
            String uf = estadoEntry.getKey();
            sb.append("--------------------------------------------------------------------------------\n");
            sb.append("📍 ESTADO: ").append(uf);
            if (prioritario) {
                sb.append("  [⭐ ESTADO PRIORITÁRIO]");
            }
            sb.append("\n--------------------------------------------------------------------------------\n");

            for (Map.Entry<String, List<Concurso>> bancaEntry : estadoEntry.getValue().entrySet()) {
                String banca = bancaEntry.getKey();
                sb.append("  🏛️ BANCA: ").append(banca).append("\n");

                for (Concurso c : bancaEntry.getValue()) {
                    sb.append("    • Título: ").append(c.titulo()).append("\n");
                    sb.append("      Edital / Inscrição: ").append(c.linkEdital()).append("\n\n");
                }
            }
        }

        sb.append("================================================================================\n");
        sb.append("Notificação gerada automaticamente pelo Sistema de Monitoramento de Concursos.\n");
        return sb.toString();
    }

    private String gerarCorpoHtml(Map<String, Map<String, List<Concurso>>> agrupados, int total, boolean prioritario) {
        StringBuilder sb = new StringBuilder();

        String corHeader = prioritario ? "#1e3a8a" : "#0f172a";
        String badgeTitulo = prioritario ? "⭐ ESTADOS PRIORITÁRIOS" : "💻 MONITORAMENTO NACIONAL";

        sb.append("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Novos Concursos de TI</title>
                </head>
                <body style="margin:0; padding:20px; background-color:#f1f5f9; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                    <div style="max-width:700px; margin:0 auto; background-color:#ffffff; border-radius:10px; overflow:hidden; box-shadow:0 4px 6px -1px rgba(0,0,0,0.1);">
                        <!-- Header -->
                        <div style="background-color:%s; color:#ffffff; padding:28px 24px;">
                            <span style="background-color:rgba(255,255,255,0.2); padding:4px 10px; border-radius:12px; font-size:12px; font-weight:bold; letter-spacing:0.5px;">
                                %s
                            </span>
                            <h1 style="margin:12px 0 6px 0; font-size:22px;">Novos Concursos de TI Encontrados</h1>
                            <p style="margin:0; font-size:14px; opacity:0.9;">Total de <b>%d</b> novo(s) edital(is) relevante(s) na varredura de %s</p>
                        </div>
                        <div style="padding:24px;">
                """.formatted(corHeader, badgeTitulo, total, LocalDateTime.now().format(DATE_TIME_FORMATTER)));

        for (Map.Entry<String, Map<String, List<Concurso>>> estadoEntry : agrupados.entrySet()) {
            String uf = estadoEntry.getKey();
            sb.append("""
                    <!-- Bloco do Estado -->
                    <div style="margin-top:24px; margin-bottom:12px; border-left:5px solid #2563eb; padding-left:12px;">
                        <h2 style="margin:0; font-size:18px; color:#1e293b; display:flex; align-items:center;">
                            📍 ESTADO: <span style="color:#2563eb; margin-left:6px; font-weight:800;">%s</span>
                        </h2>
                    </div>
                    """.formatted(uf));

            for (Map.Entry<String, List<Concurso>> bancaEntry : estadoEntry.getValue().entrySet()) {
                String banca = bancaEntry.getKey();
                sb.append("""
                        <!-- Bloco da Banca -->
                        <div style="background-color:#f8fafc; border:1px solid #e2e8f0; border-radius:8px; padding:16px; margin-bottom:14px;">
                            <div style="font-size:14px; font-weight:bold; color:#475569; margin-bottom:10px;">
                                🏛️ BANCA ORGANIZADORA: <span style="color:#0f172a; font-size:15px;">%s</span>
                            </div>
                        """.formatted(banca));

                for (Concurso c : bancaEntry.getValue()) {
                    sb.append("""
                            <!-- Card do Concurso -->
                            <div style="background-color:#ffffff; border:1px solid #cbd5e1; border-radius:6px; padding:12px; margin-bottom:8px;">
                                <div style="font-size:15px; font-weight:600; color:#1e293b; margin-bottom:6px;">
                                    📋 %s
                                </div>
                                <div style="margin-top:8px;">
                                    <a href="%s" target="_blank" style="display:inline-block; background-color:#2563eb; color:#ffffff; padding:7px 14px; border-radius:4px; text-decoration:none; font-weight:600; font-size:12px;">
                                        🔗 Acessar Edital / Inscrição &rarr;
                                    </a>
                                </div>
                            </div>
                            """.formatted(c.titulo(), c.linkEdital()));
                }

                sb.append("</div>");
            }
        }

        sb.append("""
                        </div>
                        <!-- Footer -->
                        <div style="background-color:#f8fafc; border-top:1px solid #e2e8f0; padding:16px; text-align:center; font-size:12px; color:#64748b;">
                            Notificação automatizada do Sistema de Monitoramento de Concursos de TI.
                        </div>
                    </div>
                </body>
                </html>
                """);

        return sb.toString();
    }
}
