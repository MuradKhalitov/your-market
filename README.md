# YourMarket

YourMarket — MVP платной Telegram-доски объявлений. Пользователь создаёт объявление в диалоге с ботом, проверяет предпросмотр, оплачивает Telegram Invoice через ЮKassa, после чего бот публикует фотографию и описание в канале.

## Архитектура

Приложение написано на Java 21 и Spring Boot 3. Слои разделены на `controller`, `service`, `repository`, `model`, `dto`, `mapper` и Telegram-адаптер. PostgreSQL хранит пользователей, черновики, объявления и платежи. Схему создаёт Liquibase. Telegram-слой только разбирает `Update` и вызывает сервисы; переходы статусов выполняются транзакционно.

Критические операции оплаты и публикации используют блокировки БД и уникальные ограничения. Повторный `SuccessfulPayment`, повторная публикация и повторное удаление безопасны.

## Сценарий пользователя

1. `/start` открывает главное меню.
2. «Разместить объявление» запускает сохранённый в БД пошаговый черновик: категория, название, описание, цена, фотография, город и контакт.
3. Бот показывает предпросмотр. Ошибочный ввод не меняет текущий шаг.
4. «Оплатить» создаёт объявление и Invoice на стоимость публикации.
5. Pre-checkout сверяет payload, пользователя, валюту, сумму и статусы с БД.
6. После `SuccessfulPayment` платёж фиксируется как `SUCCEEDED`, объявление — как `PAID`; затем выполняется публикация.
7. «Мои объявления» показывает до 10 последних записей. Опубликованное объявление можно снять с канала.

## Настройка Telegram и ЮKassa

1. Откройте `@BotFather`, выполните `/newbot`, задайте имя и username, сохраните токен.
2. Создайте публичный Telegram-канал и задайте ему username.
3. Добавьте бота администратором с правами публикации и удаления сообщений.
4. В BotFather выберите бот → Payments, подключите **ЮKassa Test** и сохраните provider token.
5. ID канала можно узнать, переслав сообщение канала боту вроде `@userinfobot`, либо вызвав Bot API `getUpdates` после публикации тестового сообщения. ID супергруппы/канала обычно начинается с `-100`.

Никогда не коммитьте токены. Скопируйте `.env.example` в `.env` и заполните:

- `TELEGRAM_BOT_USERNAME`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_CHANNEL_ID`
- `TELEGRAM_CHANNEL_USERNAME`
- `TELEGRAM_CHANNEL_URL`
- `YOOKASSA_PROVIDER_TOKEN`
- при необходимости `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PUBLICATION_PRICE`

Spring Boot сам не читает `.env` при обычном локальном запуске: экспортируйте переменные в оболочку или настройте их в IDE. Альтернатива — скопировать `application-local.yml.example` в игнорируемый `application-local.yml` и запустить с профилем `local`.

## Локальный запуск

Требуется JDK 21 и Docker.

```bash
docker compose up -d
./mvnw spring-boot:run
```

Windows:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Для файла локального профиля добавьте `-Dspring-boot.run.profiles=local`.

Тестовый платёж: запустите бота с тестовым provider token, создайте объявление, нажмите «Оплатить» и используйте тестовые реквизиты, показанные ЮKassa. Реальные запросы Telegram/ЮKassa в автоматических тестах не выполняются.

## Сборка и тесты

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

Интеграционный тест поднимает PostgreSQL через Testcontainers и автоматически пропускается, если Docker недоступен.

## Административный retry

`POST /api/admin/advertisements/{id}/retry-publication` повторяет публикацию для `PAID` или `PUBLICATION_FAILED` и не дублирует уже `PUBLISHED`.

Endpoint защищён заголовком `X-Admin-Api-Key`. Значение задаётся через `ADMIN_API_KEY`.
В production-профиле приложение не запускается с пустым ключом.

## Дополнительные возможности

Поддерживаются ручная модерация, публикация до пяти фотографий, пользовательский retry,
автоматическое истечение публикации, PostgreSQL rate limit и API-key для admin endpoint.
При отклонении модератором возврат оплаты выполняется вручную: автоматический refund через
ЮKassa в MVP не реализован.

## Ограничения MVP

### Ручная сверка публикации

Telegram Bot API не поддерживает idempotency key для отправки сообщения. Если Telegram уже вернул
`messageId`, но сохранение результата в PostgreSQL завершилось ошибкой, объявление переводится в
`PUBLICATION_RECONCILIATION_REQUIRED`; автоматический и пользовательский retry блокируются, чтобы не
создать дубликат. Администратор сначала сверяет канал, затем вызывает защищённый endpoint:

`POST /api/admin/advertisements/{id}/resolve-publication?action=MARK_PUBLISHED&channelMessageId=123`

или, если публикации в канале точно нет:

`POST /api/admin/advertisements/{id}/resolve-publication?action=RETRY_AFTER_VERIFICATION`

Оба вызова требуют заголовок `X-Admin-Api-Key`.

Invoice с неоднозначным результатом отправки получает состояние `SEND_UNKNOWN` и автоматически не
отправляется повторно. После ручной проверки администратор может вызвать защищённый endpoint
`POST /api/admin/advertisements/payments/{paymentId}/resolve-invoice?retryAllowed=true|false`.

Промежуточный прогресс публикации и модерации сохраняется после каждого успешного Telegram-вызова.
Неоднозначные операции переходят в reconciliation и не повторяют уже отправленные media group.

- один бот, один канал и одна фиксированная цена публикации;
- от одной до пяти фотографий; поля можно редактировать до создания Invoice;
- нет возвратов и автоматического планировщика retry (retry доступен через admin endpoint);
- long polling и синхронные вызовы Telegram API;
- нет Web UI и пользовательской модерации;
- защита admin endpoint оставлена задачей production-этапа.
