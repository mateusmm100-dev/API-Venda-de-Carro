package br.unifor.locadora.dto;

import br.unifor.locadora.integracao.FipeResponse;

public record CarroDetalhesResponse(
        CarroResponse carro,
        FipeResponse fipe
) {
}
