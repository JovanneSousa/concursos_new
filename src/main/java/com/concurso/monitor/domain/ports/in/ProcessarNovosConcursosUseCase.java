package com.concurso.monitor.domain.ports.in;

import com.concurso.monitor.domain.model.Concurso;
import com.concurso.monitor.domain.ports.out.ConcursoRepository;
import com.concurso.monitor.domain.ports.out.NotificacaoOutputPort;
import com.concurso.monitor.domain.service.EstadoPrioritarioService;
import com.concurso.monitor.domain.service.FiltroTecnologiaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ProcessarNovosConcursosUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarNovosConcursosUseCase.class);

    private final NotificacaoOutputPort notificacaoOutputPort;
    private final ConcursoRepository concursoRepository;
    private final EstadoPrioritarioService estadoPrioritarioService;
    private final FiltroTecnologiaService filtroTecnologiaService;

    public ProcessarNovosConcursosUseCase(
            NotificacaoOutputPort notificacaoOutputPort,
            ConcursoRepository concursoRepository,
            EstadoPrioritarioService estadoPrioritarioService,
            FiltroTecnologiaService filtroTecnologiaService
    ) {
        this.notificacaoOutputPort = Objects.requireNonNull(notificacaoOutputPort, "notificacaoOutputPort não pode ser nulo");
        this.concursoRepository = Objects.requireNonNull(concursoRepository, "concursoRepository não pode ser nulo");
        this.estadoPrioritarioService = Objects.requireNonNull(estadoPrioritarioService, "estadoPrioritarioService não pode ser nulo");
        this.filtroTecnologiaService = Objects.requireNonNull(filtroTecnologiaService, "filtroTecnologiaService não pode ser nulo");
    }

    /**
     * Processa uma lista de concursos candidatos:
     * 1. Classifica prioridade de estado (MT, SC, PR, DF, GO, etc.).
     * 2. Classifica perfil de Tecnologia da Informação (TI).
     * 3. Salva todos os concursos novos no banco para deduplicação (evita reprocessamento contínuo).
     * 4. Dispara notificações APENAS para os editais com perfil de TI, em lote unificado.
     */
    public void executar(List<Concurso> candidatos) {
        if (candidatos == null || candidatos.isEmpty()) {
            log.debug("Nenhum concurso candidato para processar.");
            return;
        }

        // Classifica prioridade e perfil de TI, ordenando os prioritários no topo
        List<Concurso> classificados = candidatos.stream()
                .filter(Objects::nonNull)
                .map(c -> {
                    boolean prio = c.prioritario() || estadoPrioritarioService.isPrioritario(c.regiao(), c.titulo());
                    boolean ti = c.tecnologia() || filtroTecnologiaService.isTecnologia(c.titulo(), c.regiao());
                    return c.comPrioridade(prio).comTecnologia(ti);
                })
                .sorted(Comparator.comparing(Concurso::prioritario).reversed())
                .toList();

        int totalNovos = 0;
        List<Concurso> novosParaNotificar = new ArrayList<>();

        for (Concurso concurso : classificados) {
            try {
                if (concursoRepository.existePorHash(concurso.hashDeduplicacao())) {
                    log.debug("Concurso já conhecido no banco, ignorando: [{}]", concurso.titulo());
                    continue;
                }

                // Salva no banco de dados para garantir deduplicação
                concursoRepository.salvar(concurso);
                totalNovos++;

                // Filtra para disparo de notificação: somente vagas de Tecnologia
                if (concurso.tecnologia()) {
                    novosParaNotificar.add(concurso);
                    log.info("🎯 [TI DETECTADO] Concurso incluído para notificação: [{}] | Região: {} | Banca: {} | Prioritário: {}",
                            concurso.titulo(), concurso.regiao(), concurso.banca(), concurso.prioritario());
                } else {
                    log.debug("Concurso arquivado no banco, ignorado para notificação (não é perfil de TI): [{}]",
                            concurso.titulo());
                }
            } catch (Exception e) {
                log.error("Erro ao persistir/processar concurso [{}]: {}", concurso.titulo(), e.getMessage(), e);
            }
        }

        log.info("Varredura processada: {} novo(s) concurso(s) no banco. {} com perfil de Tecnologia para notificação.",
                totalNovos, novosParaNotificar.size());

        if (!novosParaNotificar.isEmpty()) {
            try {
                log.info("Disparando notificações unificadas para {} concurso(s) de TI...", novosParaNotificar.size());
                notificacaoOutputPort.enviarNotificacoes(novosParaNotificar);
                log.info("Notificações disparadas com sucesso.");
            } catch (Exception e) {
                log.error("Erro ao disparar lote de notificações: {}", e.getMessage(), e);
            }
        } else {
            log.info("Nenhum novo edital com perfil de Tecnologia da Informação nesta varredura.");
        }
    }

    public void executar(Concurso concurso) {
        if (concurso != null) {
            executar(List.of(concurso));
        }
    }

    public void processar(List<Concurso> candidatos) {
        executar(candidatos);
    }

    public void processar(Concurso concurso) {
        executar(concurso);
    }
}
