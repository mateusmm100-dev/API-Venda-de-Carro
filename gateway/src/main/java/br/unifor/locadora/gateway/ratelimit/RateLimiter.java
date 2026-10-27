package br.unifor.locadora.gateway.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Rate limiting por janela fixa, em memória: no máximo N requisições por chave dentro da janela.
 * Estourou o limite -> o chamador responde 429 com o header Retry-After.
 *
 * Limitação: o contador vive na memória de UMA instância do gateway. Com várias instâncias seria
 * preciso um armazenamento compartilhado (ex.: Redis).
 */
@Component
public class RateLimiter {

    public record Result(boolean allowed, long retryAfterSeconds) {
    }

    private static final class Janela {
        final long inicio;
        int contagem;

        Janela(long inicio) {
            this.inicio = inicio;
        }
    }

    private static final int LIMITE_DE_CHAVES = 10_000;

    private final Map<String, Janela> janelas = new HashMap<>();

    public synchronized Result tryAcquire(String chave, int limite, Duration duracao) {
        long agora = System.currentTimeMillis();
        long tamanho = duracao.toMillis();

        if (janelas.size() > LIMITE_DE_CHAVES) {
            janelas.values().removeIf(j -> agora - j.inicio >= tamanho);
        }

        Janela janela = janelas.get(chave);
        if (janela == null || agora - janela.inicio >= tamanho) {
            janela = new Janela(agora);
            janelas.put(chave, janela);
        }
        janela.contagem++;

        if (janela.contagem <= limite) {
            return new Result(true, 0);
        }
        long restanteMs = janela.inicio + tamanho - agora;
        return new Result(false, Math.max(1, (restanteMs + 999) / 1000));
    }
}
