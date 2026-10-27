package br.unifor.locadora.gateway.auth;

import br.unifor.locadora.gateway.security.TokenDenylist;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Endpoints de autenticação expostos pelo gateway:
 *  POST /auth/register  (público)       cria a conta do cliente B2C
 *  POST /auth/login     (público)       devolve access_token + refresh_token
 *  POST /auth/logout    (autenticado)   revoga o token e encerra a sessão
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    public record LoginRequest(String username, String password) {
    }

    public record RegisterRequest(String nome, String email, String senha) {
    }

    public record LogoutRequest(String refresh_token) {
    }

    private final KeycloakClient keycloak;
    private final TokenDenylist denylist;

    public AuthController(KeycloakClient keycloak, TokenDenylist denylist) {
        this.keycloak = keycloak;
        this.denylist = denylist;
    }

    @PostMapping("/login")
    public Mono<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (vazio(req.username()) || vazio(req.password())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe username e password");
        }
        return keycloak.login(req.username().strip(), req.password());
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, String>>> register(@RequestBody RegisterRequest req) {
        if (vazio(req.nome()) || vazio(req.email()) || vazio(req.senha())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe nome, email e senha");
        }
        if (!req.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "E-mail inválido");
        }
        if (req.senha().length() < 8) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A senha deve ter pelo menos 8 caracteres");
        }
        return keycloak.registrar(req.nome(), req.email().strip().toLowerCase(), req.senha())
                .then(Mono.just(ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("message", "Cadastro realizado. Faça login em /auth/login"))));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@AuthenticationPrincipal Jwt jwt,
                                             @RequestBody(required = false) LogoutRequest req) {
        // 1) o access token atual deixa de valer já (mesmo antes de expirar)
        denylist.revogar(jwt.getId(), jwt.getExpiresAt());
        // 2) a sessão no Keycloak é encerrada: o refresh token não renova mais
        Mono<Void> encerrarSessao = (req != null && !vazio(req.refresh_token()))
                ? keycloak.logout(req.refresh_token())
                : Mono.empty();
        return encerrarSessao.then(Mono.just(ResponseEntity.noContent().build()));
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
