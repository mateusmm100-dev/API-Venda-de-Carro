package br.unifor.locadora.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DevolucaoRequest(
        @NotNull(message = "A quilometragem final é obrigatória")
        @Min(value = 0, message = "A quilometragem não pode ser negativa")
        Integer quilometragemFinal,

        /** Opcional: se omitida, usa a data de hoje. */
        LocalDate dataDevolucaoReal,

        @Size(max = 500, message = "As observações devem ter no máximo 500 caracteres")
        String observacoes) {
}
