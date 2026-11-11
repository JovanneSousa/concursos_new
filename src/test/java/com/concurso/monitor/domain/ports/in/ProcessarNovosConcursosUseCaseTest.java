package com.concurso.monitor.domain.ports.in;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.out.ConcursoRepository;
import com.concurso.monitor.domain.ports.out.NotificacaoOutputPort;
import com.concurso.monitor.domain.service.EstadoPrioritarioService;
import com.concurso.monitor.domain.service.FiltroTecnologiaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessarNovosConcursosUseCaseTest {

    @Mock
    private NotificacaoOutputPort notificacaoOutputPort;

    @Mock
    private ConcursoRepository concursoRepository;

    @Mock
    private EstadoPrioritarioService estadoPrioritarioService;

    @Mock
    private FiltroTecnologiaService filtroTecnologiaService;

    @InjectMocks
    private ProcessarNovosConcursosUseCase useCase;

    private Concurso criarConcurso(String id, String titulo, String regiao) {
        String hash = Concurso.gerarHash("TEST", "https://banca.org/" + id);
        return new Concurso(null, titulo, "Cebraspe", "https://banca.org/" + id,
                regiao, LocalDateTime.now(), "TEST", hash);
    }

    @Test
    @DisplayName("Deveria persistir e notificar concurso novo com perfil de TI")
    void deveNotificarESalvarConcursoDeTecnologia() {
        Concurso concursoTi = criarConcurso("c1", "Analista de Sistemas - TI", "MT");

        when(concursoRepository.existePorHash(concursoTi.hashDeduplicacao())).thenReturn(false);
        when(estadoPrioritarioService.isPrioritario("MT", concursoTi.titulo())).thenReturn(true);
        when(filtroTecnologiaService.isTecnologia(concursoTi.titulo(), "MT")).thenReturn(true);

        useCase.executar(List.of(concursoTi));

        // Deve salvar no repositório com as flags atualizadas
        verify(concursoRepository, times(1)).salvar(any(Concurso.class));

        // Deve disparar notificação em lote
        ArgumentCaptor<List<Concurso>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificacaoOutputPort, times(1)).enviarNotificacoes(captor.capture());

        List<Concurso> notificados = captor.getValue();
        assertThat(notificados).hasSize(1);
        assertThat(notificados.get(0).titulo()).isEqualTo("Analista de Sistemas - TI");
        assertThat(notificados.get(0).tecnologia()).isTrue();
        assertThat(notificados.get(0).prioritario()).isTrue();
    }

    @Test
    @DisplayName("Deveria descartar concurso que não é de TI: não salva no banco nem notifica")
    void deveDescartarConcursoSeNaoForTI() {
        Concurso concursoSaude = criarConcurso("c2", "Médico Cardiologista", "SC");

        when(estadoPrioritarioService.isPrioritario(any(), any())).thenReturn(true);
        when(filtroTecnologiaService.isTecnologia(concursoSaude.titulo(), "SC")).thenReturn(false);

        useCase.executar(List.of(concursoSaude));

        verify(concursoRepository, never()).salvar(any(Concurso.class));

        // NÃO deve disparar notificação para cargo que não é de TI
        verify(notificacaoOutputPort, never()).enviarNotificacoes(any());
        verify(notificacaoOutputPort, never()).enviarNotificacao(any());
    }

    @Test
    @DisplayName("Deveria ordenar os concursos de estados prioritários no topo da lista a ser notificada")
    void devePriorizarConcursosDeEstadosPrioritariosNaNotificacao() {
        Concurso tiComum = criarConcurso("c10", "Desenvolvedor Backend", "SP");
        Concurso tiPrioritario = criarConcurso("c11", "Analista de TI - Cuiabá", "MT");

        when(concursoRepository.existePorHash(any())).thenReturn(false);
        when(estadoPrioritarioService.isPrioritario("SP", "Desenvolvedor Backend")).thenReturn(false);
        when(estadoPrioritarioService.isPrioritario("MT", "Analista de TI - Cuiabá")).thenReturn(true);
        when(filtroTecnologiaService.isTecnologia(any(), any())).thenReturn(true);

        useCase.executar(List.of(tiComum, tiPrioritario));

        ArgumentCaptor<List<Concurso>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificacaoOutputPort, times(1)).enviarNotificacoes(captor.capture());

        List<Concurso> notificados = captor.getValue();
        assertThat(notificados).hasSize(2);
        // O prioritário deve vir no topo
        assertThat(notificados.get(0).titulo()).isEqualTo("Analista de TI - Cuiabá");
        assertThat(notificados.get(0).prioritario()).isTrue();
        assertThat(notificados.get(1).titulo()).isEqualTo("Desenvolvedor Backend");
        assertThat(notificados.get(1).prioritario()).isFalse();
    }

    @Test
    @DisplayName("Deveria ignorar concurso já existente no banco (hash duplicado)")
    void deveIgnorarConcursoDuplicado() {
        Concurso concurso = criarConcurso("c3", "Desenvolvedor Java", "DF");

        when(concursoRepository.existePorHash(concurso.hashDeduplicacao())).thenReturn(true);
        when(filtroTecnologiaService.isTecnologia(any(), any())).thenReturn(true);

        useCase.executar(List.of(concurso));

        verify(concursoRepository, never()).salvar(any());
        verify(notificacaoOutputPort, never()).enviarNotificacoes(any());
    }

    @Test
    @DisplayName("Deveria ignorar silenciosamente lista nula ou vazia")
    void deveIgnorarListaVaziaOuNula() {
        useCase.executar((List<Concurso>) null);
        useCase.executar(List.of());

        verifyNoInteractions(concursoRepository, notificacaoOutputPort);
    }
}
