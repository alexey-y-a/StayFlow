#!/usr/bin/env bash
# Логин seed-пользователей и сохранение токенов в файлы.
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}"

login() {
  curl -s -X POST "$BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)[\"token\"])"
}

USER_TOKEN=$(login user User123!)
ADMIN_TOKEN=$(login admin Admin123!)

printf '%s\n' "$USER_TOKEN" > /tmp/stayflow-user-token.txt
printf '%s\n' "$ADMIN_TOKEN" > /tmp/stayflow-admin-token.txt

echo "user token: ${#USER_TOKEN} chars"
echo "admin token: ${#ADMIN_TOKEN} chars"
echo "files: /tmp/stayflow-user-token.txt, /tmp/stayflow-admin-token.txt"
