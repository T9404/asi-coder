# ASI Coder

Монорепозиторий для LLM/MCP стеков вокруг управления задачами (YouTrack), документацией (Confluence) и вспомогательных интеграций. 

## Быстрый старт
- Подготовьте секреты: `OPENAI_API_KEY`, `YOUTRACK_TOKEN`, `CONFLUENCE_USERNAME`, `CONFLUENCE_API_TOKEN`, `GITHUB_PERSONAL_ACCESS_TOKEN`, `TELEGRAM_API_ID/HASH/SESSION_STRING`, учётки для Grafana (`GF_SECURITY_*`) и n8n БД.
- Локальный запуск сервисов: `docker compose up -d` (порты см. ниже). Для отдельных модулей Gradle-образы можно поднять через `./gradlew bootRun` внутри каталога сервиса.
- Основные порты: 9091 (project-manager-client), 8082 (youtrack-mcp-server), 8084 (confluence-mcp-server), 9095 (documentation-agent-client), 9099 (coordinator), 3000 (Grafana), 9090 (Prometheus), 5678 (n8n), 8080 (YouTrack), 8090 (Confluence).

## Модули

### project-manager-client
Java/Spring Boot клиент MCP для управления задачами через LLM. Открывает HTTP сервис с инструментом `issue_operation`, который планирует шаги и выполняет запросы к YouTrack MCP.
- Конфигурация: `OPENAI_API_KEY`, `YOUTRACK_MCP_SERVER_URL` (по умолчанию `http://localhost:8082`), `SERVER_PORT` (9091). Файл `project-manager-client/src/main/resources/application.yaml`.
- Сборка/запуск: `./gradlew bootRun` или `./gradlew build` в каталоге `project-manager-client`.
- Наблюдаемость: actuator, метрики Micrometer/Prometheus (`/actuator/prometheus`, лейбл `application=project-manager-client`).

### youtrack-mcp-server
MCP сервер (SSE, sync) для работы с YouTrack: поиск, чтение и изменение задач, полей, комментариев, тегов, ссылок.
- Конфигурация: `YOUTRACK_URL` (по умолчанию `http://localhost:8080`), `YOUTRACK_TOKEN`, `SERVER_PORT` (8082). Файл `youtrack-mcp-server/src/main/resources/application.yaml`.
- Запуск: `./gradlew bootRun` внутри `youtrack-mcp-server` или контейнер из `docker-compose.yml`.

### confluence-mcp-server
MCP сервер (SSE, sync) для Confluence: создание/обновление страниц, блогов, комментариев и вложений.
- Конфигурация: `CONFLUENCE_URL` (`http://localhost:8090/rest/api` по умолчанию), `CONFLUENCE_USERNAME`, `CONFLUENCE_API_TOKEN`, `SERVER_PORT` (8084). См. `confluence-mcp-server/src/main/resources/application.yaml`.
- Запуск: `./gradlew bootRun` в `confluence-mcp-server` или контейнер из docker compose.

### documentation-agent-client
LLM агент, который через MCP взаимодействует с Confluence и Project Manager. Поднимает собственный MCP сервер (`documentation-agent-server`) и синхронный MCP клиент.
- Конфигурация: `OPENAI_API_KEY`, `CONFLUENCE_MCP_SERVER_URL` (по умолчанию `http://localhost:8084`), `PM_MCP_SERVER_URL` (`http://localhost:9091`), `SERVER_PORT` (9095). Файл `documentation-agent-client/src/main/resources/application.yaml`.
- Особенности: модель OpenAI (`gpt-5`), таймаут MCP 120s, инструкции требуют отдавать HTML в storage-формате Confluence.
- Запуск: `./gradlew bootRun` в `documentation-agent-client` или контейнер.

### coordinator
Промежуточный MCP клиент/сервер, координирующий вызовы между `project-manager-client` и `documentation-agent-client`.
- Конфигурация: `OPENAI_API_KEY`, `PM_MCP_SERVER_URL` (`http://localhost:9091`), `DOCUMENTATION_AGENT_MCP_SERVER_URL` (`http://localhost:9095`), `SERVER_PORT` (9099). Файл `coordinator/src/main/resources/application.yaml`.
- Запуск: `./gradlew bootRun` в `coordinator` или контейнер.

### Инструменты и интеграции
- **Prometheus** (`prometheus/prometheus.yml`, порт 9090) — сбор метрик от сервисов (включая MCP-клиентов). Поднимается docker compose.
- **Grafana** (порт 3000) — использует provisioning из `grafana/provisioning` и дашборды из `grafana/dashboards/model.json`.
- **n8n** (порт 5678) — no-code оркестратор, данные БД Postgres (`n8n-db`) и пример воркфлоу `n8n/test_workflow.json`.
- **YouTrack** (порт 8080) — основная трекер-служба, данные/конфиг/логи вынесены в volume.
- **Confluence** (порт 8090) с отдельной Postgres (`confluence-db`, порт 5433 наружу) — хранит рабочую документацию.
- **github-mcp-server** (порт 8086) — готовый контейнер с инструментами GitHub; требует `GITHUB_PERSONAL_ACCESS_TOKEN`.
- **telegram-mcp** (порт 7090) — MCP сервер для Telegram, требуется API ID/HASH и session string.

## Полезные заметки
- Включите трассировку MCP (`io.modelcontextprotocol=TRACE`, `org.springframework.ai.mcp=TRACE`) для отладки взаимодействий.
- Все Spring приложения маркируют метрики тегом `application`, что упрощает фильтры в Prometheus/Grafana.
- Для локальной разработки без Docker используйте соответствующие Gradle wrapper’ы в подмодулях; переменные окружения из compose помогут воспроизвести конфиг.
