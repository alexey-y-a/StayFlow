# StayFlow

Платформа бронирования отелей на Spring Boot и Spring Cloud.

## О проекте

StayFlow - микросервисная система бронирования отелей с API Gateway, Eureka, JWT-аутентификацией и сагой подтверждения бронирования.

## Планируемая архитектура

| Модуль | Назначение | Порт |
|---|---|---|
| `eureka-server` | Service Discovery | 8761 |
| `api-gateway` | Маршрутизация, JWT, трассировка | 8080 |
| `hotel-service` | Отели, номера, доступность, статистика | 8081 |
| `booking-service` | Пользователи, бронирования, сага | 8082 |
| `common` | Общие DTO, ошибки, трассировка | - |

## Стек

- Java 21
- Spring Boot 3.5.16
- Spring Cloud 2025.0.3
- Spring Security, JWT
- Spring Data JPA, H2
- Maven
- JUnit 5, MockMvc, WireMock

## Переменные окружения

Секреты в репозитории не хранятся. Значения задаются в файле `.env` в корне проекта; файл добавлен в `.gitignore`, в git попадает только шаблон `.env.example`.

```bash
cp .env.example .env
```

| Переменная | Назначение |
|---|---|
| `JWT_SECRET` | Base64-ключ HS256 для подписи и проверки токенов (не короче 32 байт в декодированном виде) |
| `INTERNAL_API_KEY` | Ключ внутренних эндпоинтов Hotel Service (`X-Internal-Api-Key`) |

Генерация значений:

```bash
openssl rand -base64 32   # JWT_SECRET
openssl rand -hex 32      # INTERNAL_API_KEY
```

Одно значение `JWT_SECRET` должно использоваться всеми сервисами: Booking выпускает токены, Gateway и Hotel их проверяют.

Тесты не зависят от `.env`: значения заданы в `src/test/resources/application-test.yml`.

## Запуск

Все сервисы в Docker:

```bash
docker compose up --build
```

Compose читает `.env` из корня проекта и передаёт переменные контейнерам.

Локальный запуск одного сервиса без Docker (переменные окружения подхватываются из `.env`):

```bash
./scripts/run-local.sh eureka-server
./scripts/run-local.sh hotel-service
./scripts/run-local.sh booking-service
./scripts/run-local.sh api-gateway
```

Порядок запуска: Eureka, затем Hotel и Booking, затем Gateway. Каждый сервис запускается в отдельном терминале.

## Статус

Проект в разработке.
