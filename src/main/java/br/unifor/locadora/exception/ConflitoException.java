package br.unifor.locadora.exception;

import org.springframework.http.HttpStatus;

public class ConflitoException extends ApiException {
    public ConflitoException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
