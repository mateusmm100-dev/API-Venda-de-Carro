package br.unifor.locadora.repository;

import br.unifor.locadora.domain.Carro;
import br.unifor.locadora.domain.CarroStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarroRepository extends JpaRepository<Carro, Long> {

    List<Carro> findByStatusOrderByModelo(CarroStatus status);

    boolean existsByPlaca(String placa);

    boolean existsByPlacaAndIdNot(String placa, Long id);
}
