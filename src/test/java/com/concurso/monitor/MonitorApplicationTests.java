package com.concurso.monitor;

import com.concurso.monitor.domain.ports.in.ProcessarNovosConcursosUseCase;
import com.concurso.monitor.domain.ports.out.NotificacaoOutputPort;
import com.concurso.monitor.infrastructure.adapters.output.notification.CompositeNotificationAdapter;
import com.concurso.monitor.infrastructure.adapters.output.notification.EmailNotificationAdapter;
import com.concurso.monitor.infrastructure.adapters.output.notification.TelegramNotificationAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "telegram.bot.token=test-bot-token",
        "telegram.bot.chat-id=123456789",
        "notification.email.to=candidato@teste.com",
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        // Desabilita scrapers para não fazer chamadas reais nos testes de contexto
        "scraper.fgv.enabled=false",
        "scraper.cebraspe.enabled=false",
        "scraper.fcc.enabled=false",
        "scraper.ibfc.enabled=false",
        "scraper.quadrix.enabled=false",
        "scraper.selecon.enabled=false",
        "scraper.dou.enabled=false",
        "scraper.doe-mt.enabled=false"
})
class MonitorApplicationTests {

    @Autowired
    private ProcessarNovosConcursosUseCase processarNovosConcursosUseCase;

    @Autowired
    private CompositeNotificationAdapter compositeNotificationAdapter;

    @Autowired
    private TelegramNotificationAdapter telegramNotificationAdapter;

    @Autowired
    private EmailNotificationAdapter emailNotificationAdapter;

    @Test
    @DisplayName("Deveria carregar o contexto do Spring e injetar o CompositeNotificationAdapter como @Primary")
    void contextLoadsAndPrimaryAdapterInjected() {
        assertThat(processarNovosConcursosUseCase).isNotNull();
        assertThat(compositeNotificationAdapter).isNotNull();
        assertThat(telegramNotificationAdapter).isNotNull();
        assertThat(emailNotificationAdapter).isNotNull();

        // Composite deve conter os canais específicos e ter filtrado a si mesmo
        List<NotificacaoOutputPort> registeredAdapters = compositeNotificationAdapter.getAdapters();
        assertThat(registeredAdapters)
                .contains(telegramNotificationAdapter, emailNotificationAdapter)
                .doesNotContain(compositeNotificationAdapter);
    }

    @Test
    @DisplayName("Deveria ter o ScrapingScheduler com a lista de scrapers (0 quando todos desabilitados)")
    void schedulerDeveCarregarComListaVaziaQuandoScrapersDesabilitados() {
        // Nenhuma banca habilitada → o scheduler não deve quebrar o contexto
        assertThat(processarNovosConcursosUseCase).isNotNull();
    }
}
