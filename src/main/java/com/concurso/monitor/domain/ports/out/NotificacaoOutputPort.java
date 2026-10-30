package com.concurso.monitor.domain.ports.out;

import com.concurso.monitor.domain.model.Concurso;

import java.util.List;

public interface NotificacaoOutputPort {

    /**
     * Envia notificação individual sobre um concurso.
     */
    void enviarNotificacao(Concurso concurso);

    /**
     * Envia notificações em lote sobre uma lista de concursos detectados em uma execução.
     * Permite que canais como e-mail unifiquem múltiplos concursos em um ou dois disparos agrupados.
     */
    default void enviarNotificacoes(List<Concurso> concursos) {
        if (concursos != null && !concursos.isEmpty()) {
            for (Concurso concurso : concursos) {
                enviarNotificacao(concurso);
            }
        }
    }
}
