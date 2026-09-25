# Задача: реализовать main-сервис «Pact»

## Контекст проекта

Учебный проект — сервис взаимной подотчётности для достижения целей. Пользователь создаёт цель (название, категория, дедлайн), может разбить её на этапы с промежуточными дедлайнами. Цель публикуется как пакт; другие пользователи со своей активной целью вступают в пакт (пакт групповой) или приглашаются в него. Каждый участник вносит залог. Перед каждым дедлайном участник отчитывается и прикладывает доказательства (файл, фото, ссылка); напарники подтверждают или отклоняют отчёт с комментарием. Если дедлайн просрочен — залог списывается в благотворительный фонд, выбранный в пакте. Если цель выполнена вовремя — залог возвращается, начисляются баллы (coins), которые тратятся в магазине на кастомизацию профиля.

### Архитектура

- **main-service** (этот репозиторий) — цели, пакты, отчёты, магазин, поддержка.
- **auth-service** (отдельно, ещё не готов) — регистрация, логин, выдача JWT. Пароли в main НЕ хранятся.
- **payment-service** (отдельно, ещё не готов) — заморозка, списание и возврат залогов. Таблицы Deposit/Transaction/PaymentAuditLog живут там, в main их нет.
- **email-service** (отдельно, ещё не готов) — рассылка писем.

### Аутентификация

В сервисе уже есть Security-фильтр, который парсит Bearer JWT. **Не переписывай его**, изучи и используй. Клеймы токена:

```json
{ "iss": "pact-auth", "sub": "<UUID пользователя>", "aud": "pact-main",
  "exp": 0, "iat": 0, "roles": ["USER"] }
```

`sub` == `users.id` в main. Роль `ADMIN` открывает `/api/v1/admin/**`. Сделай единый способ получить id текущего пользователя (например, компонент `CurrentUser` или argument resolver) и используй его везде.

---

## Шаг 0. Сначала изучи проект, код не пиши

1. Изучи структуру пакетов, build-файл (Maven/Gradle), версии Java и Spring Boot, какие зависимости уже есть (Lombok, MapStruct, validation, Testcontainers и т.д.).
2. Изучи Security-фильтр и конфигурацию: как он кладёт пользователя в `SecurityContext`, какие пути открыты.
3. Изучи Liquibase: мастер-файл `resources/db/db.changelog-master.xml` и changeset'ы в `resources/db/changeset` (001–014).
4. Изучи существующие соглашения в коде (именование, слои, обработка ошибок) и следуй им.
5. **Покажи мне план**: какие пакеты/классы создашь, что поменяешь в существующем коде, и список расхождений между этим документом и кодом. Дождись моего подтверждения.

Если в процессе что-то в этом документе противоречит коду или неоднозначно — остановись и спроси, не придумывай молча.

---

## Изменения схемы БД

**Не редактируй существующие changeset'ы 001–014.** Все изменения — новыми файлами `015-...xml` и далее, с подключением в мастер-файл.

1. `users`:
   - удалить колонки `password_hash`, `failed_login_attempts`, `locked_until`;
   - убрать `defaultValueComputed` у `id` (id приходит из auth-service);
   - `email` остаётся копией (нужен для рассылок).
2. `pact_participant` — у каждого участника своя цель и свой залог:
   - `goal_id UUID NOT NULL` → FK на `goal(id)`, `ON DELETE RESTRICT`, индекс, уникальность по `goal_id` (одна цель — один пакт);
   - `deposit_amount DECIMAL(12,2) NOT NULL`;
   - `deposit_currency VARCHAR(3) NOT NULL DEFAULT 'RUB'`;
   - `deposit_status VARCHAR(50) NOT NULL`.
   `pact.goal_id` остаётся — это цель создателя пакта.
3. Новая таблица `pact_invitation`: `id`, `pact_id` (FK CASCADE), `inviter_id` (FK users), `invitee_id` (FK users), `status VARCHAR(50)`, `created_at`, `responded_at NULL`. Частичный уникальный индекс: одно `PENDING`-приглашение на пару `(pact_id, invitee_id)` (через `<sql>` с `<rollback>`).
4. Новая таблица `coin_transaction`: `id`, `user_id` (FK CASCADE), `amount INT` (± знак), `reason VARCHAR(50)` (`GOAL_COMPLETED`, `PURCHASE`), `reference_id UUID NULL`, `created_at`.

В `application.yml` поставь `spring.jpa.hibernate.ddl-auto=validate`, чтобы сущности сверялись со схемой.

---

## Статусы (enum, хранить как `@Enumerated(EnumType.STRING)`)

| Сущность | Значения |
|---|---|
| Goal | `DRAFT`, `IN_PACT`, `COMPLETED`, `FAILED` |
| Milestone | `PENDING`, `COMPLETED`, `FAILED` |
| Pact | `OPEN` (набор участников), `ACTIVE`, `FINISHED`, `CANCELLED` |
| PactParticipant | `PENDING_DEPOSIT`, `ACTIVE`, `COMPLETED`, `FAILED`, `LEFT` |
| DepositStatus | `NOT_STARTED`, `PENDING`, `HELD`, `CHARGED`, `RELEASED`, `FAILED` |
| CheckIn | `PENDING`, `APPROVED`, `REJECTED` |
| Review | `APPROVED`, `REJECTED` |
| Proof type | `FILE`, `PHOTO`, `LINK` |
| Invitation | `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED` |
| SupportTicket | `OPEN`, `IN_PROGRESS`, `CLOSED` |
| ShopItem type | `AVATAR`, `FRAME`, `BACKGROUND` |

---

## Бизнес-правила

### Цели и этапы
- Цель создаётся в `DRAFT`. Редактировать/удалять цель и её этапы можно только в `DRAFT`.
- Дедлайн цели в будущем. Дедлайн этапа — в будущем и не позже дедлайна цели.
- Лента `/goals/feed` показывает цели в статусе `IN_PACT`, чей пакт в статусе `OPEN`, кроме своих.

### Пакт
- `POST /pacts` — создатель передаёт `goalId` (своя цель в `DRAFT`), `charityId` (активный фонд), `depositAmount`. Создаётся пакт `OPEN` и участник-создатель `PENDING_DEPOSIT`; цель → `IN_PACT`.
- Вступить (`join`) или принять приглашение можно, только если пакт `OPEN`, пользователь ещё не участник, и он передаёт свою цель в `DRAFT` и `depositAmount`. Участник → `PENDING_DEPOSIT`, цель → `IN_PACT`.
- Залог: `POST /pacts/{id}/deposit` вызывает `PaymentClient.holdDeposit(...)`, `deposit_status` → `PENDING`, клиенту возвращается `paymentUrl`. Callback от payment-service с `HELD` → участник `ACTIVE`.
- `POST /pacts/{id}/start` — только создатель, только если ≥ 2 участников `ACTIVE`. Пакт → `ACTIVE`; участники, не внёсшие залог, → `LEFT`, их цели → `DRAFT`, pending-приглашения → `CANCELLED`.
- `leave` — пока пакт `OPEN`: участник → `LEFT`, цель → `DRAFT`, если залог `HELD` — `PaymentClient.release(...)`. Создатель выйти не может, он может отменить пакт (`DELETE /pacts/{id}` → `CANCELLED`, всем залогам release). После `ACTIVE` выход запрещён (409).

### Отчёты и проверка
- Отчёт создаёт только `ACTIVE`-участник `ACTIVE`-пакта. `milestoneId` — этап **его** цели в `PENDING`, либо `null` = финальный отчёт по цели. Нельзя создать отчёт, если по этому этапу уже есть `PENDING` или `APPROVED`.
- Доказательства добавляет/удаляет только автор, пока отчёт `PENDING`. У `LINK` обязателен `externalUrl`, у `FILE`/`PHOTO` — загруженный файл. Файлы — через интерфейс `FileStorage` с локальной реализацией (путь в конфиге), лимит размера в конфиге.
- Проверяет любой другой `ACTIVE`-участник того же пакта, один раз на отчёт. Себя проверять нельзя.
- **Правило решения (вынести в одно место, чтобы легко поменять):** первое `APPROVED` → отчёт `APPROVED`, этап → `COMPLETED` (для финального отчёта — цель завершена, см. ниже). `REJECTED` с обязательным комментарием → отчёт `REJECTED`, автор может подать новый отчёт до дедлайна.

### Дедлайны (`@Scheduled`, интервал в конфиге)
- Напоминание: за N часов (конфиг) до дедлайна этапа/цели без `APPROVED`-отчёта — `EmailClient.sendDeadlineReminder(...)`, один раз (придумай, как не слать повторно).
- Просрочка: этап или цель с прошедшим дедлайном без `APPROVED`-отчёта → этап/цель `FAILED`, участник `FAILED`, `PaymentClient.charge(participantId, charityId)`, письмо с итогом.
- Финальный отчёт `APPROVED` до дедлайна цели (все этапы `COMPLETED`) → цель `COMPLETED`, участник `COMPLETED`, `PaymentClient.release(...)`, начислить `app.coins.goal-completed` (конфиг) + запись в `coin_transaction`.
- Когда все участники в финальных статусах → пакт `FINISHED`.
- Обработка идемпотентна: повторный запуск не должен повторно списывать/начислять.

### Магазин
- Покупка в одной транзакции: атомарное списание `UPDATE users SET coins = coins - :price WHERE id = :id AND coins >= :price` (0 строк → 409 «недостаточно баллов»), запись в `purchase` (цена на момент покупки) и `coin_transaction`. Неактивный товар купить нельзя.
- `PUT /users/me/avatar` — только купленный пользователем предмет типа `AVATAR`.

---

## Внешние сервисы (пока заглушки)

Создай интерфейсы и реализации-заглушки, которые логируют вызов (и для payment — сразу возвращают фейковый `paymentUrl`). Реальные HTTP-клиенты будут позже, поэтому бизнес-логика должна зависеть только от интерфейсов.

- `PaymentClient`: `holdDeposit(participantId, userId, amount, currency) → paymentUrl`, `charge(participantId, charityId)`, `release(participantId)`.
- `EmailClient`: `sendPactInvitation(...)`, `sendDeadlineReminder(...)`, `sendPactResult(...)`.

### Внутренние эндпоинты `/internal/**`
- Закрыты от пользовательского JWT; доступ по заголовку `X-Internal-Token`, значение из конфига. Аккуратно встрой в существующую security-конфигурацию, не ломая текущий фильтр.
- `POST /internal/users` — auth-service создаёт профиль после регистрации: `{id, email, username}`. Идемпотентно.
- `POST /internal/payments/events` — `{participantId, event: HELD|CHARGED|RELEASED|FAILED}` → обновить `deposit_status` (и статус участника для `HELD`). Идемпотентно.

---

## Эндпоинты (префикс `/api/v1`)

**Профиль**
- `GET /users/me`, `PATCH /users/me` (username, firstName, lastName)
- `PUT /users/me/avatar` `{shopItemId}`
- `GET /users/{id}` — публичный профиль (без email и coins)
- `GET /users?search=` — поиск по username для приглашений, пагинация
- `GET /users/me/purchases`, `GET /users/me/coin-transactions`

**Справочники**
- `GET /categories`, `GET /charities` (только активные)

**Цели и этапы**
- `POST /goals`, `GET /goals/my?status=`, `GET /goals/{id}`, `PATCH /goals/{id}`, `DELETE /goals/{id}`
- `GET /goals/feed?categoryId=&page=&size=`
- `POST /goals/{id}/milestones`, `PATCH /milestones/{id}`, `DELETE /milestones/{id}`

**Пакты**
- `POST /pacts`, `GET /pacts/my`, `GET /pacts/{id}` (с участниками, их целями и статусами)
- `POST /pacts/{id}/join` `{goalId, depositAmount}`, `POST /pacts/{id}/leave`, `POST /pacts/{id}/start`, `DELETE /pacts/{id}`
- `POST /pacts/{id}/deposit`, `GET /pacts/{id}/deposit`
- `POST /pacts/{id}/invitations` `{inviteeId}`, `GET /invitations/my`, `POST /invitations/{id}/accept` `{goalId, depositAmount}`, `POST /invitations/{id}/decline`

**Чат**
- `GET /pacts/{id}/messages?before=&limit=` (курсорная пагинация по `created_at`), `POST /pacts/{id}/messages`. Доступ — только участникам пакта (кроме `LEFT`).

**Отчёты**
- `POST /pacts/{id}/check-ins`, `GET /pacts/{id}/check-ins`, `GET /check-ins/{id}`
- `POST /check-ins/{id}/proofs` (multipart для файла или JSON для ссылки), `DELETE /proofs/{id}`
- `GET /check-ins/to-review`, `POST /check-ins/{id}/reviews` `{status, comment}`

**Магазин**
- `GET /shop/items?type=`, `POST /shop/items/{id}/purchase`

**Поддержка**
- `POST /support/tickets`, `GET /support/tickets/my`, `GET /support/tickets/{id}` (только свой)

**Админка (`ROLE_ADMIN`)**
- CRUD `/admin/categories`, `/admin/charities`, `/admin/shop-items` (удаление = `is_active=false`, если есть ссылки)
- `GET /admin/support-tickets?status=`, `PATCH /admin/support-tickets/{id}` `{status}`

---

## Технические требования

- Слои: controller → service → repository. Контроллеры без бизнес-логики.
- Entity — JPA, связи `LAZY`, без `@Data` на сущностях (equals/hashCode по id). Избегай N+1 в списках (`@EntityGraph` или fetch join).
- DTO запросов/ответов — отдельные классы (records), сущности наружу не отдавать. Маппинг — MapStruct, если он есть в проекте, иначе ручные мапперы.
- Валидация — `jakarta.validation` на DTO.
- Ошибки — глобальный `@RestControllerAdvice` с `ProblemDetail`: 400 валидация, 403 нет прав (чужой ресурс), 404 не найдено, 409 нарушение бизнес-правила/статуса. Свои исключения: `NotFoundException`, `ForbiddenException`, `BusinessRuleException`.
- Проверки прав доступа (владелец цели, участник пакта и т.д.) — в сервисном слое.
- Списки — пагинация через `Pageable`.
- `@Transactional` на сервисных методах, изменяющих данные; вызовы внешних клиентов — после успешного изменения состояния (или через `@TransactionalEventListener(phase = AFTER_COMMIT)`).
- Все настраиваемые значения (coins за цель, интервалы шедулера, лимит файлов, internal token, путь хранилища) — в `application.yml` через `@ConfigurationProperties`.

## Тесты

- Unit-тесты сервисов на ключевые правила: переходы статусов пакта, правило ревью, запрет самопроверки, покупка при нехватке баллов, обработка просрочки и завершения цели (включая идемпотентность).
- Интеграционные тесты с Testcontainers PostgreSQL (добавь зависимость, если её нет): Liquibase накатывается, `ddl-auto=validate` проходит, основной сценарий «создал цель → пакт → второй вступил → залоги HELD → start → отчёт → ревью → завершение → coins начислены» работает end-to-end.

## Порядок работы

Работай по фазам. После каждой фазы проект должен собираться и тесты проходить; коротко отчитайся, что сделано.

1. Миграции 015+ и все JPA-сущности, enum'ы, репозитории. Проверка: интеграционный тест контекста с `validate`.
2. Общая инфраструктура: `CurrentUser`, исключения и `@RestControllerAdvice`, `@ConfigurationProperties`, интерфейсы и заглушки `PaymentClient`/`EmailClient`/`FileStorage`, `/internal/**` с проверкой токена.
3. Профиль, справочники, админка справочников.
4. Цели и этапы.
5. Пакты, участники, приглашения, залоги, чат.
6. Отчёты, доказательства, ревью.
7. Шедулер дедлайнов, завершение целей, начисление coins.
8. Магазин и поддержка.
9. End-to-end интеграционный тест и финальный прогон.

В конце дай список: что реализовано, какие решения ты принял сам, что осталось заглушкой.
