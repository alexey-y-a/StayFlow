# Справочник API StayFlow

Базовый URL для внешних клиентов: `http://localhost:8080` (API Gateway).

Аутентификация: `Authorization: Bearer <JWT>`. Токен выдаётся на 1 час.

Публичные эндпоинты: регистрация, вход, `/actuator/health`, `/actuator/info`.

## 1. Формат ответов

Списки возвращаются в обёртке `PageResponse`:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "sort": "name: ASC"
}
```

Параметры пагинации: `page` (с 0), `size`, `sort=поле,asc|desc`.

Ошибки - единый `ErrorDTO`:

```json
{
  "timestamp": "2026-10-22T09:15:30.123Z",
  "status": 409,
  "code": "CONFLICT",
  "message": "Room is not available for requested dates",
  "path": "/api/bookings",
  "traceId": "3f1c9a7e-...",
  "details": []
}
```

Для ошибок валидации `details` содержит список `{"field": "...", "message": "..."}`.

| `code` | HTTP | Когда возникает |
|---|---|---|
| `VALIDATION_ERROR` | 400 | нарушены ограничения полей |
| `BAD_REQUEST` | 400 | некорректный запрос, даты, параметры |
| `UNAUTHORIZED` | 401 | нет токена, токен истёк или неверен |
| `FORBIDDEN` | 403 | роль не подходит, чужое бронирование |
| `NOT_FOUND` | 404 | объект не найден |
| `CONFLICT` | 409 | номер занят, дубль, нарушение состояния |
| `IDEMPOTENCY_CONFLICT` | 409 | тот же ключ с другим телом запроса |
| `METHOD_NOT_ALLOWED` | 405 | метод не поддержан |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | не тот Content-Type |
| `REMOTE_SERVICE_ERROR` | 502 | Hotel вернул ошибку |
| `REMOTE_SERVICE_UNAVAILABLE` | 503 | Hotel недоступен, circuit breaker открыт |
| `REMOTE_TIMEOUT` | 504 | тайм-аут вызова Hotel |
| `INTERNAL_ERROR` | 500 | непредвиденная ошибка |

## 2. Аутентификация

### POST /api/auth/register

Регистрация пользователя с ролью `USER`.

Запрос:

```json
{"username": "ivan", "password": "Password123!"}
```

Ответ `201`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": 3,
  "username": "ivan",
  "role": "USER"
}
```

Ошибки: 400 (валидация), 409 (логин занят).

### POST /api/auth/login

Запрос:

```json
{"username": "user", "password": "User123!"}
```

Ответ `200` - тот же `AuthResponse`. Ошибки: 401 (неверные данные), 403 (пользователь отключён).

## 3. Пользователи (ADMIN)

Все запросы требуют роль `ADMIN`.

| Метод | Путь | Тело / параметры | Ответ |
|---|---|---|---|
| GET | `/api/users` | `page`, `size`, `sort` (по умолчанию `username`) | `PageResponse<UserResponse>` |
| POST | `/api/users` | `{"username","password","role"}` | 201, `UserResponse` |
| PATCH | `/api/users` | `{"id","username?","password?","role?","enabled?"}` | 200, `UserResponse` |
| DELETE | `/api/users?id={id}` | - | 204 |

`UserResponse`: `id`, `username`, `role`, `enabled`, `createdAt`, `updatedAt`.

Удалить самого себя нельзя: 400 `BAD_REQUEST` («You cannot delete yourself»). Удаление последнего администратора даёт 409.

## 4. Отели

### GET /api/hotels (USER, ADMIN)

Параметры фильтра: `city`, `name`, `minRating`, `maxRating`; пагинация `page`, `size`, `sort` (по умолчанию `name`).

```bash
curl -sS "http://localhost:8080/api/hotels?city=Москва&minRating=4.5&page=0&size=5" \
  -H "Authorization: Bearer $TOKEN"
```

### GET /api/hotels/{id}

Ответ `HotelResponse`: `id`, `name`, `city`, `address`, `description`, `rating`, `createdAt`, `updatedAt`.

### POST /api/hotels (ADMIN)

```json
{
  "name": "Grand Stay",
  "city": "Москва",
  "address": "ул. Тверская, 1",
  "description": "Отель в центре",
  "rating": 4.8
}
```

Ответ `201` с `Location`.

### PUT /api/hotels/{id} (ADMIN)

Тело как у создания, ответ `200`.

### DELETE /api/hotels/{id} (ADMIN)

`204`. Если в отеле есть номера, вернётся `409`.

### GET /api/hotels/{id}/statistics (ADMIN)

```json
{
  "hotelId": 1,
  "hotelName": "Grand Stay",
  "totalRooms": 4,
  "availableRooms": 4,
  "occupiedRooms": 0,
  "activeReservations": 0,
  "occupancyRate": 0.0,
  "totalTimesBooked": 13,
  "averageTimesBooked": 3.25,
  "topRooms": [
    {"roomId": 2, "number": "102", "timesBooked": 7, "activeReservations": 0}
  ]
}
```

## 5. Номера

### GET /api/rooms (USER, ADMIN)

Фильтры: `hotelId`, `type` (`SINGLE`, `DOUBLE`, `TWIN`, `SUITE`, `FAMILY`), `minCapacity`, `maxPrice`, `available`; пагинация `page`, `size`, `sort` (по умолчанию `id`).

`RoomResponse`: `id`, `hotelId`, `hotelName`, `number`, `type`, `capacity`, `pricePerNight`, `floor`, `available`, `timesBooked`, `description`, `createdAt`, `updatedAt`.

### GET /api/rooms/{id} (USER, ADMIN)

`200` или `404`.

### GET /api/rooms/recommend (USER, ADMIN)

Рекомендации для автоподбора: свободные на даты номера, отсортированные по возрастанию `timesBooked`, при равенстве - по `id`.

| Параметр | Обязателен | Назначение |
|---|---|---|
| `hotelId` | нет | ограничить отелем |
| `startDate` | нет | заезд, ISO-дата |
| `endDate` | нет | выезд, ISO-дата |

```bash
curl -sS "http://localhost:8080/api/rooms/recommend?hotelId=1&startDate=2026-11-01&endDate=2026-11-05" \
  -H "Authorization: Bearer $TOKEN"
```

Ответ - массив `RoomResponse` (без обёртки пагинации).

### POST /api/rooms (ADMIN)

```json
{
  "hotelId": 1,
  "number": "305",
  "type": "DOUBLE",
  "capacity": 2,
  "pricePerNight": 7300.00,
  "floor": 3,
  "available": true,
  "description": "Номер с балконом"
}
```

Ответ `201`. Ошибки: 404 (отеля нет), 409 (номер с таким `number` уже есть в отеле).

### PUT /api/rooms/{id}, DELETE /api/rooms/{id} (ADMIN)

Тело обновления: `number`, `type`, `capacity`, `pricePerNight`, `floor`, `available`, `description`. Удаление - `204`.

## 6. Бронирования (USER, ADMIN)

### POST /api/bookings

Обязательный заголовок `Idempotency-Key` (до 100 символов). Повтор с тем же ключом возвращает существующее бронирование.

```json
{
  "hotelId": 1,
  "roomId": 2,
  "startDate": "2026-11-01",
  "endDate": "2026-11-05",
  "autoSelect": false
}
```

- `autoSelect: true` - `roomId` игнорируется, номер выбирается по рекомендациям;
- `autoSelect: false` - бронируется указанный `roomId` (обязателен);
- `startDate < endDate`, даты не в прошлом.

Ответ `201`:

```json
{
  "id": "0f2f6a3e-8f3f-4c4b-9a1e-3d2f5c7b9a11",
  "userId": 2,
  "username": "user",
  "roomId": 2,
  "hotelId": 1,
  "startDate": "2026-11-01",
  "endDate": "2026-11-05",
  "status": "CONFIRMED",
  "totalPrice": 27200.00,
  "currency": "RUB",
  "requestId": "demo-1",
  "createdAt": "2026-10-22T09:20:00Z",
  "updatedAt": "2026-10-22T09:20:00Z"
}
```

Ошибки: 400 (даты, отсутствует `roomId` при `autoSelect=false`), 401, 409 (номер занят), 502/503/504 (Hotel).

### GET /api/bookings

История текущего пользователя (ADMIN видит только свои, если не запрашивает по id).

| Параметр | Назначение |
|---|---|
| `status` | фильтр `PENDING`, `CONFIRMED`, `CANCELLED` |
| `page`, `size` | пагинация, размер по умолчанию 10 |
| `sort` | по умолчанию `createdAt,desc` |

### GET /api/bookings/{id}

Владелец или ADMIN. Иначе `403`. Несуществующее - `404`.

### DELETE /api/bookings/{id}

Отмена. Если бронирование было `CONFIRMED`, Booking вызывает `release` в Hotel, снимает слот и переводит в `CANCELLED`. Ответ `204`.

Повторная отмена идемпотентна: `204`.

## 7. Внутренние эндпоинты Hotel Service

Не публикуются через Gateway: Gateway возвращает `404` на эти пути. Вызываются только из Booking Service с заголовком `X-Internal-Api-Key`.

### POST /api/rooms/{id}/confirm-availability

```json
{
  "bookingId": "0f2f6a3e-8f3f-4c4b-9a1e-3d2f5c7b9a11",
  "requestId": "9f2c...",
  "startDate": "2026-11-01",
  "endDate": "2026-11-05"
}
```

Ответ `200`:

```json
{
  "roomId": 2,
  "bookingId": "0f2f6a3e-...",
  "available": true,
  "roomNumber": "102",
  "hotelId": 1,
  "hotelName": "Grand Stay",
  "pricePerNight": 6800.00,
  "startDate": "2026-11-01",
  "endDate": "2026-11-05"
}
```

Повтор с тем же `requestId` возвращает сохранённый результат, `times_booked` не растёт.

Ошибки: 401 (нет или неверный ключ), 404 (номера нет), 409 (номер не в эксплуатации, даты пересекаются, у бронирования уже есть слот).

### POST /api/rooms/{id}/release

```json
{"bookingId": "0f2f6a3e-...", "requestId": "7a1d..."}
```

Ответ `200`:

```json
{"roomId": 2, "bookingId": "0f2f6a3e-...", "released": true}
```

Повтор идемпотентен. Ошибки: 401, 404, 409 (бронирование относится к другому номеру).

## 8. Порядок вызова для клиента

1. `POST /api/auth/register` или `POST /api/auth/login` - получить JWT;
2. `GET /api/hotels` - выбрать отель;
3. `GET /api/rooms/recommend?hotelId=...&startDate=...&endDate=...` - посмотреть свободные номера;
4. `POST /api/bookings` с `Idempotency-Key` - создать бронирование;
5. `GET /api/bookings` - проверить историю;
6. `DELETE /api/bookings/{id}` - отменить при необходимости.

Для администратора дополнительно: CRUD отелей и номеров, статистика `GET /api/hotels/{id}/statistics`, управление пользователями `/api/users`.
