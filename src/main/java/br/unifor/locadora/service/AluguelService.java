package br.unifor.locadora.service;

import br.unifor.locadora.domain.*;
import br.unifor.locadora.dto.AluguelCreateRequest;
import br.unifor.locadora.dto.AluguelResponse;
import br.unifor.locadora.dto.DevolucaoRequest;
import br.unifor.locadora.exception.ConflitoException;
import br.unifor.locadora.exception.RecursoNaoEncontradoException;
import br.unifor.locadora.exception.RegraNegocioException;
import br.unifor.locadora.repository.AluguelRepository;
import br.unifor.locadora.repository.CarroRepository;
import br.unifor.locadora.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AluguelService {

    private static final List<AluguelStatus> ATIVOS =
            List.of(AluguelStatus.RESERVADO, AluguelStatus.EM_ANDAMENTO);

    private final AluguelRepository aluguelRepository;
    private final CarroRepository carroRepository;
    private final UsuarioService usuarioService;
    private final Clock clock;

    @Transactional
    public AluguelResponse criar(AuthenticatedUser usuario, AluguelCreateRequest req) {
        Usuario cliente = usuarioService.obterOuProvisionar(usuario);
        if (vazio(cliente.getCpf()) || vazio(cliente.getCnh())) {
            throw new RegraNegocioException(
                    "Complete seu perfil (CPF e CNH) em PUT /usuarios/me antes de alugar um carro");
        }

        LocalDate hoje = LocalDate.now(clock);
        if (req.dataRetirada().isBefore(hoje)) {
            throw new RegraNegocioException("A data de retirada não pode estar no passado");
        }
        if (!req.dataDevolucaoPrevista().isAfter(req.dataRetirada())) {
            throw new RegraNegocioException("A devolução prevista deve ser posterior à data de retirada");
        }

        Carro carro = carroRepository.findById(req.carroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Carro " + req.carroId() + " não encontrado"));
        if (carro.getStatus() != CarroStatus.DISPONIVEL) {
            throw new ConflitoException("O carro não está disponível para locação");
        }
        if (aluguelRepository.existeConflito(carro.getId(), req.dataRetirada(),
                req.dataDevolucaoPrevista(), ATIVOS)) {
            throw new ConflitoException("O carro já está reservado para o período informado");
        }

        Aluguel aluguel = new Aluguel();
        aluguel.setCliente(cliente);
        aluguel.setCarro(carro);
        aluguel.setDataRetirada(req.dataRetirada());
        aluguel.setDataDevolucaoPrevista(req.dataDevolucaoPrevista());
        aluguel.setValorTotal(calcularValor(carro.getValorDiaria(), dias(req.dataRetirada(), req.dataDevolucaoPrevista())));
        aluguel.setStatus(AluguelStatus.RESERVADO);
        aluguel.setObservacoes(req.observacoes());
        return AluguelResponse.from(aluguelRepository.save(aluguel));
    }

    /** Aluguéis do próprio usuário logado. */
    @Transactional
    public List<AluguelResponse> listarMeus(AuthenticatedUser usuario) {
        Usuario cliente = usuarioService.obterOuProvisionar(usuario);
        return aluguelRepository.findByClienteIdOrderByDataRetiradaDesc(cliente.getId()).stream()
                .map(AluguelResponse::from).toList();
    }

    /** Todos os aluguéis (STAFF/ADMIN), com filtro opcional por status. */
    @Transactional(readOnly = true)
    public List<AluguelResponse> listarTodos(AluguelStatus status) {
        Sort ordem = Sort.by(Sort.Direction.DESC, "dataRetirada");
        List<Aluguel> alugueis = (status == null)
                ? aluguelRepository.findAll(ordem)
                : aluguelRepository.findByStatus(status, ordem);
        return alugueis.stream().map(AluguelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AluguelResponse buscar(Long id, AuthenticatedUser usuario) {
        Aluguel aluguel = obter(id);
        garantirAcesso(aluguel, usuario);
        return AluguelResponse.from(aluguel);
    }

    @Transactional
    public AluguelResponse cancelar(Long id, AuthenticatedUser usuario) {
        Aluguel aluguel = obter(id);
        garantirAcesso(aluguel, usuario);
        if (aluguel.getStatus() != AluguelStatus.RESERVADO) {
            throw new ConflitoException("Só é possível cancelar aluguéis com status RESERVADO");
        }
        aluguel.setStatus(AluguelStatus.CANCELADO);
        return AluguelResponse.from(aluguel);
    }

    @Transactional
    public AluguelResponse registrarRetirada(Long id) {
        Aluguel aluguel = obter(id);
        if (aluguel.getStatus() != AluguelStatus.RESERVADO) {
            throw new ConflitoException("A retirada só pode ser registrada para aluguéis RESERVADOS");
        }
        if (LocalDate.now(clock).isBefore(aluguel.getDataRetirada())) {
            throw new RegraNegocioException("A retirada não pode ser registrada antes de "
                    + aluguel.getDataRetirada());
        }
        Carro carro = aluguel.getCarro();
        if (carro.getStatus() != CarroStatus.DISPONIVEL) {
            throw new ConflitoException("O carro não está disponível para retirada (status " + carro.getStatus() + ")");
        }
        carro.setStatus(CarroStatus.ALUGADO);
        aluguel.setStatus(AluguelStatus.EM_ANDAMENTO);
        return AluguelResponse.from(aluguel);
    }

    @Transactional
    public AluguelResponse registrarDevolucao(Long id, DevolucaoRequest req) {
        Aluguel aluguel = obter(id);
        if (aluguel.getStatus() != AluguelStatus.EM_ANDAMENTO) {
            throw new ConflitoException("A devolução só pode ser registrada para aluguéis EM_ANDAMENTO");
        }
        LocalDate devolucao = (req.dataDevolucaoReal() != null) ? req.dataDevolucaoReal() : LocalDate.now(clock);
        if (devolucao.isBefore(aluguel.getDataRetirada())) {
            throw new RegraNegocioException("A devolução não pode ser anterior à retirada");
        }
        Carro carro = aluguel.getCarro();
        if (req.quilometragemFinal() < carro.getQuilometragem()) {
            throw new RegraNegocioException("A quilometragem final não pode ser menor que a atual ("
                    + carro.getQuilometragem() + " km)");
        }

        // Cobra o maior entre os dias previstos e os dias reais (atraso gera cobrança adicional).
        long diasCobrados = Math.max(
                dias(aluguel.getDataRetirada(), aluguel.getDataDevolucaoPrevista()),
                dias(aluguel.getDataRetirada(), devolucao));
        aluguel.setValorTotal(calcularValor(carro.getValorDiaria(), diasCobrados));
        aluguel.setDataDevolucaoReal(devolucao);
        aluguel.setStatus(AluguelStatus.CONCLUIDO);
        if (!vazio(req.observacoes())) {
            aluguel.setObservacoes(req.observacoes());
        }

        carro.setQuilometragem(req.quilometragemFinal());
        carro.setStatus(CarroStatus.DISPONIVEL);
        return AluguelResponse.from(aluguel);
    }

    private Aluguel obter(Long id) {
        return aluguelRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluguel " + id + " não encontrado"));
    }

    /** Cliente só acessa os próprios aluguéis; STAFF e ADMIN acessam todos. */
    private void garantirAcesso(Aluguel aluguel, AuthenticatedUser usuario) {
        if (usuario.ehCliente() && !aluguel.getCliente().getExternalId().equals(usuario.externalId())) {
            throw new AccessDeniedException("Aluguel pertence a outro usuário");
        }
    }

    private static long dias(LocalDate inicio, LocalDate fim) {
        return Math.max(1, ChronoUnit.DAYS.between(inicio, fim));
    }

    private static BigDecimal calcularValor(BigDecimal diaria, long dias) {
        return diaria.multiply(BigDecimal.valueOf(dias));
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
