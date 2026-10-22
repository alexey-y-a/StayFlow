# Архитектурные решения (ADR)

| ADR | Решение |
|---|---|
| [0001](0001-database-per-service.md) | Отдельная база у каждого сервиса |
| [0002](0002-saga-orchestration.md) | Сага с оркестрацией в Booking Service |
| [0003](0003-idempotency.md) | Идемпотентность через requestId и уникальные индексы |
| [0004](0004-resilience-in-process.md) | Resilience4j вместо очередей и брокера |
| [0005](0005-recovery-job.md) | DB-backed recovery job вместо брокера сообщений |
| [0006](0006-jwt-per-service.md) | Проверка JWT в каждом сервисе, internal-эндпоинты по ключу |

Формат: контекст, решение, последствия, альтернативы.
