package br.unifor.locadora.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FipeServiceTest {

    private static final String URL = "https://parallelum.com.br/fipe/api/v1/carros";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private FipeService fipeService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        fipeService = new FipeService(restTemplate);
    }

    @Test
    void combinaConsultasDaFipeParaEncontrarPrecoDoCarro() {
        server.expect(requestTo(URL + "/marcas"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"nome":"Chevrolet","codigo":"23"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/marcas/23/modelos"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"modelos":[{"nome":"Onix","codigo":"123"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/marcas/23/modelos/123/anos"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"nome":"2023","codigo":"2023-1"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/marcas/23/modelos/123/anos/2023-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "Marca":"Chevrolet",
                          "Modelo":"Onix 1.0",
                          "AnoModelo":2023,
                          "Valor":"R$ 75.000,00",
                          "MesReferencia":"outubro de 2026",
                          "CodigoFipe":"004504-3"
                        }
                        """, MediaType.APPLICATION_JSON));

        FipeResponse resposta = fipeService.consultar("chevrolet", "onix", 2023);

        assertThat(resposta.disponivel()).isTrue();
        assertThat(resposta.marca()).isEqualTo("Chevrolet");
        assertThat(resposta.modelo()).isEqualTo("Onix 1.0");
        assertThat(resposta.anoModelo()).isEqualTo(2023);
        assertThat(resposta.valor()).isEqualTo("R$ 75.000,00");
        assertThat(resposta.mesReferencia()).isEqualTo("outubro de 2026");
        assertThat(resposta.codigoFipe()).isEqualTo("004504-3");
        assertThat(resposta.mensagem()).isNull();
        server.verify();
    }

    @Test
    void retornaAvisoQuandoApiExternaFalha() {
        server.expect(requestTo(URL + "/marcas"))
                .andRespond(withServerError());

        FipeResponse resposta = fipeService.consultar("Chevrolet", "Onix", 2023);

        assertThat(resposta.disponivel()).isFalse();
        assertThat(resposta.marca()).isNull();
        assertThat(resposta.valor()).isNull();
        assertThat(resposta.mensagem())
                .isEqualTo("Não foi possível consultar a FIPE no momento.");
        server.verify();
    }

    @Test
    void retornaAvisoQuandoModeloNaoExisteNaFipe() {
        server.expect(requestTo(URL + "/marcas"))
                .andRespond(withSuccess("""
                        [{"nome":"Chevrolet","codigo":"23"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/marcas/23/modelos"))
                .andRespond(withSuccess("""
                        {"modelos":[{"nome":"Prisma","codigo":"456"}]}
                        """, MediaType.APPLICATION_JSON));

        FipeResponse resposta = fipeService.consultar("Chevrolet", "Onix", 2023);

        assertThat(resposta.disponivel()).isFalse();
        assertThat(resposta.mensagem()).isEqualTo("Modelo não encontrado na FIPE.");
        server.verify();
    }
}
