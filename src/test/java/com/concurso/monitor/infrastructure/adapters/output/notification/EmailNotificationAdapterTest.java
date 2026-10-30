package com.concurso.monitor.infrastructure.adapters.output.notification;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.service.EstadoPrioritarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    private EstadoPrioritarioService estadoPrioritarioService;

    private final String recipientEmail = "destinatario@concursos.com.br";

    @BeforeEach
    void setUp() {
        estadoPrioritarioService = new EstadoPrioritarioService("MT,SC,PR,DF,GO");
    }

    private static Concurso criarConcurso(String id, String titulo, String banca, String regiao, boolean prioritario) {
        return new Concurso(
                id,
                titulo,
                banca,
                "https://banca.org/edital/" + id,
                regiao,
                LocalDateTime.of(2026, 10, 30, 9, 0, 0),
                banca.toUpperCase(),
                Concurso.gerarHash(banca, "https://banca.org/edital/" + id),
                prioritario,
                true
        );
    }

    @Test
    @DisplayName("Deveria unificar múltiplos concursos prioritários em um único e-mail agrupado por Estado e Banca")
    void deveUnificarConcursosPrioritariosEmUmUnicoEmail() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, recipientEmail, estadoPrioritarioService);

        Concurso c1 = criarConcurso("1", "Câmara de Cuiabá - Analista de TI", "Instituto Selecon", "MT", true);
        Concurso c2 = criarConcurso("2", "Prefeitura de Cuiabá - Desenvolvedor", "Instituto Selecon", "MT", true);
        Concurso c3 = criarConcurso("3", "TJ-SC - Analista de Sistemas", "FGV", "SC", true);

        adapter.enviarNotificacoes(List.of(c1, c2, c3));

        // Deve disparar EXATAMENTE 1 e-mail para todos os prioritários
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMsg = captor.getValue();
        assertThat(sentMsg.getTo()).containsExactly(recipientEmail);
        assertThat(sentMsg.getSubject()).contains("ESTADOS PRIORITÁRIOS").contains("3 Novo(s)");

        String body = sentMsg.getText();
        // Verifica se os estados e as bancas aparecem destacados e agrupados
        assertThat(body).contains("📍 ESTADO: MT");
        assertThat(body).contains("📍 ESTADO: SC");
        assertThat(body).contains("🏛️ BANCA: Instituto Selecon");
        assertThat(body).contains("🏛️ BANCA: FGV");
        assertThat(body).contains("Câmara de Cuiabá - Analista de TI");
        assertThat(body).contains("Prefeitura de Cuiabá - Desenvolvedor");
        assertThat(body).contains("TJ-SC - Analista de Sistemas");
    }

    @Test
    @DisplayName("Deveria disparar no máximo 2 e-mails quando houver concursos prioritários e não-prioritários")
    void deveEnviarDoisEmailsQuandoHouverAmbosOsGrupos() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, recipientEmail, estadoPrioritarioService);

        Concurso prio = criarConcurso("10", "DPE-MT - Desenvolvedor", "FGV", "MT", true);
        Concurso comum = criarConcurso("20", "TRF-3 - Analista Judiciário TI", "Cebraspe", "SP", false);

        adapter.enviarNotificacoes(List.of(prio, comum));

        // Exatamente 2 e-mails: 1 para prioritários e 1 para demais estados
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(captor.capture());

        List<SimpleMailMessage> msgs = captor.getAllValues();
        assertThat(msgs.get(0).getSubject()).contains("ESTADOS PRIORITÁRIOS");
        assertThat(msgs.get(1).getSubject()).contains("DEMAIS ESTADOS");
    }

    @Test
    @DisplayName("Não deveria enviar nenhum e-mail caso a lista de concursos seja vazia ou nula")
    void naoDeveEnviarEmailSeListaVazia() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, recipientEmail, estadoPrioritarioService);

        adapter.enviarNotificacoes(List.of());
        adapter.enviarNotificacoes(null);

        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Deveria lançar RuntimeException caso o envio de e-mail falhe")
    void deveLancarExcecaoQuandoEnvioFalhar() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, recipientEmail, estadoPrioritarioService);
        Concurso concurso = criarConcurso("1", "Analista de Sistemas", "FGV", "MT", true);

        doThrow(new MailSendException("Erro SMTP")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> adapter.enviarNotificacao(concurso))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro ao enviar e-mail de notificação");
    }
}
