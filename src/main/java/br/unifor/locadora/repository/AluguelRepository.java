package br.unifor.locadora.repository;

import br.unifor.locadora.domain.Aluguel;
import br.unifor.locadora.domain.AluguelStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AluguelRepository extends JpaRepository<Aluguel, Long> {

    List<Aluguel> findByClienteIdOrderByDataRetiradaDesc(Long clienteId);

    List<Aluguel> findByStatus(AluguelStatus status, Sort sort);

    boolean existsByCarroId(Long carroId);

    /** Existe aluguel ativo do mesmo carro que se sobrepõe ao período [inicio, fim)? */
    @Query("""
            select count(a) > 0 from Aluguel a
            where a.carro.id = :carroId
              and a.status in :statuses
              and a.dataRetirada < :fim
              and a.dataDevolucaoPrevista > :inicio
            """)
    boolean existeConflito(@Param("carroId") Long carroId,
                           @Param("inicio") LocalDate inicio,
                           @Param("fim") LocalDate fim,
                           @Param("statuses") Collection<AluguelStatus> statuses);
}
