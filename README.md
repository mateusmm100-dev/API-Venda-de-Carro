# Locadora API — Backend de domínio

API REST de aluguel de carros do projeto **AV2 — Técnicas de Integração de Sistemas**.
Autenticação do projeto: **Gateway de API B2C** (o gateway autentica o cliente; este backend confia
na identidade que o gateway repassa e aplica as permissões por perfil).

- **Stack:** Java 21, Spring Boot 3.2.5, Spring Security, Spring Data JPA, Bean Validation, Lombok
- **Banco:** H2 em memória (desenvolvimento/testes) e PostgreSQL (perfil `prod`)

## Como rodar localmente

Pré-requisitos: JDK 21 e Maven 3.9+. Para executar o sistema completo com Keycloak e gateway,
é necessário Docker com Docker Compose.

```bash
mvn spring-boot:run          # backend isolado em http://localhost:8080, com H2 e dados de exemplo
mvn test                     # testes do backend, incluindo a integração FIPE simulada
docker compose up --build     # sistema completo: gateway :8000, Keycloak :8180 e backend interno
```

### Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `GATEWAY_SHARED_SECRET` | Em produção | Segredo longo e aleatório, igual no gateway e no backend. O valor padrão só serve para desenvolvimento local. |
| `PORT` | Não | Porta HTTP atribuída pela plataforma (padrão 8080 no backend e 8000 no gateway). |
| `SPRING_PROFILES_ACTIVE` | Em produção | Use `prod`; desliga o seed automático e configura o PostgreSQL. |
| `DATABASE_URL` | Em produção | URL JDBC, por exemplo `jdbc:postgresql://host:5432/locadora`. |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Em produção | Credenciais do PostgreSQL. |
| `KEYCLOAK_URL` | No gateway | URL HTTPS pública do Keycloak, sem `/realms/...`; deve corresponder ao emissor (`iss`) dos tokens. |
| `KEYCLOAK_REALM` | No gateway | Realm configurado no Keycloak (neste projeto, `locadora`). |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | No gateway | Segredo do cliente confidencial `gateway-admin`; configure fora do repositório. |
| `BACKEND_URL` | No gateway | Endereço interno do backend, acessível pelo gateway. |
| `CORS_ALLOWED_ORIGINS` | No gateway | Origens autorizadas, separadas por vírgula; em produção, informe somente as origens necessárias. |

Veja `.env.example`.

## Contrato com o gateway (importante para o Integrante 1)

O backend **não faz login**. Depois de validar o usuário no provedor B2C, o gateway deve:

1. **Remover** qualquer header `X-User-*` e `X-Gateway-Secret` que o cliente tenha enviado;
2. **Injetar** os headers abaixo com a identidade já validada:

| Header | Obrigatório | Conteúdo |
|---|---|---|
| `X-Gateway-Secret` | Sim | Mesmo valor de `GATEWAY_SHARED_SECRET` |
| `X-User-Id` | Sim | Identificador do usuário no B2C (claim `sub`) |
| `X-User-Role` | Sim | `CLIENTE`, `STAFF` ou `ADMIN` |
| `X-User-Email` | Não | E-mail do usuário |
| `X-User-Name` | Não | Nome do usuário |

Sem o segredo correto, sem `X-User-Id` ou com perfil inválido, a resposta é **401**. Isso impede que
alguém chame o backend direto e forje um perfil de ADMIN. Em produção, o ideal é que o backend
também não seja acessível publicamente (somente pelo gateway).

No primeiro acesso de um usuário, o backend cria o perfil local dele (`usuarios`) automaticamente.
Para alugar, o cliente precisa antes completar CPF e CNH em `PUT /usuarios/me`.

## Perfis e permissões

| Endpoint | CLIENTE | STAFF | ADMIN |
|---|:-:|:-:|:-:|
| `GET /carros`, `GET /carros/{id}` | só disponíveis | todos | todos |
| `GET /carros/{id}/detalhes` | só disponíveis | todos | todos |
| `POST /carros`, `PUT /carros/{id}`, `DELETE /carros/{id}` | 403 | 403 | OK |
| `PATCH /carros/{id}/status` | 403 | OK | OK |
| `POST /alugueis` | OK (para si) | 403 | 403 |
| `GET /alugueis/meus` | OK | OK | OK |
| `GET /alugueis` | 403 | OK | OK |
| `GET /alugueis/{id}` | só os próprios | OK | OK |
| `PATCH /alugueis/{id}/cancelar` | só os próprios (RESERVADO) | OK | OK |
| `PATCH /alugueis/{id}/retirada` | 403 | OK | OK |
| `PATCH /alugueis/{id}/devolucao` | 403 | OK | OK |
| `GET /usuarios` (CPF/CNH mascarados) | 403 | 403 | OK |
| `GET /usuarios/me`, `PUT /usuarios/me` | OK | OK | OK |

## Integração com a API externa (FIPE)

`GET /carros/{id}/detalhes` combina o carro armazenado localmente com a consulta pública da FIPE.
O backend procura a marca, os modelos, os anos disponíveis e, por fim, o valor de referência para
o ano correspondente. A busca ignora diferenças entre maiúsculas/minúsculas e acentos; para o
modelo, tenta correspondência exata antes de procurar uma versão compatível.

Exemplo de chamada local ao backend, simulando os headers injetados pelo gateway:

```bash
curl -s http://localhost:8080/carros/1/detalhes \
  -H "X-Gateway-Secret: dev-secret-troque-em-producao" \
  -H "X-User-Id: b2c-cliente-001" \
  -H "X-User-Role: CLIENTE"
```

O ID `1` corresponde ao Onix criado pelo seed de desenvolvimento. Com o sistema completo no
Docker Compose, faça a requisição ao gateway em `http://localhost:8000/carros/1/detalhes` usando
`Authorization: Bearer <access_token>` obtido em `POST /auth/login`.

O JSON reúne `carro` (dados locais) e `fipe` (resposta externa), com marca, modelo, ano-modelo,
valor de referência, mês de referência e código FIPE. Se a marca ou modelo não forem encontrados,
ou a FIPE estiver indisponível, a resposta continua incluindo o carro local e informa o resultado
em `fipe.disponivel` e `fipe.mensagem`; quando disponível, os campos do valor ficam preenchidos.
O valor FIPE é uma referência externa e não é somado ao valor da diária nem representa cotação de
seguro.

Exemplo de resposta quando a FIPE está disponível (o valor e o mês variam conforme a consulta):

```json
{
  "carro": {
    "id": 1,
    "placa": "ABC1D23",
    "marca": "Chevrolet",
    "modelo": "Onix",
    "ano": 2023,
    "categoria": "ECONOMICO",
    "valorDiaria": 120.00,
    "status": "DISPONIVEL",
    "quilometragem": 15000
  },
  "fipe": {
    "disponivel": true,
    "marca": "Chevrolet",
    "modelo": "Onix 1.0",
    "anoModelo": 2023,
    "valor": "R$ 75.000,00",
    "mesReferencia": "outubro de 2026",
    "codigoFipe": "004504-3"
  }
}
```

O cliente HTTP usa timeout de 3 segundos para conexão e 5 segundos para leitura. Os testes não
dependem da disponibilidade da FIPE: usam respostas HTTP simuladas para cobrir sucesso, falha da
API externa, modelo inexistente e a composição/fallback do endpoint.

## Dados de exemplo (perfis diferentes de `prod`)

| `X-User-Id` | `X-User-Role` | Quem é |
|---|---|---|
| `b2c-cliente-001` | `CLIENTE` | Maria Souza (perfil completo) |
| `b2c-cliente-002` | `CLIENTE` | João Lima (perfil completo) |
| `b2c-staff-001` | `STAFF` | Ana Atendente |
| `b2c-admin-001` | `ADMIN` | Carlos Gestor |

Há 4 carros (Onix, Compass, Corolla em manutenção, Hilux alugada) e 3 aluguéis (em andamento,
concluído e reservado).

O `DataSeeder` é executado somente fora do perfil `prod` e só popula uma base vazia. Em produção,
os usuários e dados de demonstração devem ser preparados de forma controlada; não dependa do seed
local para popular a base publicada.

## Exemplos de requisição

Simulando o gateway com `curl` (substitua o segredo se mudou):

```bash
GW="X-Gateway-Secret: dev-secret-troque-em-producao"

# Cliente lista carros disponíveis
curl -s localhost:8080/carros -H "$GW" -H "X-User-Id: b2c-cliente-001" -H "X-User-Role: CLIENTE"

# Cliente consulta os dados locais e a referência FIPE do Onix (ID 1 do seed)
curl -s localhost:8080/carros/1/detalhes -H "$GW" -H "X-User-Id: b2c-cliente-001" -H "X-User-Role: CLIENTE"

# Admin cadastra um carro
curl -s -X POST localhost:8080/carros -H "$GW" -H "X-User-Id: b2c-admin-001" -H "X-User-Role: ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"placa":"XYZ1A23","marca":"Fiat","modelo":"Argo","ano":2024,"categoria":"ECONOMICO","valorDiaria":110.00,"quilometragem":500}'

# Cliente cria um aluguel
curl -s -X POST localhost:8080/alugueis -H "$GW" -H "X-User-Id: b2c-cliente-001" -H "X-User-Role: CLIENTE" \
  -H "Content-Type: application/json" \
  -d '{"carroId":1,"dataRetirada":"2030-01-10","dataDevolucaoPrevista":"2030-01-13"}'

# Staff registra a devolução do aluguel 1
curl -s -X PATCH localhost:8080/alugueis/1/devolucao -H "$GW" -H "X-User-Id: b2c-staff-001" -H "X-User-Role: STAFF" \
  -H "Content-Type: application/json" -d '{"quilometragemFinal":30500}'

# Cliente completa o perfil (necessário antes de alugar)
curl -s -X PUT localhost:8080/usuarios/me -H "$GW" -H "X-User-Id: b2c-cliente-novo" -H "X-User-Role: CLIENTE" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Novo Cliente","cpf":"529.982.247-25","telefone":"85999998888","cnh":"12345678900"}'
```

### Exemplos de erro

**401 — sem identidade válida do gateway**

```bash
curl -i localhost:8080/carros
```
```json
{"timestamp":"2030-01-01T12:00:00Z","status":401,"error":"Unauthorized",
 "message":"Autenticação necessária: requisição sem identidade válida do gateway","path":"/carros"}
```

**403 — perfil sem permissão**

```bash
curl -i -X POST localhost:8080/carros -H "$GW" -H "X-User-Id: b2c-cliente-001" -H "X-User-Role: CLIENTE" \
  -H "Content-Type: application/json" -d '{}'
```
```json
{"timestamp":"2030-01-01T12:00:00Z","status":403,"error":"Forbidden",
 "message":"Acesso negado: seu perfil não tem permissão para este recurso","path":"/carros"}
```

**422 — dados inválidos (com detalhe por campo)**

```json
{"timestamp":"...","status":422,"error":"Unprocessable Entity","message":"Dados inválidos na requisição",
 "path":"/carros","campos":[{"campo":"placa","mensagem":"Placa inválida (use o formato ABC1234 ou ABC1D23)"}]}
```

Outros códigos usados: **400** (JSON malformado / parâmetro inválido), **404** (recurso inexistente),
**405** (método não suportado), **409** (conflito: placa duplicada, carro já reservado no período,
transição de estado inválida) e **500** (erro inesperado, sem expor detalhes internos).

## Regras de negócio implementadas

- Cliente só vê carros `DISPONIVEL`; um carro indisponível responde 404 para ele.
- Aluguel: retirada não pode estar no passado; devolução prevista deve ser posterior à retirada;
  o carro precisa estar disponível e sem reserva ativa sobrepondo o período.
- Valor = diárias × dias (mínimo 1 diária). Na devolução cobra-se o **maior** entre dias previstos e reais.
- Ciclo do aluguel: `RESERVADO → EM_ANDAMENTO → CONCLUIDO` (ou `RESERVADO → CANCELADO`).
  A retirada marca o carro como `ALUGADO`; a devolução libera o carro e atualiza a quilometragem.
- Carro com histórico de aluguéis não pode ser removido (use `MANUTENCAO`).
- CPF validado pelos dígitos verificadores; CPF e CNH nunca saem completos nas listagens administrativas.

## Estrutura

```
src/main/java/br/unifor/locadora
├── config/       SecurityConfig, AppConfig (Clock), DataSeeder
├── security/     GatewayAuthFilter, AuthenticatedUser, JsonSecurityHandlers (401/403 em JSON)
├── domain/       Usuario, Carro, Aluguel e enums
├── repository/   Spring Data JPA
├── dto/          Requests/Responses com Bean Validation
├── service/      Regras de negócio e verificação de dono do recurso
├── controller/   /carros, /alugueis, /usuarios (@PreAuthorize por perfil)
├── exception/    Exceções de negócio, ApiError e GlobalExceptionHandler
└── util/         CpfValidator, Mascara
```

## Limitações conhecidas

- Os headers do gateway são confiáveis apenas se o segredo for mantido em sigilo e o backend aceitar
  requisições somente do gateway. Se a plataforma não oferecer rede privada, mantenha o segredo fora
  do repositório e rotacione-o imediatamente se houver suspeita de vazamento.
- Rate limiting e expiração de sessão são responsabilidade do gateway (Integrante 1).
- Schema gerado por `ddl-auto: update`; antes de uso real em produção, migre para Flyway/Liquibase.
- Dois primeiros acessos simultâneos do mesmo usuário podem gerar um 409 transitório (tente novamente).
- A FIPE é uma dependência externa: timeout, mudanças no serviço ou indisponibilidade podem deixar
  os dados `fipe` indisponíveis, sem impedir o retorno dos dados locais do carro.

## Como fazer deploy no Render

O repositório contém Dockerfiles para o backend e o gateway, mas **não contém um deploy já publicado
nem uma URL de produção**. O `docker-compose.yml` é para desenvolvimento local; o Render não o sobe
como um único serviço. Crie os serviços abaixo no Render e use os endereços/segredos fornecidos nos
respectivos painéis.

### 1. Banco PostgreSQL

Crie um PostgreSQL no Render e guarde o host, nome do banco, usuário e senha. No backend, configure
`DATABASE_URL` no formato JDBC esperado pela aplicação
(`jdbc:postgresql://<host-interno>:5432/<nome-do-banco>`), além de `DATABASE_USER` e
`DATABASE_PASSWORD`. A URL de conexão copiada do Render pode usar outro formato; não a cole sem
adaptá-la ao formato JDBC acima. Use o endereço interno quando backend e banco estiverem na mesma
região/rede.

### 2. Backend

Crie um serviço **Web Service** com runtime **Docker**, apontando para a raiz do repositório e para
o `Dockerfile` da raiz. Configure:

| Variável | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | URL JDBC do PostgreSQL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Credenciais do PostgreSQL |
| `GATEWAY_SHARED_SECRET` | Segredo aleatório forte, compartilhado somente com o gateway |

O Render fornece `PORT` automaticamente. Depois da publicação, copie o endereço interno do backend
para `BACKEND_URL` no gateway. Se o plano/rede permitir, torne o backend privado para que somente o
gateway consiga acessá-lo; caso contrário, ele continuará sendo um serviço público, embora as rotas
da API ainda exijam os headers e o segredo do gateway.

### 3. Keycloak

O gateway depende de um Keycloak acessível por HTTPS e persistente. Configure o realm `locadora`,
os papéis `CLIENTE`, `STAFF` e `ADMIN`, e os clientes `locadora-api` e `gateway-admin` conforme a
configuração do projeto. Use uma instalação/serviço de Keycloak com banco persistente; não use
`start-dev`, armazenamento efêmero ou as senhas/segredos de demonstração do arquivo
`keycloak/realm-locadora.json` em produção. Configure o emissor público HTTPS do Keycloak antes de
criar usuários e credenciais de produção.

### 4. Gateway

Crie outro **Web Service** Docker com diretório raiz `gateway/` e use o Dockerfile existente em
`gateway/Dockerfile`.
Configure:

| Variável | Valor |
|---|---|
| `KEYCLOAK_URL` | URL HTTPS pública do Keycloak, sem `/realms/locadora` |
| `KEYCLOAK_REALM` | `locadora` |
| `KEYCLOAK_CLIENT_ID` | `locadora-api` |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `gateway-admin` |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | Segredo configurado no Keycloak para esse cliente |
| `BACKEND_URL` | URL interna do serviço backend, incluindo `http://` e a porta |
| `GATEWAY_SHARED_SECRET` | Exatamente o mesmo valor configurado no backend |
| `CORS_ALLOWED_ORIGINS` | Origens explícitas do cliente; não use `*` em produção sem necessidade |
| `RATE_LIMIT_AUTH_PER_MINUTE` | Limite de autenticação por minuto (padrão: `5`) |
| `RATE_LIMIT_API_PER_MINUTE` | Limite de API por minuto (padrão: `60`) |

O Render define `PORT`; não fixe a porta pública no painel. Após publicar, use a URL HTTPS do
gateway como endereço público da API. Verifique `GET /actuator/health` e depois autenticação e
`GET /carros/{id}/detalhes` com uma conta de teste. Cadastre manualmente os dados necessários no
PostgreSQL de produção, pois o seed é desativado pelo perfil `prod`.

O Keycloak deve emitir tokens cujo `iss` seja exatamente a URL pública configurada em
`KEYCLOAK_URL` mais `/realms/locadora`. Se usar `RATE_LIMIT_TRUST_XFF=true`, faça isso somente após
confirmar que os headers de encaminhamento são definidos pelo proxy confiável da plataforma.

Mantenha `GATEWAY_SHARED_SECRET`, credenciais do PostgreSQL e segredos do Keycloak somente nas
variáveis secretas dos serviços. Não os inclua no Git, README, imagens Docker ou logs. Antes da
apresentação, registre a URL pública efetivamente gerada pelo Render nesta seção e prepare um plano
de backup (por exemplo, uma gravação da demonstração); até isso ser feito, não há deploy online
confirmado.
