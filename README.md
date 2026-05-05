# Local LLM Chat

Локальное чат-приложение в стиле ChatGPT на Java 21 / Spring Boot 4, работающее полностью офлайн на базе [Ollama](https://ollama.com).

## Стек технологий

| Компонент   | Версия                    |
|-------------|---------------------------|
| Java        | 21                        |
| Spring Boot | 4.0.6                     |
| PostgreSQL  | 17                        |
| Flyway      | (управляется Spring Boot) |
| Ollama      | latest                    |

**UI:** Thymeleaf + Vanilla JS (без Node.js)  
**Streaming:** Server-Sent Events через `WebFlux` + `SseEmitter`

## Что умеет

- Список чатов, создание, переименование, удаление
- Автоматическое название чата по первому ответу (генерируется LLM)
- История сообщений в PostgreSQL с метриками (токены, время ответа)
- Стриминг ответа ассистента через SSE
- Markdown-рендеринг ответов и кнопка копирования
- Контекст из последних `N` сообщений + системный prompt

## Требования

- Docker & Docker Compose
- (опционально) NVIDIA Container Toolkit для GPU-ускорения Ollama

## Быстрый запуск

```bash
docker compose up --build -d
```

После запуска:
- UI: http://localhost:8877
- Ollama API: http://localhost:11434

Модель `qwen2.5:7b-instruct-q4_K_M` скачивается автоматически сервисом `ollama-pull` при первом старте.

## Конфигурация через переменные окружения

Создайте файл `.env` в корне проекта (или задайте переменные в окружении):

```env
# Модель Ollama
APP_LLM_MODEL=qwen2.5:7b-instruct-q4_K_M

# Температура генерации (0.0 – 1.0)
APP_LLM_TEMPERATURE=0.7

# Keep-alive сессии модели в Ollama (0 = выгружать сразу, -1 = никогда)
APP_LLM_KEEP_ALIVE=1m

# Количество последних сообщений в контексте (1 – 200)
APP_LLM_MAX_CONTEXT_MESSAGES=20

# Таймаут запроса к Ollama (в секундах, 5 – 600)
APP_LLM_TIMEOUT_SECONDS=180

# Системный prompt
APP_LLM_SYSTEM_PROMPT=You are a helpful local AI assistant.

# Пароль PostgreSQL
POSTGRES_PASSWORD=chat_password
```

## Смена модели

Установите переменную `APP_LLM_MODEL` в `.env` или напрямую в `docker-compose.yml`:

```env
APP_LLM_MODEL=llama3.2:3b
```

Модель будет скачана сервисом `ollama-pull` при следующем старте.

## Запуск без GPU

В `docker-compose.yml` закомментируйте блок `deploy.resources` в сервисе `ollama`:

```yaml
# deploy:
#   resources:
#     reservations:
#       devices:
#         - driver: nvidia
#           count: all
#           capabilities: [gpu]
```

## Запуск из IDE (локально)

1. Поднимите инфраструктуру:
   ```bash
   docker compose up -d postgres ollama ollama-pull
   ```
2. Запустите `LocalLlmChatApplication` с профилем `local`.
3. Приложение подключится к:
   - PostgreSQL: `localhost:5432`
   - Ollama: `localhost:11434`

## Полезные команды

```bash
# Логи приложения / Ollama
docker compose logs -f app
docker compose logs -f ollama

# Остановка
docker compose down

# Полная очистка (включая базу данных и скачанные модели)
docker compose down -v
```

## REST API

### Чаты

| Метод  | URL             | Статус | Описание          |
|--------|-----------------|--------|-------------------|
| GET    | /api/chats      | 200    | Список чатов      |
| POST   | /api/chats      | 201    | Создать чат       |
| GET    | /api/chats/{id} | 200    | Получить чат      |
| PATCH  | /api/chats/{id} | 200    | Переименовать чат |
| DELETE | /api/chats/{id} | 204    | Удалить чат       |

### Сообщения

| Метод | URL                              | Описание                  |
|-------|----------------------------------|---------------------------|
| GET   | /api/chats/{id}/messages         | История сообщений         |
| POST  | /api/chats/{id}/messages/stream  | Отправить сообщение (SSE) |

#### Пример стриминга

```
POST /api/chats/{id}/messages/stream
Content-Type: application/json

{"content": "Привет!"}
```

Ответ — поток Server-Sent Events:

```
event: token
data: {"type":"token","content":"Привет","error":null}

event: token
data: {"type":"token","content":"!","error":null}

event: done
data: {"type":"done","content":"","error":null}
```

В случае ошибки (например, Ollama недоступна):

```
event: error
data: {"type":"error","content":"","error":"Ollama is unavailable or returned an error."}
```

## Настройки приложения

| Параметр `application.yml`     | Переменная окружения           | По умолчанию                  |
|--------------------------------|--------------------------------|-------------------------------|
| `app.llm.model`                | `APP_LLM_MODEL`                | `qwen2.5:7b-instruct-q4_K_M` |
| `app.llm.temperature`          | `APP_LLM_TEMPERATURE`          | `0.7`                         |
| `app.llm.keep-alive`           | `APP_LLM_KEEP_ALIVE`           | `1m`                          |
| `app.llm.max-context-messages` | `APP_LLM_MAX_CONTEXT_MESSAGES` | `20`                          |
| `app.llm.timeout-seconds`      | `APP_LLM_TIMEOUT_SECONDS`      | `180`                         |
| `app.llm.system-prompt`        | `APP_LLM_SYSTEM_PROMPT`        | _(встроенный prompt)_         |

## Структура проекта

```
src/main/java/ru/local/llmchat/
├── config/        # Конфигурация Spring и AppLlmProperties
├── controller/    # Web (Thymeleaf) и REST-контроллеры
├── dto/           # API-модели запросов и ответов
├── entity/        # JPA-сущности (ChatSession, ChatMessage)
├── exception/     # Исключения и GlobalExceptionHandler
├── llm/           # Интеграция с Ollama (OllamaLlmService)
├── repository/    # JPA-репозитории
└── service/       # Бизнес-логика (ChatService, MessageService)

src/main/resources/
├── db/migration/  # Flyway-миграции
├── static/        # CSS и JavaScript
└── templates/     # Thymeleaf-шаблоны
```