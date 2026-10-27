
package br.unifor.locadora.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class FipeService {

    private static final String URL =
            "https://parallelum.com.br/fipe/api/v1/carros";

    private final RestTemplate restTemplate;

    public FipeService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public FipeResponse consultar(String marca, String modelo, Integer ano) {
        try {
            // 1. Busca a marca na FIPE.
            JsonNode marcas = restTemplate.getForObject(
                    URL + "/marcas", JsonNode.class);

            JsonNode marcaEncontrada = encontrar(
                    marcas, "nome", marca);

            if (marcaEncontrada == null) {
                return indisponivel("Marca não encontrada na FIPE.");
            }

            String codigoMarca = marcaEncontrada.path("codigo").asText();

            // 2. Busca os modelos dessa marca.
            JsonNode respostaModelos = restTemplate.getForObject(
                    URL + "/marcas/" + codigoMarca + "/modelos",
                    JsonNode.class);

            if (respostaModelos == null) {
                return indisponivel("Resposta inválida da FIPE para modelos.");
            }

            JsonNode modelos = respostaModelos.path("modelos");
            String modeloProcurado = normalizar(modelo);

            // 3. Procura primeiro uma versão com o nome exato.
            // Se não existir, guarda a primeira versão compatível.
            JsonNode modeloExato = null;
            JsonNode primeiroModeloCompatível = null;

            for (JsonNode item : modelos) {
                String nomeModelo = normalizar(
                        item.path("nome").asText());

                if (nomeModelo.equals(modeloProcurado)) {
                    modeloExato = item;
                    break;
                }

                if (primeiroModeloCompatível == null
                        && nomeModelo.contains(modeloProcurado)) {
                    primeiroModeloCompatível = item;
                }
            }

            JsonNode modeloBase = modeloExato != null
                    ? modeloExato : primeiroModeloCompatível;

            if (modeloBase == null) {
                return indisponivel("Modelo não encontrado na FIPE.");
            }

            // 4. Coloca o modelo exato ou as versões compatíveis
            // em uma lista para testar os anos disponíveis.
            for (JsonNode item : modelos) {
                String nomeModelo = normalizar(
                        item.path("nome").asText());

                boolean corresponde =
                        nomeModelo.equals(modeloProcurado)
                        || nomeModelo.contains(modeloProcurado);

                if (modeloExato != null) {
                    corresponde = nomeModelo.equals(modeloProcurado);
                }

                if (item != modeloBase && !corresponde) {
                    continue;
                }

                String codigoModelo = item.path("codigo").asText();

                JsonNode anos = restTemplate.getForObject(
                        URL + "/marcas/" + codigoMarca
                                + "/modelos/" + codigoModelo + "/anos",
                        JsonNode.class);

                if (anos == null || !anos.isArray()) {
                    continue;
                }

                // 5. Procura o ano informado.
                for (JsonNode itemAno : anos) {
                    String nomeAno = itemAno.path("nome").asText().strip();
                    String anoProcurado = String.valueOf(ano);

                    if (!nomeAno.equals(anoProcurado)
                            && !nomeAno.startsWith(anoProcurado + " ")) {
                        continue;
                    }

                    String codigoAno = itemAno.path("codigo").asText();

                    // 6. Consulta o preço da primeira versão com esse ano.
                    JsonNode preco = restTemplate.getForObject(
                            URL + "/marcas/" + codigoMarca
                                    + "/modelos/" + codigoModelo
                                    + "/anos/" + codigoAno,
                            JsonNode.class);

                    if (preco != null) {
                        return new FipeResponse(
                                true,
                                preco.path("Marca").asText(),
                                preco.path("Modelo").asText(),
                                preco.path("AnoModelo").asInt(),
                                preco.path("Valor").asText(),
                                preco.path("MesReferencia").asText(),
                                preco.path("CodigoFipe").asText(),
                                null
                        );
                    }
                }
            }

            return indisponivel(
                    "Nenhuma versão do modelo com ano "
                            + ano + " foi encontrada na FIPE.");

        } catch (RestClientException e) {
            return indisponivel(
                    "Não foi possível consultar a FIPE no momento.");
        }
    }

    private JsonNode encontrar(
            JsonNode lista, String campo, String valor) {

        if (lista == null || !lista.isArray()) {
            return null;
        }

        String procurado = normalizar(valor);
        JsonNode melhor = null;

        for (JsonNode item : lista) {
            String nome = normalizar(item.path(campo).asText());

            if (nome.equals(procurado)) {
                return item;
            }

            if (nome.contains(procurado)
                    && (melhor == null
                    || nome.length() < normalizar(
                            melhor.path(campo).asText()).length())) {
                melhor = item;
            }
        }

        return melhor;
    }

    private String normalizar(String texto) {
        String semAcentos = Normalizer.normalize(
                texto == null ? "" : texto,
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}", "");

        return semAcentos.strip().toLowerCase(Locale.ROOT);
    }

    private FipeResponse indisponivel(String mensagem) {
        return new FipeResponse(
                false, null, null, null, null, null, null, mensagem);
    }
}
