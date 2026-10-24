#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERNAME="smoke_$(date +%s)"
PASSWORD="Smoke123!"
START=$(python3 -c 'import datetime; print((datetime.date.today()+datetime.timedelta(days=10)).isoformat())')
END=$(python3 -c 'import datetime; print((datetime.date.today()+datetime.timedelta(days=14)).isoformat())')

echo "Waiting for Gateway at $BASE_URL ..."
for _ in $(seq 1 90); do
  if curl -fsS "$BASE_URL/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -fsS "$BASE_URL/actuator/health" >/dev/null

echo "Registering $USERNAME ..."
REGISTER=""
for _ in $(seq 1 30); do
  if REGISTER=$(curl -fsS -X POST "$BASE_URL/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" 2>/dev/null); then
    break
  fi
  sleep 2
done
if [ -z "$REGISTER" ]; then
  echo "error: registration failed, check that all services are UP in Eureka" >&2
  exit 1
fi
TOKEN=$(printf '%s' "$REGISTER" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

echo "Reading hotels and rooms ..."
HOTELS=""
for _ in $(seq 1 30); do
  if HOTELS=$(curl -fsS "$BASE_URL/api/hotels" -H "Authorization: Bearer $TOKEN" 2>/dev/null); then
    break
  fi
  sleep 2
done
if [ -z "$HOTELS" ]; then
  echo "error: Gateway cannot reach hotel-service yet" >&2
  exit 1
fi
HOTEL_ID=$(printf '%s' "$HOTELS" | python3 -c 'import sys,json; print(json.load(sys.stdin)["content"][0]["id"])')
ROOMS=$(curl -fsS "$BASE_URL/api/rooms?hotelId=$HOTEL_ID" -H "Authorization: Bearer $TOKEN")
ROOM_ID=$(printf '%s' "$ROOMS" | python3 -c 'import sys,json; print(json.load(sys.stdin)["content"][0]["id"])')

echo "Creating booking for room $ROOM_ID ..."
BOOKING=$(curl -fsS -X POST "$BASE_URL/api/bookings" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: smoke-$(date +%s)" \
  -H "Content-Type: application/json" \
  -d "{\"roomId\":$ROOM_ID,\"hotelId\":$HOTEL_ID,\"startDate\":\"$START\",\"endDate\":\"$END\",\"autoSelect\":false}")
BOOKING_ID=$(printf '%s' "$BOOKING" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
echo "Booking $BOOKING_ID created"

curl -fsS "$BASE_URL/api/bookings" -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Cancelling booking $BOOKING_ID ..."
curl -fsS -X DELETE "$BASE_URL/api/bookings/$BOOKING_ID" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Checking internal endpoint is blocked by Gateway ..."
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/rooms/$ROOM_ID/release")
if [ "$STATUS" != "404" ]; then
  echo "Expected 404 for internal release, got $STATUS" >&2
  exit 1
fi

echo "SMOKE TEST PASSED"
