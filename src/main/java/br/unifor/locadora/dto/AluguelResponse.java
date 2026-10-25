package br.unifor.locadora.dto;

import br.unifor.locadora.domain.Aluguel;
import br.unifor.locadora.domain.AluguelStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AluguelResponse(
        Long id,
        Long clienteId,
        String clienteNome,
        Long carroId,
        String carroPlaca,
        String carroModelo,
        LocalDate dataRetirada,
        LocalDate dataDevolucaoPrevista,
        LocalDate dataDevolucaoReal,
        BigDecimal valorTotal,
        AluguelStatus status,
        String observacoes) {

    /** Deve ser chamado dentro de uma transação (as associações são LAZY). */
    public static AluguelResponse from(Aluguel a) {
        return new AluguelResponse(
                a.getId(),
                a.getCliente().getId(),
                a.getCliente().getNome(),
                a.getCarro().getId(),
                a.getCarro().getPlaca(),
                a.getCarro().getModelo(),
                a.getDataRetirada(),
                a.getDataDevolucaoPrevista(),
                a.getDataDevolucaoReal(),
                a.getValorTotal(),
                a.getStatus(),
                a.getObservacoes());
    }
}
