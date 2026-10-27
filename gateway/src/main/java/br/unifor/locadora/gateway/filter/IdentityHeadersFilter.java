package br.unifor.locadora.gateway.filter;

import br.unifor.locadora.gateway.config.GatewayProperties;
import br.unifor.locadora.gateway.ratelimit.RateLimiter;
import br.unifor.locadora.gateway.security.TokenDenylist;
import br.unifor.locadora.gateway.web.JsonErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Coração do gateway. Para cada requisição que vai ao backend (o JWT já foi validado pelo Spring Security):
 *  1. confere se o token não foi revogado no logout;
 *  2. aplica o rate limit por usuário (429);
 *  3. REMOVE qualquer header X-User-*, X-Gateway-Secret e Authorization que o cliente tenha mandado;
 *  4. INJETA o segredo compartilhado e a identidade (id, perfil, e-mail, nome) tirada do token.
 *
 * Assim o backend nunca vê o token e nunca recebe uma identidade que o cliente possa ter forjado.
 */
@Component
public class IdentityHeadersFilter implements GlobalFilter, Ordered {

    static final String H_SECRET = "X-Gateway-Secret";
    static final String H_USER_ID = "X-User-Id";
    static final String H_USER_ROLE = "X-User-Role";
    static final String H_USER_EMAIL = "X-User-Email";
    static final String H_USER_NAME = "X-User-Name";

    private final String segredo;
    private final TokenDenylist denylist;
    private final RateLimiter limiter;
    private final int limitePorMinuto;

    public IdentityHeadersFilter(GatewayProperties props, TokenDenylist denylist, RateLimiter limiter,
                                 @Value("${ratelimit.api-per-minute}") int limitePorMinuto) {
        if (props.sharedSecret() == null || props.sharedSecret().isBlank()) {
            throw new IllegalStateException("gateway.shared-secret (GATEWAY_SHARED_SECRET) não pode ser vazio");
        }
        this.segredo = props.sharedSecret();
        this.denylist = denylist;
        this.limiter = limiter;
        this.limitePorMinuto = limitePorMinuto;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(opt -> opt.isPresent()
                        ? encaminhar(opt.get().getToken(), exchange, chain)
                        : JsonErrors.write(exchange, HttpStatus.UNAUTHORIZED, "Autenticação necessária"));
    }

    private Mono<Void> encaminhar(Jwt jwt, ServerWebExchange exchange, GatewayFilterChain chain) {
        if (denylist.estaRevogado(jwt.getId())) {
            return JsonErrors.write(exchange, HttpStatus.UNAUTHORIZED, "Token revogado (logout realizado)");
        }

        RateLimiter.Result r = limiter.tryAcquire("user:" + jwt.getSubject(), limitePorMinuto, Duration.ofMinutes(1));
        if (!r.allowed()) {
            exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(r.retryAfterSeconds()));
            return JsonErrors.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de requisições excedido. Tente novamente em " + r.retryAfterSeconds() + " segundos");
        }

        String email = jwt.getClaimAsString("email");
        String nome = jwt.getClaimAsString("name");
        if (nome == null) {
            nome = jwt.getClaimAsString("preferred_username");
        }
        final String nomeFinal = nome;

        ServerHttpRequest request = exchange.getRequest().mutate().headers(h -> {
            h.keySet().removeIf(k -> k.toLowerCase().startsWith("x-user-")
                    || k.equalsIgnoreCase(H_SECRET)
                    || k.equalsIgnoreCase(HttpHeaders.AUTHORIZATION));
            h.set(H_SECRET, segredo);
            h.set(H_USER_ID, jwt.getSubject());
            h.set(H_USER_ROLE, resolverPerfil(jwt));
            if (email != null && !email.isBlank()) {
                h.set(H_USER_EMAIL, sanitizar(email));
            }
            if (nomeFinal != null && !nomeFinal.isBlank()) {
                h.set(H_USER_NAME, sanitizar(nomeFinal));
            }
        }).build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * Perfil vem das roles do Keycloak (claim realm_access.roles). ADMIN > STAFF > CLIENTE.
     * Sem nenhuma role reconhecida, o usuário é CLIENTE (menor privilégio).
     */
    public static String resolverPerfil(Jwt jwt) {
        Map<String, Object> realm = jwt.getClaim("realm_access");
        Collection<?> roles = List.of();
        if (realm != null && realm.get("roles") instanceof Collection<?> c) {
            roles = c;
        }
        if (roles.contains("ADMIN")) {
            return "ADMIN";
        }
        if (roles.contains("STAFF")) {
            return "STAFF";
        }
        return "CLIENTE";
    }

    /** Headers HTTP devem ser ASCII simples: remove acentos e quebras de linha (evita injeção de header). */
    static String sanitizar(String valor) {
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.replaceAll("[^\\x20-\\x7E]", "").strip();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
