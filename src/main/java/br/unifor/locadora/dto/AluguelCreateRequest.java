package br.unifor.locadora.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AluguelCreateRequest(
        @NotNull(message = "O carro é obrigatório")
        Long carroId,

        @NotNull(message = "A data de retirada é obrigatória (AAAA-MM-DD)")
        LocalDate dataRetirada,

        @NotNull(message = "A data de devolução prevista é obrigatória (AAAA-MM-DD)")
        LocalDate dataDevolucaoPrevista,

        @Size(max = 500, message = "As observações devem ter no máximo 500 caracteres")
        String observacoes) {
}
