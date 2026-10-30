package com.concurso.monitor.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoPrioritarioServiceTest {

    private final EstadoPrioritarioService service = new EstadoPrioritarioService("MT,SC,PR,DF,GO");

    @Test
    @DisplayName("Deveria identificar corretamente concursos prioritários por sigla de UF")
    void deveIdentificarPorSigla() {
        assertThat(service.isPrioritario("MT", "Concurso Câmara de Cuiabá")).isTrue();
        assertThat(service.isPrioritario("Nacional", "CRECI - SC 01/2026")).isTrue();
        assertThat(service.isPrioritario("Nacional", "Polícia Civil - PR")).isTrue();
        assertThat(service.isPrioritario("DF", "Analista TCU")).isTrue();
        assertThat(service.isPrioritario("Nacional", "Prefeitura de Goiânia (GO)")).isTrue();
    }

    @Test
    @DisplayName("Deveria identificar corretamente concursos prioritários por nome de estado por extenso")
    void deveIdentificarPorNomeExtenso() {
        assertThat(service.isPrioritario("Mato Grosso", "Auditor SEFAZ")).isTrue();
        assertThat(service.isPrioritario("Nacional", "Polícia Militar de Santa Catarina")).isTrue();
        assertThat(service.isPrioritario("Nacional", "Tribunal de Justiça do Paraná")).isTrue();
        assertThat(service.isPrioritario("Distrito Federal", "Defensoria Pública")).isTrue();
        assertThat(service.isPrioritario("Nacional", "Secretaria de Saúde de Goiás")).isTrue();
    }

    @Test
    @DisplayName("Deveria retornar false para estados que não constam na lista prioritária")
    void deveRetornarFalseParaNaoPrioritarios() {
        assertThat(service.isPrioritario("SP", "Tribunal de Justiça de São Paulo")).isFalse();
        assertThat(service.isPrioritario("RJ", "Polícia Civil do Rio de Janeiro")).isFalse();
        assertThat(service.isPrioritario("Nacional", "TRF 1ª Região - Minas Gerais")).isFalse();
        assertThat(service.isPrioritario("Nacional", "Edital Nacional Geral")).isFalse();
    }

    @Test
    @DisplayName("Deveria permitir alterar a lista de estados prioritários via configuração")
    void devePermitirConfigurarUfsDiferentes() {
        EstadoPrioritarioService customService = new EstadoPrioritarioService("SP,RJ,MG");

        assertThat(customService.isPrioritario("SP", "Concurso TJ-SP")).isTrue();
        assertThat(customService.isPrioritario("MT", "Concurso Cuiabá")).isFalse();
    }
}
