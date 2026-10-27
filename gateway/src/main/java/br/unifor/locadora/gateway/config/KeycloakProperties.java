package br.unifor.locadora.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dados de acesso ao provedor de identidade (Keycloak). */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(String baseUrl, String realm, String clientId,
                                 String adminClientId, String adminClientSecret) {
}
