package com.concurso.monitor.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroTecnologiaServiceTest {

    private final FiltroTecnologiaService service = new FiltroTecnologiaService(true, null);

    @Test
    @DisplayName("Deveria identificar cargos de tecnologia por termos compostos comuns")
    void deveIdentificarPorTermosCompostos() {
        assertThat(service.isTecnologia("Analista de Sistemas - Pleno", "")).isTrue();
        assertThat(service.isTecnologia("Concurso SEFAZ - Tecnologia da Informação", "")).isTrue();
        assertThat(service.isTecnologia("Desenvolvedor de Software Backend", "")).isTrue();
        assertThat(service.isTecnologia("Engenharia de Software e Banco de Dados", "")).isTrue();
        assertThat(service.isTecnologia("Ciência da Computação e Redes de Computadores", "")).isTrue();
        assertThat(service.isTecnologia("Programador Web Full Stack", "")).isTrue();
        assertThat(service.isTecnologia("Técnico em Informática", "")).isTrue();
        assertThat(service.isTecnologia("Especialista em Segurança da Informação", "")).isTrue();
        assertThat(service.isTecnologia("Cientista de Dados", "")).isTrue();
    }

    @Test
    @DisplayName("Deveria identificar siglas de TI isoladas com limite de palavra")
    void deveIdentificarSiglasIsoladas() {
        assertThat(service.isTecnologia("Analista Judiciário - Especialidade TI", "")).isTrue();
        assertThat(service.isTecnologia("Técnico de Apoio - T.I.", "")).isTrue();
        assertThat(service.isTecnologia("Gestão de TIC", "")).isTrue();
        assertThat(service.isTecnologia("Analista DBA", "")).isTrue();
    }

    @Test
    @DisplayName("Não deveria dar falsos positivos em palavras que contenham as letras 'TI' como substring")
    void naoDeveDarFalsoPositivoEmPalavrasComuns() {
        assertThat(service.isTecnologia("Prefeitura de Curitiba - Auditor Fiscal", "")).isFalse();
        assertThat(service.isTecnologia("Gratificação de Produtividade para Médicos", "")).isFalse();
        assertThat(service.isTecnologia("Concurso Parintins - Professor de História", "")).isFalse();
        assertThat(service.isTecnologia("Participação Social e Direitos Humanos", "")).isFalse();
        assertThat(service.isTecnologia("Polícia Militar - Soldado Combatente", "")).isFalse();
        assertThat(service.isTecnologia("Tribunal de Contas - Auditor de Controle Externo - Direito", "")).isFalse();
    }

    @Test
    @DisplayName("Deveria aceitar todos os concursos quando o filtro estiver desabilitado")
    void deveAceitarTudoQuandoDesabilitado() {
        FiltroTecnologiaService desabilitado = new FiltroTecnologiaService(false, null);
        assertThat(desabilitado.isTecnologia("Médico Veterinário", "")).isTrue();
    }

    @Test
    @DisplayName("Deveria reconhecer termos customizados configurados pelo usuário")
    void deveReconhecerTermosCustomizados() {
        FiltroTecnologiaService custom = new FiltroTecnologiaService(true, "geoprocessamento,bioinformatica");
        assertThat(custom.isTecnologia("Analista em Geoprocessamento", "")).isTrue();
        assertThat(custom.isTecnologia("Pesquisador em Bioinformática", "")).isTrue();
    }
}
