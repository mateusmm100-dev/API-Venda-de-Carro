package br.unifor.locadora.gateway.config;

import br.unifor.locadora.gateway.web.JsonErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Regras de entrada do gateway: tudo exige um JWT válido emitido pelo Keycloak,
 * exceto login, cadastro e health check. Sem token (ou token inválido/expirado) -> 401 em JSON.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(a -> a
                        .pathMatchers("/auth/login", "/auth/register", "/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> JsonErrors.write(exchange,
                                HttpStatus.UNAUTHORIZED,
                                "Autenticação necessária: token ausente, inválido ou expirado"))
                        .accessDeniedHandler((exchange, ex) -> JsonErrors.write(exchange,
                                HttpStatus.FORBIDDEN, "Acesso negado")))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${cors.allowed-origins}") String origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> lista = Arrays.stream(origins.split(",")).map(String::trim).toList();
        cfg.setAllowedOriginPatterns(lista);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
