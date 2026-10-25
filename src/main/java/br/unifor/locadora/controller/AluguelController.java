package br.unifor.locadora.controller;

import br.unifor.locadora.domain.AluguelStatus;
import br.unifor.locadora.dto.AluguelCreateRequest;
import br.unifor.locadora.dto.AluguelResponse;
import br.unifor.locadora.dto.DevolucaoRequest;
import br.unifor.locadora.security.AuthenticatedUser;
import br.unifor.locadora.service.AluguelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/alugueis")
@RequiredArgsConstructor
public class AluguelController {

    private final AluguelService service;

    /** Somente CLIENTE cria aluguel (para si mesmo). */
    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<AluguelResponse> criar(@AuthenticationPrincipal AuthenticatedUser usuario,
                                                 @Valid @RequestBody AluguelCreateRequest req) {
        AluguelResponse criado = service.criar(usuario, req);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    /** Qualquer usuário autenticado: devolve apenas os aluguéis dele. */
    @GetMapping("/meus")
    public List<AluguelResponse> meus(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.listarMeus(usuario);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public List<AluguelResponse> listar(@RequestParam(required = false) AluguelStatus status) {
        return service.listarTodos(status);
    }

    /** Dono do aluguel, STAFF ou ADMIN. */
    @GetMapping("/{id}")
    public AluguelResponse buscar(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.buscar(id, usuario);
    }

    /** Dono do aluguel (enquanto RESERVADO), STAFF ou ADMIN. */
    @PatchMapping("/{id}/cancelar")
    public AluguelResponse cancelar(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.cancelar(id, usuario);
    }

    @PatchMapping("/{id}/retirada")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public AluguelResponse retirada(@PathVariable Long id) {
        return service.registrarRetirada(id);
    }

    @PatchMapping("/{id}/devolucao")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public AluguelResponse devolucao(@PathVariable Long id, @Valid @RequestBody DevolucaoRequest req) {
        return service.registrarDevolucao(id, req);
    }
}
