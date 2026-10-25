package br.unifor.locadora.config;

import br.unifor.locadora.domain.*;
import br.unifor.locadora.repository.AluguelRepository;
import br.unifor.locadora.repository.CarroRepository;
import br.unifor.locadora.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Dados de demonstração (apenas fora do perfil "prod"): 4 usuários, 4 carros e 3 aluguéis.
 * Os externalId abaixo são os valores que o gateway deve enviar no header X-User-Id.
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final CarroRepository carroRepository;
    private final AluguelRepository aluguelRepository;
    private final Clock clock;

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }
        LocalDate hoje = LocalDate.now(clock);

        Usuario maria = usuario("b2c-cliente-001", "Maria Souza", "maria@email.com",
                "52998224725", "85999990001", "12345678900", Role.CLIENTE);
        Usuario joao = usuario("b2c-cliente-002", "João Lima", "joao@email.com",
                "11144477735", "85999990002", "98765432100", Role.CLIENTE);
        usuario("b2c-staff-001", "Ana Atendente", "ana@locadora.com", null, null, null, Role.STAFF);
        usuario("b2c-admin-001", "Carlos Gestor", "carlos@locadora.com", null, null, null, Role.ADMIN);

        Carro onix = carro("ABC1D23", "Chevrolet", "Onix", 2023, Categoria.ECONOMICO, "120.00",
                CarroStatus.DISPONIVEL, 15000);
        Carro compass = carro("DEF2G34", "Jeep", "Compass", 2022, Categoria.SUV, "300.00",
                CarroStatus.DISPONIVEL, 22000);
        carro("GHI3J45", "Toyota", "Corolla", 2021, Categoria.SEDAN, "180.00",
                CarroStatus.MANUTENCAO, 41000);
        Carro hilux = carro("JKL4M56", "Toyota", "Hilux", 2022, Categoria.SUV, "250.00",
                CarroStatus.ALUGADO, 30000);

        // Em andamento: retirado há 2 dias, devolução prevista em 3 dias (5 diárias)
        aluguel(maria, hilux, hoje.minusDays(2), hoje.plusDays(3), null, "1250.00", AluguelStatus.EM_ANDAMENTO);
        // Concluído no passado
        aluguel(joao, onix, hoje.minusDays(20), hoje.minusDays(15), hoje.minusDays(15), "600.00",
                AluguelStatus.CONCLUIDO);
        // Reserva futura
        aluguel(maria, compass, hoje.plusDays(10), hoje.plusDays(13), null, "900.00", AluguelStatus.RESERVADO);
    }

    private Usuario usuario(String externalId, String nome, String email, String cpf, String telefone,
                            String cnh, Role perfil) {
        Usuario u = new Usuario();
        u.setExternalId(externalId);
        u.setNome(nome);
        u.setEmail(email);
        u.setCpf(cpf);
        u.setTelefone(telefone);
        u.setCnh(cnh);
        u.setPerfil(perfil);
        return usuarioRepository.save(u);
    }

    private Carro carro(String placa, String marca, String modelo, int ano, Categoria categoria,
                        String diaria, CarroStatus status, int km) {
        Carro c = new Carro();
        c.setPlaca(placa);
        c.setMarca(marca);
        c.setModelo(modelo);
        c.setAno(ano);
        c.setCategoria(categoria);
        c.setValorDiaria(new BigDecimal(diaria));
        c.setStatus(status);
        c.setQuilometragem(km);
        return carroRepository.save(c);
    }

    private void aluguel(Usuario cliente, Carro carro, LocalDate retirada, LocalDate prevista,
                         LocalDate real, String valor, AluguelStatus status) {
        Aluguel a = new Aluguel();
        a.setCliente(cliente);
        a.setCarro(carro);
        a.setDataRetirada(retirada);
        a.setDataDevolucaoPrevista(prevista);
        a.setDataDevolucaoReal(real);
        a.setValorTotal(new BigDecimal(valor));
        a.setStatus(status);
        aluguelRepository.save(a);
    }
}
