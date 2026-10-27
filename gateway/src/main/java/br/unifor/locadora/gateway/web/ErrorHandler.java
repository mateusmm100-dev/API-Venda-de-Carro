package br.unifor.locadora.gateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Erros dos endpoints /auth/* no mesmo formato do restante da API. */
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> tratar(ResponseStatusException e, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", Instant.now().toString());
        corpo.put("status", status.value());
        corpo.put("error", status.getReasonPhrase());
        corpo.put("message", e.getReason() != null ? e.getReason() : status.getReasonPhrase());
        corpo.put("path", exchange.getRequest().getPath().value());
        return ResponseEntity.status(status).body(corpo);
    }
}
