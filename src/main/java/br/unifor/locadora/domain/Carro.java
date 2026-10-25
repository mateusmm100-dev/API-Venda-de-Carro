package br.unifor.locadora.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "carros")
@Getter
@Setter
@NoArgsConstructor
public class Carro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 7)
    private String placa;

    @Column(nullable = false, length = 60)
    private String marca;

    @Column(nullable = false, length = 80)
    private String modelo;

    @Column(nullable = false)
    private Integer ano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Categoria categoria;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorDiaria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CarroStatus status;

    @Column(nullable = false)
    private Integer quilometragem;
}
