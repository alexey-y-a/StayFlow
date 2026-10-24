#!/usr/bin/env bash
# Полная ручная проверка StayFlow. Запуск из корня проекта: ./scripts/manual-check.sh
set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
USER_NAME="user"
USER_PASS="User123!"
ADMIN_NAME="admin"
ADMIN_PASS="Admin123!"
START=$(python3 -c 'import datetime; print((datetime.date.today()+datetime.timedelta(days=200)).isoformat())')
END=$(python3 -c 'import datetime; print((datetime.date.today()+datetime.timedelta(days=203)).isoformat())')

PASS=0
FAIL=0
say() { printf '%s
' "$*"; }
ok() { PASS=$((PASS+1)); say "OK   $1"; }
bad() { FAIL=$((FAIL+1)); say "FAIL $1"; }
expect() { if [ "$2" = "$3" ]; then ok "$1 ($3)"; else bad "$1: ожидалось $2, получено $3"; fi; }
code() { curl -s -o /tmp/stayflow-resp.json -w '%{http_code}' "$@"; }
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }
login() { curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" | json 'd["token"]'; }

say "Ждём готовность Gateway ..."
READY=0
for _ in $(seq 1 60); do
  if curl -fsS "$BASE/actuator/health" >/dev/null 2>&1; then READY=1; break; fi
  sleep 2
done
[ "$READY" = "1" ] || { say "Gateway не поднялся"; exit 1; }

TOKEN=$(login "$USER_NAME" "$USER_PASS")
ADMIN_TOKEN=$(login "$ADMIN_NAME" "$ADMIN_PASS")
[ -n "$TOKEN" ] || { say "Не удалось получить токен user"; exit 1; }
[ -n "$ADMIN_TOKEN" ] || { say "Не удалось получить токен admin"; exit 1; }
say "Токены получены (длины: ${#TOKEN}, ${#ADMIN_TOKEN})"

STATUS=$(curl -s "$BASE/actuator/health" | json 'd["status"]')
expect "health" "UP" "$STATUS"

expect "отели без токена" "401" "$(code "$BASE/api/hotels")"
expect "отели с токеном" "200" "$(code "$BASE/api/hotels?size=3" --oauth2-bearer "$TOKEN")"

REC=$(curl -s "$BASE/api/rooms/recommend?hotelId=1&startDate=$START&endDate=$END" --oauth2-bearer "$TOKEN")
ORDER=$(printf '%s' "$REC" | json 'str([r["timesBooked"] for r in d])')
SORTED=$(printf '%s' "$REC" | python3 -c 'import sys,json;d=json.load(sys.stdin);t=[r["timesBooked"] for r in d];print("yes" if t==sorted(t) else "no")' 2>/dev/null)
if [ "$SORTED" = "yes" ]; then ok "рекомендации отсортированы: $ORDER"; else bad "рекомендации не по возрастанию: $ORDER"; fi

KEY="manual-$(date +%s)"
RESPONSE=$(curl -s -X POST "$BASE/api/bookings" --oauth2-bearer "$TOKEN" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "{\"hotelId\":1,\"startDate\":\"$START\",\"endDate\":\"$END\",\"autoSelect\":true}")
BID=$(printf '%s' "$RESPONSE" | json 'd["id"]')
BSTATUS=$(printf '%s' "$RESPONSE" | json 'd["status"]')
if [ -n "$BID" ] && [ "$BSTATUS" = "CONFIRMED" ]; then ok "бронь создана ($BID, CONFIRMED)"; else bad "бронь не создана: $RESPONSE"; fi

REPEAT_ID=$(curl -s -X POST "$BASE/api/bookings" --oauth2-bearer "$TOKEN" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "{\"hotelId\":1,\"startDate\":\"$START\",\"endDate\":\"$END\",\"autoSelect\":true}" | json 'd["id"]')
expect "повтор с тем же ключом" "$BID" "$REPEAT_ID"

RID=$(printf '%s' "$RESPONSE" | json 'd["roomId"]')
OTHER=1
[ "$RID" = "1" ] && OTHER=2
CONFLICT=$(code -X POST "$BASE/api/bookings" --oauth2-bearer "$TOKEN" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "{\"hotelId\":1,\"startDate\":\"$START\",\"endDate\":\"$END\",\"autoSelect\":false,\"roomId\":$OTHER}")
CONFLICT_CODE=$(json 'd.get("code")' < /tmp/stayflow-resp.json)
expect "другое тело с тем же ключом" "409" "$CONFLICT"
if [ "$CONFLICT_CODE" = "IDEMPOTENCY_CONFLICT" ]; then ok "код IDEMPOTENCY_CONFLICT"; else bad "код конфликта: $CONFLICT_CODE"; fi

expect "история" "200" "$(code "$BASE/api/bookings" --oauth2-bearer "$TOKEN")"
expect "бронь по id" "200" "$(code "$BASE/api/bookings/$BID" --oauth2-bearer "$TOKEN")"
expect "отмена" "204" "$(code -X DELETE "$BASE/api/bookings/$BID" --oauth2-bearer "$TOKEN")"
expect "повторная отмена" "204" "$(code -X DELETE "$BASE/api/bookings/$BID" --oauth2-bearer "$TOKEN")"
FINAL_STATUS=$(curl -s "$BASE/api/bookings/$BID" --oauth2-bearer "$TOKEN" | json 'd["status"]')
expect "статус после отмены" "CANCELLED" "$FINAL_STATUS"

expect "пользователи: user" "403" "$(code "$BASE/api/users" --oauth2-bearer "$TOKEN")"
expect "пользователи: admin" "200" "$(code "$BASE/api/users" --oauth2-bearer "$ADMIN_TOKEN")"
expect "статистика: user" "403" "$(code "$BASE/api/hotels/1/statistics" --oauth2-bearer "$TOKEN")"
expect "статистика: admin" "200" "$(code "$BASE/api/hotels/1/statistics" --oauth2-bearer "$ADMIN_TOKEN")"
expect "internal через Gateway" "404" "$(code "$BASE/api/rooms/internal/recommend?hotelId=1" --oauth2-bearer "$TOKEN")"

IK=$(grep '^INTERNAL_API_KEY=' "$ROOT/.env" | cut -d= -f2-)
expect "internal напрямую без ключа" "401" "$(code "http://localhost:8081/api/rooms/internal/recommend?hotelId=1")"
expect "internal напрямую с ключом" "200" "$(code "http://localhost:8081/api/rooms/internal/recommend?hotelId=1" -H "X-Internal-Api-Key: $IK")"

APPS=$(curl -s http://localhost:8761/eureka/apps | grep -c '<application>')
expect "сервисов в Eureka" "3" "$APPS"

UI=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/swagger-ui.html)
DOCS=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/v3/api-docs)
if [ "$UI" = "302" ] || [ "$UI" = "200" ]; then ok "Swagger UI booking ($UI)"; else bad "Swagger UI booking: $UI"; fi
expect "OpenAPI booking" "200" "$DOCS"

printf '%s' "$TOKEN" > /tmp/stayflow-user-token.txt
printf '%s' "$ADMIN_TOKEN" > /tmp/stayflow-admin-token.txt
say ""
say "Токен для Swagger сохранён в /tmp/stayflow-user-token.txt"
say "Проверок успешно: $PASS, провалено: $FAIL"
[ "$FAIL" -eq 0 ] || exit 1
say "MANUAL CHECK PASSED"
