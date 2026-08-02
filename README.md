# YourMarket

YourMarket — MVP платной Telegram-доски объявлений. Пользователь создаёт объявление в диалоге с ботом, проверяет предпросмотр, оплачивает Telegram Invoice в Telegram Stars, после чего бот публикует фотографию и описание в канале.

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

## Настройка Telegram Stars

1. Откройте `@BotFather`, выполните `/newbot`, задайте имя и username, сохраните токен.
2. Создайте публичный Telegram-канал и задайте ему username.
3. Добавьте бота администратором с правами публикации и удаления сообщений.
4. Публикации оплачиваются через Telegram Stars (`XTR`); provider token ЮKassa для этого сценария не нужен.
5. ID канала можно узнать, переслав сообщение канала боту вроде `@userinfobot`, либо вызвав Bot API `getUpdates` после публикации тестового сообщения. ID супергруппы/канала обычно начинается с `-100`.

Никогда не коммитьте токены. Скопируйте `.env.example` в `.env` и заполните:

- `TELEGRAM_BOT_USERNAME`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_CHANNEL_ID`
- `TELEGRAM_CHANNEL_USERNAME`
- `TELEGRAM_CHANNEL_URL`
- `PUBLICATION_PRICE_STARS` — целое число Stars, минимум `1`
- при необходимости `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

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

Тестовая оплата: создайте объявление, нажмите «Оплатить» и подтвердите оплату в Telegram Stars. Реальные запросы Telegram в автоматических тестах не выполняются.

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
- admin endpoint защищён API key; в production-профиле пустой ключ запрещён.

## Деплой на VPS

### Docker Hub и локальная публикация

1. Создайте в Docker Hub репозиторий `username/your-market`.
2. Создайте Docker Hub access token (использовать пароль аккаунта не рекомендуется).
3. Передайте token только в текущую PowerShell-сессию — не сохраняйте его в Git:

```powershell
$env:DOCKERHUB_TOKEN = "..."
docker login --username username
```

Сборка, тесты, создание image и push:

```powershell
.\scripts\build-and-push.ps1 `
  -DockerHubUsername username `
  -Version 1.0.0
```

Без `-Version` скрипт создаёт тег из UTC/local timestamp и короткого Git commit. `-SkipTests`
разрешён только для осознанной повторной технической сборки; обычный release выполняет `clean verify`.

### Подготовка VPS

Установите Docker Engine и Docker Compose plugin, затем создайте `/opt/yourmarket`. Скрипт
`deploy/install-vps.sh` проверяет Ubuntu/Debian и выводит официальные инструкции, если Docker ещё
не установлен; firewall он не изменяет.

Скопируйте в `/opt/yourmarket`:

- `deploy/docker-compose.prod.yml`;
- `deploy/deploy.sh`;
- `deploy/.env.prod.example` как `.env.prod`.

Заполните `.env.prod` непосредственно на VPS и выполните:

```bash
chmod +x deploy.sh
./deploy.sh docker.io/username/your-market:1.0.0
```

PostgreSQL использует именованный volume и не публикует порт наружу. HTTP-порт приложения доступен
только как `127.0.0.1:8080`; наружу открыт лишь long polling Telegram. Production-профиль требует
непустой `ADMIN_API_KEY`.

Следующий release можно выполнить из Windows одной командой (SSH использует ключ, пароль параметром
не принимается):

```powershell
.\scripts\release.ps1 `
  -DockerHubUsername username `
  -VpsHost server-ip `
  -VpsUser deploy `
  -Version 1.0.1
```

Состояние и логи на VPS:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f app
```

При неуспешном healthcheck `deploy.sh` показывает последние 100 строк логов и пытается вернуть
предыдущий image. Для ручного rollback запустите тот же скрипт с предыдущим неизменяемым тегом:

```bash
./deploy.sh docker.io/username/your-market:0.9.9
```

Перед критичными Liquibase-миграциями сделайте backup PostgreSQL. Deploy и rollback никогда не
удаляют volume. На VPS должен работать только один экземпляр бота с данным token; при long polling
webhook должен быть отключён. Публичный HTTP-домен приложению не требуется.
