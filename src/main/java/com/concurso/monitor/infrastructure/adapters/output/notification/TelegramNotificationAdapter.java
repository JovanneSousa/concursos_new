package com.concurso.monitor.infrastructure.adapters.output.notification;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.out.NotificacaoOutputPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class TelegramNotificationAdapter implements NotificacaoOutputPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationAdapter.class);
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot%s/sendMessage";

    private final String botToken;
    private final String chatId;
    private final RestClient restClient;

    @Autowired
    public TelegramNotificationAdapter(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.chat-id}") String chatId,
            RestClient.Builder restClientBuilder
    ) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.restClient = (restClientBuilder != null ? restClientBuilder : RestClient.builder()).build();
    }

    public TelegramNotificationAdapter(
            String botToken,
            String chatId,
            RestClient restClient
    ) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.restClient = restClient;
    }

    @Override
    public void enviarNotificacao(Concurso concurso) {
        log.info("Enviando notificação via Telegram para o chat [{}] sobre o concurso [{}] (Prioritário: {}, TI: {})",
                chatId, concurso.titulo(), concurso.prioritario(), concurso.tecnologia());

        String mensagemHtml = formatarMensagemHtml(concurso);
        var payload = new TelegramSendMessagePayload(chatId, mensagemHtml, "HTML");
        String uri = String.format(TELEGRAM_API_URL, botToken);

        try {
            restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Notificação via Telegram enviada com sucesso para o chat [{}]", chatId);
        } catch (Exception e) {
            log.error("Falha ao enviar notificação via Telegram para o chat [{}]: {}", chatId, e.getMessage(), e);
            throw new RuntimeException("Erro ao comunicar com a API do Telegram: " + e.getMessage(), e);
        }
    }

    @Override
    public void enviarNotificacoes(List<Concurso> concursos) {
        if (concursos == null || concursos.isEmpty()) {
            return;
        }

        for (Concurso c : concursos) {
            try {
                enviarNotificacao(c);
                // Pausa de 150ms para evitar rate limiting (HTTP 429) da API do Telegram
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Erro ao enviar concurso [{}] via Telegram: {}", c.titulo(), e.getMessage());
            }
        }
    }

    private String formatarMensagemHtml(Concurso concurso) {
        String cabecalho;
        if (concurso.prioritario()) {
            cabecalho = "⭐ <b>[TI - ESTADO PRIORITÁRIO] Novo Edital Publicado!</b>\n🚨 <i>Oportunidade na sua região prioritária!</i>\n";
        } else {
            cabecalho = "💻 <b>[CONCURSO TI] Novo Edital Publicado!</b>\n";
        }

        return """
                %s
                📋 <b>Título:</b> %s
                🏛️ <b>Banca:</b> %s
                📍 <b>Região:</b> %s
                🔗 <b>Edital:</b> <a href="%s">Clique aqui para acessar o edital</a>
                """.formatted(
                cabecalho,
                escaparHtml(concurso.titulo()),
                escaparHtml(concurso.banca()),
                escaparHtml(concurso.regiao()),
                concurso.linkEdital() != null ? concurso.linkEdital() : ""
        );
    }

    private String escaparHtml(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public record TelegramSendMessagePayload(
            @JsonProperty("chat_id") String chatId,
            @JsonProperty("text") String text,
            @JsonProperty("parse_mode") String parseMode
    ) {
    }
}
