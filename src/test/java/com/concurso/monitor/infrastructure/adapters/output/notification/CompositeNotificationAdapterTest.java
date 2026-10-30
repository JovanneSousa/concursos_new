package com.concurso.monitor.infrastructure.adapters.output.notification;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.out.NotificacaoOutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CompositeNotificationAdapterTest {

    private static Concurso concursoExemplo() {
        return new Concurso("1", "Analista Judiciário - TI", "FCC",
                "https://fcc.org.br/edital/1", "Sudeste",
                LocalDateTime.of(2026, 10, 29, 10, 0),
                "FCC", Concurso.gerarHash("FCC", "https://fcc.org.br/edital/1"),
                false, true);
    }

    @Test
    @DisplayName("Deveria filtrar a própria instância e outros Composites para evitar recursão")
    void deveFiltrarPropriaInstanciaEOutrosComposites() {
        NotificacaoOutputPort mock1 = mock(NotificacaoOutputPort.class);
        NotificacaoOutputPort mock2 = mock(NotificacaoOutputPort.class);
        CompositeNotificationAdapter outroComposite = new CompositeNotificationAdapter(List.of(mock1));

        List<NotificacaoOutputPort> lista = new ArrayList<>(List.of(mock1, mock2, outroComposite));
        CompositeNotificationAdapter composite = new CompositeNotificationAdapter(lista);

        assertThat(composite.getAdapters())
                .hasSize(2)
                .containsExactly(mock1, mock2)
                .doesNotContain(outroComposite);
    }

    @Test
    @DisplayName("Deveria disparar notificações para todos os canais cadastrados")
    void deveEnviarParaTodosOsAdaptadores() {
        NotificacaoOutputPort adapter1 = mock(NotificacaoOutputPort.class);
        NotificacaoOutputPort adapter2 = mock(NotificacaoOutputPort.class);
        CompositeNotificationAdapter composite = new CompositeNotificationAdapter(List.of(adapter1, adapter2));

        composite.enviarNotificacao(concursoExemplo());

        verify(adapter1, times(1)).enviarNotificacoes(anyList());
        verify(adapter2, times(1)).enviarNotificacoes(anyList());
    }

    @Test
    @DisplayName("Erro em um canal não deve impedir envio pelo outro")
    void erroEmUmCanalNaoDeveImpedirOutroCanal() {
        NotificacaoOutputPort adapterFalho = mock(NotificacaoOutputPort.class);
        NotificacaoOutputPort adapterSucesso = mock(NotificacaoOutputPort.class);

        doThrow(new RuntimeException("Falha na conexão SMTP")).when(adapterFalho).enviarNotificacoes(anyList());
        CompositeNotificationAdapter composite = new CompositeNotificationAdapter(List.of(adapterFalho, adapterSucesso));

        composite.enviarNotificacao(concursoExemplo()); // não deve lançar exceção

        verify(adapterFalho, times(1)).enviarNotificacoes(anyList());
        verify(adapterSucesso, times(1)).enviarNotificacoes(anyList());
    }

    @Test
    @DisplayName("Deveria tratar lista vazia sem lançar exceção")
    void deveTratarListaVaziaSemLancarExcecao() {
        CompositeNotificationAdapter composite = new CompositeNotificationAdapter(List.of());
        assertThat(composite.getAdapters()).isEmpty();
        composite.enviarNotificacoes(List.of());
        composite.enviarNotificacao(concursoExemplo());
    }
}
