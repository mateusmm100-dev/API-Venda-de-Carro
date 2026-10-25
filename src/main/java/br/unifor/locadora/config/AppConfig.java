package br.unifor.locadora.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /** Relógio da aplicação no fuso da locadora (evita erro de data em servidores em UTC). */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("America/Fortaleza"));
    }
}
