package br.unifor.locadora.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "alugueis")
@Getter
@Setter
@NoArgsConstructor
public class Aluguel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carro_id", nullable = false)
    private Carro carro;

    @Column(nullable = false)
    private LocalDate dataRetirada;

    @Column(nullable = false)
    private LocalDate dataDevolucaoPrevista;

    private LocalDate dataDevolucaoReal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AluguelStatus status;

    @Column(length = 500)
    private String observacoes;
}
