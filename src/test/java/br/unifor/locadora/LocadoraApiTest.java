package br.unifor.locadora;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa autenticação via gateway, permissões por perfil e regras de negócio.
 * Usa os dados do DataSeeder (usuários b2c-cliente-001/002, b2c-staff-001, b2c-admin-001).
 */
@SpringBootTest(properties = "gateway.shared-secret=" + LocadoraApiTest.SECRET)
@AutoConfigureMockMvc
class LocadoraApiTest {

    static final String SECRET = "segredo-de-teste";

    @Autowired
    MockMvc mvc;

    /** Simula o gateway: injeta o segredo compartilhado e a identidade já validada. */
    private static RequestPostProcessor como(String externalId, String role) {
        return request -> {
            request.addHeader("X-Gateway-Secret", SECRET);
            request.addHeader("X-User-Id", externalId);
            request.addHeader("X-User-Email", externalId + "@teste.com");
            request.addHeader("X-User-Role", role);
            return request;
        };
    }

    private static final String CLIENTE = "b2c-cliente-001";
    private static final String OUTRO_CLIENTE = "b2c-cliente-002";
    private static final String STAFF = "b2c-staff-001";
    private static final String ADMIN = "b2c-admin-001";

    // ---------- 401: autenticação ----------

    @Test
    void semIdentidadeRetorna401() throws Exception {
        mvc.perform(get("/carros"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void segredoDoGatewayErradoRetorna401() throws Exception {
        mvc.perform(get("/carros")
                        .header("X-Gateway-Secret", "segredo-forjado")
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilInvalidoRetorna401() throws Exception {
        mvc.perform(get("/carros").with(como(CLIENTE, "SUPERUSER")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 403: permissões ----------

    @Test
    void clienteNaoPodeCriarCarro() throws Exception {
        mvc.perform(post("/carros").with(como(CLIENTE, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carroJson("CLI1A23")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void staffNaoPodeRemoverCarro() throws Exception {
        mvc.perform(delete("/carros/1").with(como(STAFF, "STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNaoListaTodosOsAlugueisNemUsuarios() throws Exception {
        mvc.perform(get("/alugueis").with(como(CLIENTE, "CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios").with(como(CLIENTE, "CLIENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void clienteNaoVeAluguelDeOutroCliente() throws Exception {
        // aluguel 2 pertence ao b2c-cliente-002
        mvc.perform(get("/alugueis/2").with(como(CLIENTE, "CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/alugueis/2").with(como(OUTRO_CLIENTE, "CLIENTE"))).andExpect(status().isOk());
        mvc.perform(get("/alugueis/2").with(como(STAFF, "STAFF"))).andExpect(status().isOk());
    }

    // ---------- visibilidade e dados protegidos ----------

    @Test
    void clienteVeSomenteCarrosDisponiveis() throws Exception {
        mvc.perform(get("/carros").with(como(CLIENTE, "CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty())
                .andExpect(jsonPath("$[?(@.status != 'DISPONIVEL')]").isEmpty());
    }

    @Test
    void adminListaUsuariosComDadosMascarados() throws Exception {
        mvc.perform(get("/usuarios").with(como(ADMIN, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cpf == '52998224725')]").isEmpty())
                .andExpect(jsonPath("$[?(@.nome == 'Maria Souza')].cpf").value("***.982.247-**"));
    }

    // ---------- CRUD, validação e conflitos ----------

    @Test
    void adminCriaCarroEPlacaDuplicadaRetorna409() throws Exception {
        mvc.perform(post("/carros").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carroJson("tst1a23")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("TST1A23"))
                .andExpect(jsonPath("$.status").value("DISPONIVEL"));

        mvc.perform(post("/carros").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carroJson("TST1A23")))
                .andExpect(status().isConflict());
    }

    @Test
    void dadosInvalidosRetornam422ComDetalhePorCampo() throws Exception {
        String invalido = """
                {"placa":"XX","marca":"","modelo":"X","ano":1900,"categoria":"SUV",
                 "valorDiaria":-1,"quilometragem":-5}
                """;
        mvc.perform(post("/carros").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalido))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.campos").isNotEmpty());
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mvc.perform(post("/carros").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalido"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void carroInexistenteRetorna404() throws Exception {
        mvc.perform(get("/carros/9999").with(como(ADMIN, "ADMIN"))).andExpect(status().isNotFound());
    }

    // ---------- regras de aluguel ----------

    @Test
    void clienteCriaAluguelECalculaValorEDetectaConflito() throws Exception {
        String corpo = aluguelJson(1, LocalDate.now().plusDays(30), LocalDate.now().plusDays(33));

        mvc.perform(post("/alugueis").with(como(CLIENTE, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVADO"))
                .andExpect(jsonPath("$.valorTotal").value(360.0)); // 3 diárias x R$ 120

        mvc.perform(post("/alugueis").with(como(OUTRO_CLIENTE, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    void clienteSemCpfECnhNaoPodeAlugar() throws Exception {
        String corpo = aluguelJson(2, LocalDate.now().plusDays(60), LocalDate.now().plusDays(62));
        mvc.perform(post("/alugueis").with(como("b2c-cliente-novo", "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void staffRegistraDevolucaoEValorUsaMaiorEntrePrevistoEReal() throws Exception {
        // aluguel 1: Hilux, EM_ANDAMENTO, 5 diárias de R$ 250
        mvc.perform(patch("/alugueis/1/devolucao").with(como(STAFF, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quilometragemFinal\":30500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.valorTotal").value(1250.0));
    }

    // ---------- helpers ----------

    private static String carroJson(String placa) {
        return """
                {"placa":"%s","marca":"Fiat","modelo":"Argo","ano":2024,"categoria":"ECONOMICO",
                 "valorDiaria":110.00,"quilometragem":500}
                """.formatted(placa);
    }

    private static String aluguelJson(long carroId, LocalDate retirada, LocalDate devolucao) {
        return """
                {"carroId":%d,"dataRetirada":"%s","dataDevolucaoPrevista":"%s"}
                """.formatted(carroId, retirada, devolucao);
    }
}
