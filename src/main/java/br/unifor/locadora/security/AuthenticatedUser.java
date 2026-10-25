package br.unifor.locadora.security;

import br.unifor.locadora.domain.Role;

/**
 * Identidade do usuário autenticado, montada a partir dos headers que o gateway B2C
 * envia depois de validar o login. Disponível nos controllers via @AuthenticationPrincipal.
 */
public record AuthenticatedUser(String externalId, String email, String nome, Role role) {

    public boolean ehCliente() {
        return role == Role.CLIENTE;
    }
}
