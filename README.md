# Locadora API — Backend de domínio (Integrante 2)

API REST de aluguel de carros do projeto **AV2 — Técnicas de Integração de Sistemas**.
Autenticação do projeto: **Gateway de API B2C** (o gateway autentica o cliente; este backend confia
na identidade que o gateway repassa e aplica as permissões por perfil).

Esta pasta contém a parte do **Integrante 2**: recursos (carros, aluguéis, usuários), endpoints,
validação de permissões por perfil, tratamento de erros e validação de entrada.

- **Stack:** Java 17, Spring Boot 3.2.5, Spring Security, Spring Data JPA, Bean Validation, Lombok
- **Banco:** H2 em memória (desenvolvimento/testes) e PostgreSQL (perfil `prod`)

## Como rodar localmente

Pré-requisitos: JDK 17 e Maven 3.9+.

```bash
mvn spring-boot:run          # sobe em http://localhost:8080 com H2 e dados de exemplo
mvn test                     # roda os testes de autenticação, permissões e regras de negócio
```

### Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `GATEWAY_SHARED_SECRET` | Em produção | Segredo compartilhado com o gateway (header `X-Gateway-Secret`). Em dev há um valor padrão: `dev-secret-troque-em-producao` |
| `PORT` | Não | Porta HTTP (padrão 8080) |
| `SPRING_PROFILES_ACTIVE` | Em produção | Use `prod` (desliga os dados de exemplo e usa PostgreSQL) |
| `DATABASE_URL` | Em produção | Ex.: `jdbc:postgresql://host:5432/locadora` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Em produção | Credenciais do banco |

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

> `GET /carros/{id}/detalhes` (integração com a API externa) é do Integrante 3 e deve ser
> adicionado no `CarroController`, reaproveitando `CarroService.buscar`.

## Dados de exemplo (perfis diferentes de `prod`)

| `X-User-Id` | `X-User-Role` | Quem é |
|---|---|---|
| `b2c-cliente-001` | `CLIENTE` | Maria Souza (perfil completo) |
| `b2c-cliente-002` | `CLIENTE` | João Lima (perfil completo) |
| `b2c-staff-001` | `STAFF` | Ana Atendente |
| `b2c-admin-001` | `ADMIN` | Carlos Gestor |

Há 4 carros (Onix, Compass, Corolla em manutenção, Hilux alugada) e 3 aluguéis (em andamento,
concluído e reservado).

## Exemplos de requisição

Simulando o gateway com `curl` (substitua o segredo se mudou):

```bash
GW="X-Gateway-Secret: dev-secret-troque-em-producao"

# Cliente lista carros disponíveis
curl -s localhost:8080/carros -H "$GW" -H "X-User-Id: b2c-cliente-001" -H "X-User-Role: CLIENTE"

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

- Os headers do gateway são confiáveis apenas se o backend não for acessível publicamente e o segredo
  for mantido em sigilo (se vazar, qualquer um forja perfis). Rotacione o segredo se houver suspeita.
- Rate limiting e expiração de sessão são responsabilidade do gateway (Integrante 1).
- Schema gerado por `ddl-auto: update`; para produção considere Flyway/Liquibase.
- Dois primeiros acessos simultâneos do mesmo usuário podem gerar um 409 transitório (tente novamente).
