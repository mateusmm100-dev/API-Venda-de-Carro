package br.unifor.locadora.repository;

import br.unifor.locadora.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByExternalId(String externalId);

    boolean existsByCpfAndIdNot(String cpf, Long id);
}
