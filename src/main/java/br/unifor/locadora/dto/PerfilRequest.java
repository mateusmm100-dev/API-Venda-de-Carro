package br.unifor.locadora.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PerfilRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "O CPF é obrigatório")
        @Pattern(regexp = "^[0-9.\\-]{11,14}$", message = "CPF em formato inválido")
        String cpf,

        @Pattern(regexp = "^\\d{10,11}$", message = "Telefone deve ter 10 ou 11 dígitos (somente números)")
        String telefone,

        @NotBlank(message = "A CNH é obrigatória")
        @Pattern(regexp = "^\\d{11}$", message = "A CNH deve ter 11 dígitos")
        String cnh) {
}
