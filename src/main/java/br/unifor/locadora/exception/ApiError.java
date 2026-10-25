package br.unifor.locadora.exception;

import java.time.Instant;
import java.util.List;

/** Formato padrão de todas as respostas de erro da API. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<CampoInvalido> campos) {

    public record CampoInvalido(String campo, String mensagem) {
    }
}
