package br.unifor.locadora.gateway.auth;

import br.unifor.locadora.gateway.config.KeycloakProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conversa com o Keycloak: login (token), logout (revoga a sessão) e cadastro de usuário. */
@Component
public class KeycloakClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
    };

    private final WebClient web;
    private final KeycloakProperties props;

    public KeycloakClient(KeycloakProperties props, WebClient.Builder builder) {
        this.props = props;
        this.web = builder.baseUrl(props.baseUrl()).build();
    }

    /** Login: troca usuário e senha por tokens. Senhas nunca são guardadas pelo gateway. */
    public Mono<Map<String, Object>> login(String usuario, String senha) {
        return web.post()
                .uri("/realms/{r}/protocol/openid-connect/token", props.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("client_id", props.clientId())
                        .with("username", usuario)
                        .with("password", senha)
                        .with("scope", "openid"))
                .retrieve()
                .onStatus(s -> s.is4xxClientError(),
                        r -> r.releaseBody().then(Mono.error(
                                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos"))))
                .bodyToMono(MAPA)
                .map(KeycloakClient::selecionarCamposDoToken)
                .onErrorMap(WebClientRequestException.class, KeycloakClient::indisponivel);
    }

    /** Logout: encerra a sessão no Keycloak invalidando o refresh token. */
    public Mono<Void> logout(String refreshToken) {
        return web.post()
                .uri("/realms/{r}/protocol/openid-connect/logout", props.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", props.clientId())
                        .with("refresh_token", refreshToken))
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorMap(WebClientRequestException.class, KeycloakClient::indisponivel);
    }

    /** Cadastro B2C: cria o usuário no Keycloak (sem roles: o gateway trata "sem role" como CLIENTE). */
    public Mono<Void> registrar(String nome, String email, String senha) {
        String[] partes = nome.strip().split("\\s+", 2);
        Map<String, Object> usuario = new LinkedHashMap<>();
        usuario.put("username", email);
        usuario.put("email", email);
        usuario.put("firstName", partes[0]);
        usuario.put("lastName", partes.length > 1 ? partes[1] : "-");
        usuario.put("enabled", true);
        usuario.put("emailVerified", true);
        usuario.put("credentials", List.of(Map.of("type", "password", "value", senha, "temporary", false)));

        return tokenAdministrativo().flatMap(token -> web.post()
                .uri("/admin/realms/{r}/users", props.realm())
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(usuario)
                .exchangeToMono(resp -> {
                    int status = resp.statusCode().value();
                    if (status == 201) {
                        return resp.releaseBody();
                    }
                    if (status == 409) {
                        return resp.releaseBody().then(Mono.<Void>error(
                                new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado")));
                    }
                    return resp.releaseBody().then(Mono.<Void>error(
                            new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "Não foi possível concluir o cadastro no provedor de identidade")));
                }))
                .onErrorMap(WebClientRequestException.class, KeycloakClient::indisponivel);
    }

    /** Token da conta de serviço do gateway (client credentials), usado só para criar usuários. */
    private Mono<String> tokenAdministrativo() {
        return web.post()
                .uri("/realms/{r}/protocol/openid-connect/token", props.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("client_id", props.adminClientId())
                        .with("client_secret", props.adminClientSecret()))
                .retrieve()
                .onStatus(s -> s.isError(),
                        r -> r.releaseBody().then(Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY, "Gateway sem permissão para cadastrar usuários"))))
                .bodyToMono(MAPA)
                .map(m -> String.valueOf(m.get("access_token")));
    }

    private static Map<String, Object> selecionarCamposDoToken(Map<String, Object> bruto) {
        Map<String, Object> saida = new LinkedHashMap<>();
        for (String campo : List.of("access_token", "expires_in", "refresh_token", "refresh_expires_in", "token_type")) {
            if (bruto.containsKey(campo)) {
                saida.put(campo, bruto.get(campo));
            }
        }
        return saida;
    }

    private static ResponseStatusException indisponivel(WebClientRequestException e) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provedor de identidade indisponível");
    }
}
