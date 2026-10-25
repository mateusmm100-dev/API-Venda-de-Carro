package br.unifor.locadora.dto;

import br.unifor.locadora.domain.Carro;
import br.unifor.locadora.domain.CarroStatus;
import br.unifor.locadora.domain.Categoria;

import java.math.BigDecimal;

public record CarroResponse(
        Long id,
        String placa,
        String marca,
        String modelo,
        Integer ano,
        Categoria categoria,
        BigDecimal valorDiaria,
        CarroStatus status,
        Integer quilometragem) {

    public static CarroResponse from(Carro c) {
        return new CarroResponse(c.getId(), c.getPlaca(), c.getMarca(), c.getModelo(), c.getAno(),
                c.getCategoria(), c.getValorDiaria(), c.getStatus(), c.getQuilometragem());
    }
}
