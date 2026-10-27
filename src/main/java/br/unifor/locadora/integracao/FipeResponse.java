package br.unifor.locadora.integracao;

public record FipeResponse(
        boolean disponivel,
        String marca,
        String modelo,
        Integer anoModelo,
        String valor,
        String mesReferencia,
        String codigoFipe,
        String mensagem
) {
}
