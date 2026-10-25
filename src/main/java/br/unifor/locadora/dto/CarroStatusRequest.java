package br.unifor.locadora.dto;

import br.unifor.locadora.domain.CarroStatus;
import jakarta.validation.constraints.NotNull;

public record CarroStatusRequest(
        @NotNull(message = "O status é obrigatório (DISPONIVEL, ALUGADO ou MANUTENCAO)")
        CarroStatus status) {
}
