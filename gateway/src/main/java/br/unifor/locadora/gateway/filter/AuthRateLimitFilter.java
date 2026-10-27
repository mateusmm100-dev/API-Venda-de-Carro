package br.unifor.locadora.gateway.filter;

import br.unifor.locadora.gateway.ratelimit.RateLimiter;
import br.unifor.locadora.gateway.web.JsonErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Limita tentativas em /auth/login e /auth/register por IP (proteção contra força bruta e abuso).
 * Roda antes da segurança, porque essas rotas são públicas.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRateLimitFilter implements WebFilter {

    private final RateLimiter limiter;
    private final int limitePorMinuto;
    private final boolean confiarNoXForwardedFor;

    public AuthRateLimitFilter(RateLimiter limiter,
                               @Value("${ratelimit.auth-per-minute}") int limitePorMinuto,
                               @Value("${ratelimit.trust-forwarded-for}") boolean confiarNoXForwardedFor) {
        this.limiter = limiter;
        this.limitePorMinuto = limitePorMinuto;
        this.confiarNoXForwardedFor = confiarNoXForwardedFor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.equals("/auth/login") || path.equals("/auth/register")) {
            RateLimiter.Result r = limiter.tryAcquire("auth:" + ipDoCliente(exchange), limitePorMinuto,
                    Duration.ofMinutes(1));
            if (!r.allowed()) {
                exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(r.retryAfterSeconds()));
                return JsonErrors.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas tentativas. Tente novamente em " + r.retryAfterSeconds() + " segundos");
            }
        }
        return chain.filter(exchange);
    }

    private String ipDoCliente(ServerWebExchange exchange) {
        if (confiarNoXForwardedFor) {
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].strip();
            }
        }
        var remoto = exchange.getRequest().getRemoteAddress();
        return (remoto != null && remoto.getAddress() != null) ? remoto.getAddress().getHostAddress() : "desconhecido";
    }
}
