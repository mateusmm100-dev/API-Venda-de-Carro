package br.unifor.locadora.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Segredo compartilhado com o backend (vai no header X-Gateway-Secret). */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(String sharedSecret) {
}
