# Тестирование StayFlow

## 1. Запуск

Полный прогон всех модулей:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn clean verify
```

Один модуль:

```bash
mvn -pl hotel-service -am test
mvn -pl booking-service -am test
```

Один класс или метод:

```bash
mvn -pl booking-service test -Dtest=BookingServiceIntegrationTest
mvn -pl hotel-service test -Dtest=AvailabilityServiceIntegrationTest#confirmIsIdempotentByRequestId
```

Отчёт покрытия JaCoCo после `verify`:

- `common/target/site/jacoco/index.html`
- `hotel-service/target/site/jacoco/index.html`
- `booking-service/target/site/jacoco/index.html`

## 2. Как устроены тесты

- профиль `test` включается через `@ActiveProfiles("test")` или `spring.profiles.active` в тесте;
- `src/test/resources/application-test.yml` задаёт свой JWT-секрет, внутренний ключ, отключает Eureka и seed;
- `SecurityConfig` в сервисах помечен `@Profile("!test")` там, где тесту нужна своя цепочка фильтров;
- удалённые вызовы Hotel в тестах Booking подменяются WireMock;
- базы H2 создаются заново на каждый контекст, тесты не зависят от окружения и порядка запуска.

## 3. Что покрыто

### common (11 тестов)

| Класс | Проверяет |
|---|---|
| `JwtTokenServiceTest` | выпуск и разбор токена, роли, срок жизни |
| `JwtConfigTest` | создание кодировщика и декодера, поведение без секрета |
| `GlobalExceptionHandlerTest` | единый `ErrorDTO`, коды и статусы |
| `TraceIdFilterTest` | `X-Request-Id` → MDC, генерация при отсутствии |
| `PageResponseTest` | преобразование `Page` в ответ |
| `CurrentUserTest` | извлечение текущего пользователя |

### api-gateway (8 тестов)

`GatewayRouteTest` на WireMock проверяет: маршрутизацию логина и истории броней на Booking, отелей и номеров на Hotel, JSON 401 без токена, блокировку `/api/rooms/{id}/confirm-availability` и `release` (404), удаление подделанного `X-Internal-Api-Key`, доступ к админскому пути `/api/users`.

### hotel-service (29 тестов)

| Класс | Проверяет |
|---|---|
| `HotelServiceIntegrationTest` | CRUD отелей, запрет удаления отеля с номерами |
| `HotelControllerTest`, `RoomControllerTest` | валидация, коды ответов, пагинация |
| `AvailabilityServiceIntegrationTest` | подтверждение доступности, рост `timesBooked`, идемпотентность по `requestId`, параллельная повторная доставка `confirm`, отказ при пересечении дат, release и его идемпотентность, сортировка рекомендаций, параллельные подтверждения |
| `RoomReservationRepositoryTest` | уникальные ограничения, check-констрейнты, поиск пересечений |
| `SecurityIntegrationTest` | роли, 401/403 в JSON, внутренний ключ, внутренний recommend |

### booking-service (39 тестов)

| Класс | Проверяет |
|---|---|
| `BookingServiceIntegrationTest` | создание с подтверждением, идемпотентность по `Idempotency-Key`, конфликт при повторном ключе с другим телом, отказ при занятом номере, переход к следующему кандидату при `autoSelect`, история только своих, отказ в доступе к чужой брони, отмена с освобождением слота |
| `BookingRecoveryTest` | восстановление зависших `PENDING`, повтор компенсации, сохранение `PENDING` при недоступном Hotel |
| `HttpHotelGatewayTest` | retry, тайм-аут, circuit breaker на WireMock, разбор ответов Hotel |
| `BookingControllerTest` | создание, валидация тела, история, отмена, коды ответов |
| `AuthIntegrationTest` | регистрация, вход, дубль логина, отключённый пользователь |
| `SecurityIntegrationTest` | 401 в JSON, роли USER/ADMIN, CRUD пользователей |
| `BookingRepositoryTest` | уникальность `request_id` и `username`, журнал `booking_events` |
| `BookingConcurrencyIntegrationTest` | параллельные запросы с одним `Idempotency-Key`, параллельные брони одного номера, отсутствие дублей |

## 4. Нагрузочная проверка

```bash
./scripts/load-test.sh 50
```

Скрипт регистрирует отдельного пользователя и берёт отель из seed. Фаза 1: 50 параллельных броней с разными `Idempotency-Key` на одни даты, ожидаются несколько `201` по числу свободных номеров и остальные `409`, ни одного `5xx`. Фаза 2: 50 параллельных запросов с одним ключом, ожидаются 50 ответов `201` с одним бронированием и прирост ровно на одну запись. В конце печатается статистика отеля.

Даты сдвигаются на каждый прогон, поэтому повтор не упирается в занятые слоты.

Результат последнего прогона: фаза 1 - 201=4, 409=46, other=0 за 1 с; фаза 2 - 201=50, other=0, добавлена одна запись; `LOAD TEST PASSED`.

## 5. Сквозные проверки

1. Регистрация и вход, получение JWT.
2. Список отелей и номеров, рекомендации по возрастанию `timesBooked`.
3. Создание бронирования: `CONFIRMED` при свободном номере.
4. Повтор того же запроса с тем же `Idempotency-Key`: то же бронирование, без дубля.
5. Бронирование занятого номера: 409, статус `CANCELLED`.
6. Остановленный Hotel: 502/503/504, компенсация, `compensation_status`.
7. Отмена бронирования: слот освобождается, повторная отмена идемпотентна.
8. Доступ: USER не может в CRUD отелей и пользователей, чужое бронирование даёт 403.
9. Gateway: прямой вызов внутренних эндпоинтов Hotel через 8080 даёт 404.
10. Параллельные запросы с одним `Idempotency-Key` дают одно бронирование, параллельные брони одного номера подтверждают только одну.

Автоматическая проверка сквозного сценария:

```bash
docker compose up --build -d
./scripts/smoke-test.sh
```
