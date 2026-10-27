package br.unifor.locadora.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityHeadersFilterTest {

    private static Jwt jwtCom(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token").header("alg", "none").subject("user-1");
        claims.forEach(b::claim);
        return b.build();
    }

    @Test
    void adminTemPrioridadeSobreStaffECliente() {
        Jwt jwt = jwtCom(Map.of("realm_access", Map.of("roles", List.of("CLIENTE", "STAFF", "ADMIN"))));
        assertEquals("ADMIN", IdentityHeadersFilter.resolverPerfil(jwt));
    }

    @Test
    void staffQuandoNaoHaAdmin() {
        Jwt jwt = jwtCom(Map.of("realm_access", Map.of("roles", List.of("STAFF"))));
        assertEquals("STAFF", IdentityHeadersFilter.resolverPerfil(jwt));
    }

    @Test
    void semRoleReconhecidaViraClienteMenorPrivilegio() {
        Jwt jwt = jwtCom(Map.of("realm_access", Map.of("roles", List.of("offline_access"))));
        assertEquals("CLIENTE", IdentityHeadersFilter.resolverPerfil(jwt));
    }

    @Test
    void semClaimDeRolesViraCliente() {
        assertEquals("CLIENTE", IdentityHeadersFilter.resolverPerfil(jwtCom(Map.of("email", "a@b.com"))));
    }

    @Test
    void sanitizarRemoveAcentosEQuebrasDeLinha() {
        assertEquals("Joao Lima", IdentityHeadersFilter.sanitizar("João Lima"));
        assertEquals("EvilX-Admin: yes", IdentityHeadersFilter.sanitizar("Evil\r\nX-Admin: yes"));
    }
}
