package com.concurso.monitor.infrastructure.adapters.output.notification;

import com.concurso.monitor.domain.model.Concurso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramNotificationAdapterTest {

    private static final String BOT_TOKEN = "123456:TEST_TOKEN";
    private static final String CHAT_ID = "987654321";

    private MockRestServiceServer mockServer;
    private TelegramNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new TelegramNotificationAdapter(BOT_TOKEN, CHAT_ID, builder);
    }

    private static Concurso concursoExemplo() {
        return new Concurso("conc-101",
                "Auditor Fiscal da Receita Estadual & Especial",
                "FGV", "https://conhecimento.fgv.br/concursos/sefaz",
                "Nordeste", LocalDateTime.of(2026, 10, 29, 14, 0),
                "FGV", Concurso.gerarHash("FGV", "https://conhecimento.fgv.br/concursos/sefaz"));
    }

    @Test
    @DisplayName("Deveria enviar mensagem formatada em HTML para a API do Telegram")
    void deveEnviarMensagemFormatadaHtmlComSucesso() {
        mockServer.expect(requestTo("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.chat_id").value(CHAT_ID))
                .andExpect(jsonPath("$.parse_mode").value("HTML"))
                .andExpect(jsonPath("$.text").value(containsString("Auditor Fiscal da Receita Estadual &amp; Especial")))
                .andExpect(jsonPath("$.text").value(containsString("FGV")))
                .andExpect(jsonPath("$.text").value(containsString("Nordeste")))
                .andExpect(jsonPath("$.text").value(containsString("https://conhecimento.fgv.br/concursos/sefaz")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        adapter.enviarNotificacao(concursoExemplo());
        mockServer.verify();
    }

    @Test
    @DisplayName("Deveria lançar RuntimeException caso a API do Telegram retorne erro de servidor")
    void deveLancarExcecaoQuandoTelegramApiFalhar() {
        mockServer.expect(requestTo("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.enviarNotificacao(concursoExemplo()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro ao comunicar com a API do Telegram");

        mockServer.verify();
    }
}
