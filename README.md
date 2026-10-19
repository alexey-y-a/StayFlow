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

## Статус

Проект в разработке. Инструкции по запуску, описание API и архитектурные решения будут добавлены по мере реализации.