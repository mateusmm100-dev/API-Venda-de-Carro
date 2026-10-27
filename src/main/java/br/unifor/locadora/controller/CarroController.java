package br.unifor.locadora.controller;

import br.unifor.locadora.domain.CarroStatus;
import br.unifor.locadora.dto.CarroRequest;
import br.unifor.locadora.dto.CarroResponse;
import br.unifor.locadora.dto.CarroStatusRequest;
import br.unifor.locadora.security.AuthenticatedUser;
import br.unifor.locadora.service.CarroService;
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
@RequestMapping("/carros")
@RequiredArgsConstructor
public class CarroController {

    private final CarroService service;

    /** CLIENTE, STAFF, ADMIN. Cliente vê apenas carros disponíveis. */
    @GetMapping
    public List<CarroResponse> listar(@AuthenticationPrincipal AuthenticatedUser usuario,
                                      @RequestParam(required = false) CarroStatus status) {
        return service.listar(usuario, status);
    }

    @GetMapping("/{id}")
    public CarroResponse buscar(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.buscar(id, usuario);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CarroResponse> criar(@Valid @RequestBody CarroRequest req) {
        CarroResponse criado = service.criar(req);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CarroResponse atualizar(@PathVariable Long id, @Valid @RequestBody CarroRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public CarroResponse alterarStatus(@PathVariable Long id, @Valid @RequestBody CarroStatusRequest req) {
        return service.alterarStatus(id, req.status());
    }
}
