# YourMarket

YourMarket — Telegram-доска объявлений на Java 17 и Spring Boot 3. Пользователь создаёт объявление, оплачивает публикацию в Telegram Stars и получает публикацию в канале.

Для локальной разработки требуется JDK 17. Проверьте его командой `java -version`; для сборки используйте Maven Wrapper (`.\mvnw.cmd`). Runtime-образ Docker использует Eclipse Temurin 17 JRE.

## Оплата

Публикация поддерживает только Telegram Stars:

- `PUBLICATION_PRICE_STARS` — целое положительное количество Stars;
- каждый новый `Payment` хранит `amount` как `integer` и `currency = XTR`;
- Invoice не содержит provider token и включает ровно одну цену;
- pre-checkout и successful payment сверяются со snapshot платежа, а не с текущей конфигурацией;
- `telegramPaymentChargeId` сохраняется для идемпотентного возврата Stars через сервисный слой.

## Конфигурация

Скопируйте `.env.example` в локальный секретный файл или настройте переменные в IDE. Обязательны:

- `TELEGRAM_BOT_USERNAME`, `TELEGRAM_BOT_TOKEN`;
- `TELEGRAM_CHANNEL_ID`, `TELEGRAM_CHANNEL_USERNAME`, `TELEGRAM_CHANNEL_URL`;
- `PUBLICATION_PRICE_STARS` (минимум `1`);
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`;
- `ADMIN_API_KEY` для production.

При включённой модерации обязательны `TELEGRAM_MODERATION_CHAT_ID` и `TELEGRAM_ADMIN_USER_IDS`.

## Локальный запуск

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Проверка:

```powershell
.\mvnw.cmd clean verify
```

Тесты используют PostgreSQL 17 через Testcontainers и не выполняют реальные запросы в Telegram.

## Чистая база данных

Liquibase использует один актуальный baseline `001-stars-baseline.yaml`. Он рассчитан на новую пустую базу данных и не мигрирует прежние схемы оплаты.

Для безопасного пересоздания локальной тестовой БД остановите приложение, затем:

```powershell
docker compose down
docker volume rm yourmarket_postgres_data
docker compose up -d
```

На VPS перед пересозданием сделайте резервную копию. После подтверждения, что данные не нужны:

```bash
docker compose --env-file .env.prod -f docker-compose.yml down
docker volume rm yourmarket_postgres_data
docker compose --env-file .env.prod -f docker-compose.yml up -d
```

Никогда не используйте удаление volume, если в PostgreSQL есть нужные данные.

## Деплой на VPS

Собрать и отправить image:

```powershell
.\scripts\build-and-push.ps1 -DockerHubUsername username -Version 1.0.0
```

На VPS скопируйте compose-файл и `.env.prod`, заполните секреты, затем:

```bash
chmod 600 .env.prod
docker compose --env-file .env.prod -f docker-compose.yml pull
docker compose --env-file .env.prod -f docker-compose.yml up -d
docker compose --env-file .env.prod -f docker-compose.yml ps
docker compose --env-file .env.prod -f docker-compose.yml logs -f app
```

В production должен работать ровно один экземпляр бота с данным token; Telegram webhook должен быть отключён для long polling. PostgreSQL не публикуется наружу, а admin HTTP endpoint привязан к localhost.

## Ограничения MVP

- публикация и внешние вызовы Telegram используют durable claim/reconciliation state;
- неоднозначные результаты invoice или публикации требуют ручной проверки администратора;
- возврат Stars реализован как сервисный процесс, без публичного endpoint;
- нет Web UI и нет автоматического возврата при отклонении модерации.
