package br.unifor.locadora.gateway.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Lista de tokens revogados (pelo jti). O logout coloca o access token aqui até a data de expiração,
 * então ele deixa de funcionar imediatamente, mesmo ainda não tendo expirado.
 *
 * Limitação: fica em memória; se o gateway reiniciar, a lista é perdida (o token continua limitado
 * ao tempo de vida curto de 5 minutos).
 */
@Component
public class TokenDenylist {

    private final Map<String, Instant> revogados = new HashMap<>();

    public synchronized void revogar(String jti, Instant expiraEm) {
        if (jti == null) {
            return;
        }
        limparExpirados();
        revogados.put(jti, expiraEm != null ? expiraEm : Instant.now().plusSeconds(3600));
    }

    public synchronized boolean estaRevogado(String jti) {
        if (jti == null) {
            return false;
        }
        Instant expira = revogados.get(jti);
        if (expira == null) {
            return false;
        }
        if (expira.isBefore(Instant.now())) {
            revogados.remove(jti);
            return false;
        }
        return true;
    }

    private void limparExpirados() {
        Instant agora = Instant.now();
        revogados.values().removeIf(e -> e.isBefore(agora));
    }
}
