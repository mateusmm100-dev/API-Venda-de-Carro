package br.unifor.locadora.service;

import br.unifor.locadora.domain.Carro;
import br.unifor.locadora.domain.CarroStatus;
import br.unifor.locadora.dto.CarroRequest;
import br.unifor.locadora.dto.CarroResponse;
import br.unifor.locadora.exception.ConflitoException;
import br.unifor.locadora.exception.RecursoNaoEncontradoException;
import br.unifor.locadora.exception.RegraNegocioException;
import br.unifor.locadora.repository.AluguelRepository;
import br.unifor.locadora.repository.CarroRepository;
import br.unifor.locadora.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CarroService {

    private final CarroRepository carroRepository;
    private final AluguelRepository aluguelRepository;

    /** Cliente enxerga só carros DISPONIVEL; STAFF e ADMIN veem todos (e podem filtrar por status). */
    @Transactional(readOnly = true)
    public List<CarroResponse> listar(AuthenticatedUser usuario, CarroStatus filtro) {
        List<Carro> carros;
        if (usuario.ehCliente()) {
            carros = carroRepository.findByStatusOrderByModelo(CarroStatus.DISPONIVEL);
        } else if (filtro != null) {
            carros = carroRepository.findByStatusOrderByModelo(filtro);
        } else {
            carros = carroRepository.findAll(Sort.by("modelo"));
        }
        return carros.stream().map(CarroResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CarroResponse buscar(Long id, AuthenticatedUser usuario) {
        Carro carro = obter(id);
        if (usuario.ehCliente() && carro.getStatus() != CarroStatus.DISPONIVEL) {
            // Para o cliente, um carro indisponível "não existe": não revela o estado da frota.
            throw naoEncontrado(id);
        }
        return CarroResponse.from(carro);
    }

    @Transactional
    public CarroResponse criar(CarroRequest req) {
        String placa = normalizarPlaca(req.placa());
        if (carroRepository.existsByPlaca(placa)) {
            throw new ConflitoException("Já existe um carro cadastrado com a placa " + placa);
        }
        Carro carro = new Carro();
        carro.setStatus(CarroStatus.DISPONIVEL);
        preencher(carro, req, placa);
        return CarroResponse.from(carroRepository.save(carro));
    }

    @Transactional
    public CarroResponse atualizar(Long id, CarroRequest req) {
        Carro carro = obter(id);
        String placa = normalizarPlaca(req.placa());
        if (carroRepository.existsByPlacaAndIdNot(placa, id)) {
            throw new ConflitoException("Já existe outro carro cadastrado com a placa " + placa);
        }
        preencher(carro, req, placa);
        return CarroResponse.from(carro);
    }

    @Transactional
    public CarroResponse alterarStatus(Long id, CarroStatus novoStatus) {
        Carro carro = obter(id);
        if (novoStatus == CarroStatus.ALUGADO) {
            throw new RegraNegocioException(
                    "O status ALUGADO é definido automaticamente ao registrar a retirada de um aluguel");
        }
        if (carro.getStatus() == CarroStatus.ALUGADO) {
            throw new ConflitoException(
                    "O carro está alugado; registre a devolução do aluguel antes de alterar o status");
        }
        carro.setStatus(novoStatus);
        return CarroResponse.from(carro);
    }

    @Transactional
    public void remover(Long id) {
        Carro carro = obter(id);
        if (aluguelRepository.existsByCarroId(id)) {
            throw new ConflitoException(
                    "O carro possui histórico de aluguéis e não pode ser removido; altere o status para MANUTENCAO");
        }
        carroRepository.delete(carro);
    }

    private Carro obter(Long id) {
        return carroRepository.findById(id).orElseThrow(() -> naoEncontrado(id));
    }

    private RecursoNaoEncontradoException naoEncontrado(Long id) {
        return new RecursoNaoEncontradoException("Carro " + id + " não encontrado");
    }

    private void preencher(Carro carro, CarroRequest req, String placa) {
        carro.setPlaca(placa);
        carro.setMarca(req.marca().strip());
        carro.setModelo(req.modelo().strip());
        carro.setAno(req.ano());
        carro.setCategoria(req.categoria());
        carro.setValorDiaria(req.valorDiaria());
        carro.setQuilometragem(req.quilometragem());
    }

    private String normalizarPlaca(String placa) {
        return placa.strip().toUpperCase(Locale.ROOT);
    }
}
