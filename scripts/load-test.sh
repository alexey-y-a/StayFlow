#!/usr/bin/env bash
# Нагрузочная проверка конкурентных бронирований.
# Запуск: ./scripts/load-test.sh [количество параллельных запросов]
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
N="${1:-50}"
PASSWORD="Load123!"
USER="load_$(date +%s)"
RUN="run-$(date +%s)"

OFFSET=$(( ( $(date +%s) % 200 ) + 80 ))
START=$(python3 -c "import datetime; print((datetime.date.today()+datetime.timedelta(days=$OFFSET)).isoformat())")
END=$(python3 -c "import datetime; print((datetime.date.today()+datetime.timedelta(days=$OFFSET+3)).isoformat())")
START2=$(python3 -c "import datetime; print((datetime.date.today()+datetime.timedelta(days=$OFFSET+10)).isoformat())")
END2=$(python3 -c "import datetime; print((datetime.date.today()+datetime.timedelta(days=$OFFSET+13)).isoformat())")

json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }

echo "Load test: $N parallel requests, hotel dates $START .. $END"

REGISTER=$(curl -fsS -X POST "$BASE_URL/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASSWORD\"}")
TOKEN=$(printf '%s' "$REGISTER" | json 'd["token"]')

HOTELS=$(curl -fsS "$BASE_URL/api/hotels?size=1" -H "Authorization: Bearer $TOKEN")
HOTEL_ID=$(printf '%s' "$HOTELS" | json 'd["content"][0]["id"]')
echo "User: $USER, hotel: $HOTEL_ID"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
export BASE_URL TOKEN HOTEL_ID START END START2 END2 RUN TMP

echo "Phase 1: $N parallel bookings with unique Idempotency-Key ..."
BEGIN=$(date +%s)
for i in $(seq 1 "$N"); do
  (
    code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/bookings" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: $RUN-$i" \
      -d "{\"hotelId\":$HOTEL_ID,\"startDate\":\"$START\",\"endDate\":\"$END\",\"autoSelect\":true}")
    printf "%s\n" "$code" >> "$TMP/codes"
  ) &
done
wait
ELAPSED=$(( $(date +%s) - BEGIN ))

CONFIRMED=$(grep -c '^201$' "$TMP/codes" || true)
CONFLICTS=$(grep -c '^409$' "$TMP/codes" || true)
ERRORS=$(grep -vc -E '^(201|409)$' "$TMP/codes" || true)
echo "Phase 1 done in ${ELAPSED}s: 201=$CONFIRMED, 409=$CONFLICTS, other=$ERRORS"

echo "Phase 2: $N parallel requests with one shared Idempotency-Key ..."
BEFORE=$(curl -fsS "$BASE_URL/api/bookings?size=1" -H "Authorization: Bearer $TOKEN" | json 'd["totalElements"]')
SEQ_RUN="same-$RUN"
export SEQ_RUN
for i in $(seq 1 "$N"); do
  (
    code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/bookings" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: $SEQ_RUN" \
      -d "{\"hotelId\":$HOTEL_ID,\"startDate\":\"$START2\",\"endDate\":\"$END2\",\"autoSelect\":true}")
    printf "%s\n" "$code" >> "$TMP/same"
  ) &
done
wait
AFTER=$(curl -fsS "$BASE_URL/api/bookings?size=1" -H "Authorization: Bearer $TOKEN" | json 'd["totalElements"]')
SAME_201=$(grep -c '^201$' "$TMP/same" || true)
SAME_OTHER=$(grep -vc '^201$' "$TMP/same" || true)
echo "Phase 2: 201=$SAME_201, other=$SAME_OTHER, bookings added=$((AFTER-BEFORE))"

echo "Statistics:"
ADMIN_TOKEN=$(curl -fsS -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin123!"}' | json 'd["token"]')
curl -fsS "$BASE_URL/api/hotels/$HOTEL_ID/statistics" -H "Authorization: Bearer $ADMIN_TOKEN" \
  | json '"activeReservations="+str(d["activeReservations"])+" totalTimesBooked="+str(d["totalTimesBooked"])+" top="+str([(x["number"],x["timesBooked"]) for x in d["topRooms"]])'

FAILED=0
if [ "$ERRORS" -ne 0 ]; then echo "FAIL: phase 1 has unexpected statuses"; FAILED=1; fi
if [ "$SAME_OTHER" -ne 0 ]; then echo "FAIL: phase 2 has unexpected statuses"; FAILED=1; fi
if [ "$((AFTER-BEFORE))" -ne 1 ]; then echo "FAIL: shared key created more than one booking"; FAILED=1; fi
if [ "$CONFIRMED" -lt 1 ]; then echo "FAIL: no booking confirmed in phase 1"; FAILED=1; fi

if [ "$FAILED" -ne 0 ]; then
  echo "LOAD TEST FAILED"
  exit 1
fi
echo "LOAD TEST PASSED"
