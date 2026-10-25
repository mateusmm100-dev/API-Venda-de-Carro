package br.unifor.locadora.controller;

import br.unifor.locadora.dto.PerfilRequest;
import br.unifor.locadora.dto.UsuarioResponse;
import br.unifor.locadora.security.AuthenticatedUser;
import br.unifor.locadora.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService service;

    /** Somente ADMIN. CPF e CNH são devolvidos mascarados. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioResponse> listar() {
        return service.listar();
    }

    /** O próprio usuário vê seus dados completos. */
    @GetMapping("/me")
    public UsuarioResponse meuPerfil(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.meuPerfil(usuario);
    }

    /** O próprio usuário completa/atualiza nome, CPF, telefone e CNH. */
    @PutMapping("/me")
    public UsuarioResponse atualizarPerfil(@AuthenticationPrincipal AuthenticatedUser usuario,
                                           @Valid @RequestBody PerfilRequest req) {
        return service.atualizarPerfil(usuario, req);
    }
}
