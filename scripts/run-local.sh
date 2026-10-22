#!/usr/bin/env bash
# Локальный запуск сервиса StayFlow с секретами из .env.
# Использование: ./scripts/run-local.sh <module>
# Модули: eureka-server, api-gateway, hotel-service, booking-service

set -euo pipefail

MODULE="${1:-}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ -z "$MODULE" ]]; then
  echo "Usage: $0 <module>" >&2
  echo "Modules: eureka-server, api-gateway, hotel-service, booking-service" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "error: $ENV_FILE not found. Copy .env.example to .env and fill in the values." >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

for var in JWT_SECRET INTERNAL_API_KEY; do
  if [[ -z "${!var:-}" ]]; then
    echo "error: $var is empty in $ENV_FILE" >&2
    exit 1
  fi
done

cd "$ROOT_DIR"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"

mvn -q -DskipTests -pl "$MODULE" -am install
exec mvn -q -pl "$MODULE" spring-boot:run
