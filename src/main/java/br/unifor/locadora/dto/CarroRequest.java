package br.unifor.locadora.dto;

import br.unifor.locadora.domain.Categoria;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CarroRequest(
        @NotBlank(message = "A placa é obrigatória")
        @Pattern(regexp = "^[A-Za-z]{3}[0-9][A-Za-z0-9][0-9]{2}$",
                message = "Placa inválida (use o formato ABC1234 ou ABC1D23)")
        String placa,

        @NotBlank(message = "A marca é obrigatória")
        @Size(max = 60, message = "A marca deve ter no máximo 60 caracteres")
        String marca,

        @NotBlank(message = "O modelo é obrigatório")
        @Size(max = 80, message = "O modelo deve ter no máximo 80 caracteres")
        String modelo,

        @NotNull(message = "O ano é obrigatório")
        @Min(value = 1990, message = "O ano deve ser 1990 ou posterior")
        @Max(value = 2100, message = "Ano inválido")
        Integer ano,

        @NotNull(message = "A categoria é obrigatória (ECONOMICO, SEDAN, SUV ou LUXO)")
        Categoria categoria,

        @NotNull(message = "O valor da diária é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor da diária deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "Valor da diária inválido (máximo 2 casas decimais)")
        BigDecimal valorDiaria,

        @NotNull(message = "A quilometragem é obrigatória")
        @Min(value = 0, message = "A quilometragem não pode ser negativa")
        Integer quilometragem) {
}
