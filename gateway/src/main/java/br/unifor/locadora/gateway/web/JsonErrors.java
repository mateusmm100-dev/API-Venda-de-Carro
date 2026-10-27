package br.unifor.locadora.gateway.web;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Escreve erros no mesmo formato JSON do backend (timestamp, status, error, message, path). */
public final class JsonErrors {

    private JsonErrors() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String mensagem) {
        ServerHttpResponse res = exchange.getResponse();
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"timestamp\":\"" + Instant.now() + "\""
                + ",\"status\":" + status.value()
                + ",\"error\":\"" + escapar(status.getReasonPhrase()) + "\""
                + ",\"message\":\"" + escapar(mensagem) + "\""
                + ",\"path\":\"" + escapar(exchange.getRequest().getPath().value()) + "\"}";
        DataBuffer buffer = res.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return res.writeWith(Mono.just(buffer));
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
