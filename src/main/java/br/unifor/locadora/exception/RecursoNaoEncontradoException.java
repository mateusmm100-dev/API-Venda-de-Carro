package br.unifor.locadora.exception;

import org.springframework.http.HttpStatus;

public class RecursoNaoEncontradoException extends ApiException {
    public RecursoNaoEncontradoException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
