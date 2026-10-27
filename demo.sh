#!/usr/bin/env bash
# Roteiro de demonstração da autenticação (rode depois de "docker compose up --build").
# Atenção: o login tem limite de 5 tentativas por minuto por IP. Espere 1 minuto antes de rodar de novo.
# Uso: ./demo.sh [URL_DO_GATEWAY]   (padrão http://localhost:8000)
GW="${1:-http://localhost:8000}"

token() { # $1=email  $2=senha
  curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}
passo() { echo; echo "=== $1"; }

passo "1) Sem token -> 401"
curl -s -i "$GW/carros" | head -n 1

passo "2) Login com senha errada -> 401"
curl -s -i -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"maria@email.com","password":"errada"}' | head -n 1

CLIENTE=$(token maria@email.com 'Senha@123')
STAFF=$(token ana@locadora.com 'Senha@123')
ADMIN=$(token carlos@locadora.com 'Senha@123')

passo "3) CLIENTE lista carros -> 200"
curl -s -i "$GW/carros" -H "Authorization: Bearer $CLIENTE" | head -n 1

passo "3b) CLIENTE consulta detalhes do carro 1 (combina dados locais + FIPE) -> 200"
curl -s -i "$GW/carros/1/detalhes" -H "Authorization: Bearer $CLIENTE" | head -n 1

passo "4) CLIENTE tenta listar usuários (só ADMIN) -> 403"
curl -s -i "$GW/usuarios" -H "Authorization: Bearer $CLIENTE" | head -n 1

passo "5) STAFF lista todos os aluguéis -> 200"
curl -s -i "$GW/alugueis" -H "Authorization: Bearer $STAFF" | head -n 1

passo "6) ADMIN lista usuários (CPF/CNH mascarados) -> 200"
curl -s "$GW/usuarios" -H "Authorization: Bearer $ADMIN"; echo

passo "7) Tentativa de forjar perfil: cliente manda X-User-Role: ADMIN -> continua 403 (gateway sobrescreve)"
curl -s -i "$GW/usuarios" -H "Authorization: Bearer $CLIENTE" -H 'X-User-Role: ADMIN' | head -n 1

passo "8) Logout do CLIENTE e reuso do mesmo token -> 204 e depois 401"
curl -s -i -X POST "$GW/auth/logout" -H "Authorization: Bearer $CLIENTE" | head -n 1
curl -s -i "$GW/carros" -H "Authorization: Bearer $CLIENTE" | head -n 1

passo "9) Rate limit no login (5/min por IP; os logins acima já consumiram parte) -> 429 nas tentativas extras"
for i in 1 2 3 4 5 6 7; do
  curl -s -o /dev/null -w "tentativa $i -> %{http_code}\n" -X POST "$GW/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"x@x.com","password":"y"}'
done
