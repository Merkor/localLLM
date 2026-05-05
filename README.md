# Local LLM Chat

Локальное Spring Boot приложение с интерфейсом в стиле простого ChatGPT:
- backend на Java 21 / Spring Boot 4;
- UI на Thymeleaf (без отдельного Node.js frontend);
- Ollama в Docker;
- PostgreSQL + Flyway для хранения истории чатов и сообщений.

## Что умеет

- список чатов, создание, переименование, удаление;
- автоматическое имя чата по первому сообщению;
- история сообщений в PostgreSQL;
- streaming ответа ассистента через SSE (`POST /api/chats/{id}/messages/stream`);
- markdown-рендеринг ответов ассистента и copy-кнопка;
- контекст из последних `N` сообщений + системный prompt.

## Требования

- Docker
- Docker Compose
- (опционально) NVIDIA Container Toolkit для GPU-ускорения Ollama

## Быстрый запуск

```bash
docker compose up --build -d
```

После запуска:
- UI: http://localhost:8877
- Дополнительно (второй проброс того же приложения): http://localhost:8080
- Ollama API: http://localhost:11434

Модель `qwen2.5:7b-instruct-q4_K_M` подтягивается автоматически сервисом `ollama-pull` при первом запуске.

## Логи

```bash
docker compose logs -f app
docker compose logs -f ollama
```

## Остановка

```bash
docker compose down
```

## Полная очистка данных

```bash
docker compose down -v
```

## Смена модели

Измените переменную `APP_LLM_MODEL` в `docker-compose.yml`, например:

```yaml
APP_LLM_MODEL: qwen2.5:7b-instruct-q4_K_M
```

## Запуск без GPU

Закомментируйте блок `deploy.resources` в сервисе `ollama` в `docker-compose.yml`.

## Запуск из IDE (локально)

1. Поднимите только инфраструктуру:
   ```bash
   docker compose up -d postgres ollama ollama-pull
   ```
2. Запустите `LocalLlmChatApplication` из IDE с профилем `local`.
3. Приложение будет использовать:
   - PostgreSQL: `localhost:5432`
   - Ollama: `localhost:11434`

## Полезные настройки (`application.yml`)

- `app.llm.model` — модель Ollama
- `app.llm.temperature` — температура
- `app.llm.keep-alive` — keep-alive сессии модели
- `app.llm.max-context-messages` — сколько последних сообщений передавать в контекст
- `app.llm.system-prompt` — системная инструкция

## REST API

- `GET /api/chats`
- `POST /api/chats`
- `GET /api/chats/{id}`
- `PATCH /api/chats/{id}`
- `DELETE /api/chats/{id}`
- `GET /api/chats/{id}/messages`
- `POST /api/chats/{id}/messages/stream`

## Структура слоев

- `controller` — web и REST endpoints
- `service` — бизнес-логика чатов и сообщений
- `repository` — JPA доступ к данным
- `entity` — JPA сущности
- `dto` — API-модели
- `config` — конфигурация и properties
- `llm` — интеграция с Ollama
