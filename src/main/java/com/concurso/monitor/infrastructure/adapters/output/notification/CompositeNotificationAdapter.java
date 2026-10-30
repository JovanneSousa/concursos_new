package com.concurso.monitor.infrastructure.adapters.output.notification;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.out.NotificacaoOutputPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class CompositeNotificationAdapter implements NotificacaoOutputPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeNotificationAdapter.class);

    private final List<NotificacaoOutputPort> adapters;

    public CompositeNotificationAdapter(List<NotificacaoOutputPort> adapters) {
        this.adapters = adapters.stream()
                .filter(adapter -> adapter != this && !(adapter instanceof CompositeNotificationAdapter))
                .toList();
        log.info("CompositeNotificationAdapter inicializado com {} canais de notificação ativos.", this.adapters.size());
    }

    @Override
    public void enviarNotificacao(Concurso concurso) {
        enviarNotificacoes(List.of(concurso));
    }

    @Override
    public void enviarNotificacoes(List<Concurso> concursos) {
        if (concursos == null || concursos.isEmpty()) {
            return;
        }

        if (adapters.isEmpty()) {
            log.warn("Nenhum canal de notificação ativo configurado no CompositeNotificationAdapter.");
            return;
        }

        for (NotificacaoOutputPort adapter : adapters) {
            try {
                log.debug("Disparando lote de {} notificação(ões) através de [{}]",
                        concursos.size(), adapter.getClass().getSimpleName());
                adapter.enviarNotificacoes(concursos);
            } catch (Exception e) {
                log.error("Erro ao enviar notificações através do canal [{}]: {}",
                        adapter.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    public List<NotificacaoOutputPort> getAdapters() {
        return adapters;
    }
}
