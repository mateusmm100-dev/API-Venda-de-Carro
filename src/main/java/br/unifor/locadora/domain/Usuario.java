package br.unifor.locadora.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Perfil local do usuário. A identidade (login, senha) fica no provedor B2C / gateway;
 * aqui guardamos apenas os dados de negócio e os dados pessoais protegidos (CPF, CNH).
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador do usuário no provedor B2C (claim "sub"), enviado pelo gateway. */
    @Column(nullable = false, unique = true, length = 100)
    private String externalId;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 11)
    private String cpf;

    @Column(length = 20)
    private String telefone;

    @Column(length = 20)
    private String cnh;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role perfil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @PrePersist
    void aoPersistir() {
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }
    }
}
