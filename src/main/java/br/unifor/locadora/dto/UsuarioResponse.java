package br.unifor.locadora.dto;

import br.unifor.locadora.domain.Role;
import br.unifor.locadora.domain.Usuario;
import br.unifor.locadora.util.Mascara;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        String cpf,
        String telefone,
        String cnh,
        Role perfil,
        LocalDateTime dataCadastro) {

    /** Dados completos: usado apenas para o próprio usuário ver o seu perfil. */
    public static UsuarioResponse completo(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getCpf(), u.getTelefone(),
                u.getCnh(), u.getPerfil(), u.getDataCadastro());
    }

    /** Dados pessoais mascarados: usado nas listagens administrativas. */
    public static UsuarioResponse mascarado(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), Mascara.cpf(u.getCpf()),
                u.getTelefone(), Mascara.cnh(u.getCnh()), u.getPerfil(), u.getDataCadastro());
    }
}
