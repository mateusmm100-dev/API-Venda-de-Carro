package br.unifor.locadora.service;

import br.unifor.locadora.domain.Usuario;
import br.unifor.locadora.dto.PerfilRequest;
import br.unifor.locadora.dto.UsuarioResponse;
import br.unifor.locadora.exception.ConflitoException;
import br.unifor.locadora.exception.RegraNegocioException;
import br.unifor.locadora.repository.UsuarioRepository;
import br.unifor.locadora.security.AuthenticatedUser;
import br.unifor.locadora.util.CpfValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;

    /**
     * Garante que o usuário vindo do gateway tenha um perfil local. No primeiro acesso o perfil
     * é criado com os dados básicos do B2C (CPF e CNH ficam vazios até ele completar o cadastro).
     * O perfil (CLIENTE/STAFF/ADMIN) enviado pelo gateway é sempre a fonte da verdade.
     */
    @Transactional
    public Usuario obterOuProvisionar(AuthenticatedUser u) {
        return repository.findByExternalId(u.externalId())
                .map(existente -> {
                    if (existente.getPerfil() != u.role()) {
                        existente.setPerfil(u.role());
                    }
                    return existente;
                })
                .orElseGet(() -> repository.save(novo(u)));
    }

    @Transactional
    public UsuarioResponse meuPerfil(AuthenticatedUser u) {
        return UsuarioResponse.completo(obterOuProvisionar(u));
    }

    @Transactional
    public UsuarioResponse atualizarPerfil(AuthenticatedUser u, PerfilRequest req) {
        String cpf = req.cpf().replaceAll("\\D", "");
        if (!CpfValidator.valido(cpf)) {
            throw new RegraNegocioException("CPF inválido");
        }
        Usuario usuario = obterOuProvisionar(u);
        if (repository.existsByCpfAndIdNot(cpf, usuario.getId())) {
            throw new ConflitoException("Este CPF já está cadastrado para outro usuário");
        }
        usuario.setNome(req.nome().strip());
        usuario.setCpf(cpf);
        usuario.setTelefone(req.telefone());
        usuario.setCnh(req.cnh());
        return UsuarioResponse.completo(usuario);
    }

    /** Listagem administrativa: CPF e CNH sempre mascarados. */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return repository.findAll(Sort.by("nome")).stream().map(UsuarioResponse::mascarado).toList();
    }

    private Usuario novo(AuthenticatedUser u) {
        Usuario usuario = new Usuario();
        usuario.setExternalId(u.externalId());
        String email = (u.email() == null) ? u.externalId() + "@b2c.local" : u.email();
        usuario.setEmail(email);
        usuario.setNome(u.nome() != null ? u.nome() : email.split("@")[0]);
        usuario.setPerfil(u.role());
        return usuario;
    }
}
