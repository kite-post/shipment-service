# shipment-service

Накладні, місця, стейт-машина відправлення, керування доставкою, повернення і
саги (5.1). Єдиний власник статусу відправлення: решта сервісів реагує на його
події в `shipment.events` (5, вступ; 8.1). Створено з `template-java-service`.

## Що вже вміє

`POST /api/v1/shipments` — **лише чернетка** (`"draft": true`, статус `DRAFT`):

- накладна з місцями (`shipment`, `parcel`, міграція `V2`) і подія
  `kite.shipment.created.v1` пишуться **в одній транзакції** через outbox
  (5.4, п. 2); `OutboxPublisher` публікує її в `shipment.events` з ключем
  `shipmentId`;
- номер — UPU S10 з послідовності бази (`KT` + 8 цифр + контрольна + `UA`),
  штрих-коди місць — `<номер>-01…-20` (10.1);
- у подію замість телефонів ідуть їхні SHA-256 (`senderPhoneHash`,
  `recipientPhoneHash`); `X-Request-Id` запиту стає `kiterequestid`;
- JWT сервіс перевіряє сам (ті самі issuer і ключі Keycloak, що й шлюз);
  `sub` зберігається як `created_by`.

Чого ще немає — свідомо, окремими задачами:

- `"draft": false` (одразу T1) → **422** `SHIPMENT_CONFIRMATION_REQUIRES_PRICE`:
  умова T1 — «є ціна» (7.2), а її рахує pricing-service;
- `Idempotency-Key` обов'язковий за контрактом, але повтор поки не
  розпізнається;
- `senderCustomerId` у події порожній: `sub` — це користувач Keycloak, а не
  `customer.id`; відправника треба передавати в тілі;
- `origin` / `destination` у відповіді — лише `id` (назви знає network-service);
- решта ендпоїнтів `ShipmentsApi` відповідають 501.

Помилки — `application/problem+json` з `code` і `requestId` (9.1): 400
`VALIDATION_FAILED` / `MALFORMED_REQUEST`, 401 без токена, 409
`SHIPMENT_CONFLICT`, 422 — правила домену.

Критерій «подія є після HTTP-запиту і її немає після відкату» перевіряє
`ShipmentCreatedIT` на справжніх Postgres і Kafka: відкат провокується
дублем номера, який спрацьовує вже після `outbox.append`.

## Що всередині

```
pom.xml                   Spring Boot 3.5, Java 21, JPA, Flyway, Kafka, Spotless, Failsafe
src/main/java
  web/                    ShipmentsController, problem+json, X-Request-Id, JWT, /api/v1/info
  shipment/               накладна, місця, номери S10, створення чернетки
  common/UuidV7           первинні ключі й id подій (6.1)
  events/                 конверт CloudEvents, реєстр подій, DLT, прибирання
  events/outbox/          запис в outbox, публікатор, метрики відставання
  events/inbox/           ідемпотентна обробка вхідних подій (processed_event)
src/main/resources
  application.yml         база, Kafka, actuator, Prometheus, kite.events.*
  db/migration/V1__…      таблиці outbox і processed_event
  db/migration/V2__…      shipment, parcel, tracking_number_seq
src/test/java             юніт-тести + *IT на Postgres і Kafka у Testcontainers
Dockerfile                шарований образ Spring Boot на temurin-21-jre-alpine
Makefile                  той самий набір цілей, що й у решти репозиторіїв
.github/workflows/ci.yml  п'ять рядків: виклик спільного workflow із .github
```

## Команди

```bash
make build      # зібрати jar
make lint       # формат і імпорти (Spotless + palantir-java-format)
make fmt        # відформатувати
make test       # юніт-тести, без Docker
make test-it    # разом з інтеграційними (*IT): Postgres і Kafka контейнерами
make verify     # усе, що робить CI
make image      # зібрати образ локально
make run        # запустити локально з Postgres і Kafka у контейнерах
make run-stand  # запустити локально проти стенда kite-infra (localhost:5432, :9092)
make up         # підняти образ на 8080 з лімітом 384 МБ у мережі стенда
```

`make run` — це `spring-boot:test-run` з `ShipmentDevApplication`: той
самий застосунок плюс `TestcontainersConfiguration`. З IDE — запустити `main`
цього класу.

## CI

Увесь пайплайн — виклик спільного workflow з
[kite-post/.github](https://github.com/kite-post/.github): лінт, тести,
збірка образу, публікація в `ghcr.io/kite-post/<сервіс>` і PR у `kite-infra`,
який піднімає версію у `versions.env` (12.6).

На pull request образ збирається, але не публікується. Теги: `sha-<хеш>` для
`main`, `v<semver>` для релізів; рухомого `latest` немає принципово.

## База

Своя база на сервіс (5.4, п. 1), міграції Flyway сервіс накочує сам на старті
(6.1, 12.3). Домовленості 6.1: ключі — UUID v7 із застосунку (`UuidV7.next()`),
гроші — `bigint` у копійках, час — `timestamptz` в UTC, переліки — `text` з
`CHECK`, у сутностей із конкурентними змінами — колонка `version`.

Hibernate схему не створює (`ddl-auto: validate`), лише звіряє з міграціями.
Локальні значення в `application.yml` дивляться на базу стенда; у контейнері
стенд перекриває їх через `SPRING_DATASOURCE_*`.

Readiness (`/actuator/health/readiness`) залежить від бази — на неї дивляться
`HEALTHCHECK` образу і `depends_on` стенда. Kafka в readiness свідомо не входить:
без брокера сервіс працює далі, а події чекають в outbox.

## Контракти

Моделі API, інтерфейси контролерів і класи подій CloudEvents приходять
залежністю `ua.kite:kite-contracts`, закріпленою за тегом — своїх копій
`openapi.yaml` і JSON Schema сервіс не тримає (5.4, п. 6):

```xml
<kite-contracts.version>0.1.0</kite-contracts.version>
```

```bash
make contracts            # підтягнути пакет за поточним тегом
make contracts V=0.3.0    # перейти на нову версію: Makefile + pom.xml
make contracts-check      # версія закріплена, копій схем немає, залежність резолвиться
```

Нова версія приходить сама: після релізу `kite-contracts` розсилає подію
всім репозиторіям із топіком `kite-contracts`, і workflow `contracts-bump`
відкриває PR з `make contracts V=<версія>`. Версія одна на всі репозиторії.
Руками те саме: `make contracts V=0.3.0`. Якщо тегу немає, `Makefile` і
маніфест лишаються як були.

`contracts-check` проганяє спільний CI. Самі схеми лежать у тому самому jar
(`/kite/contracts/openapi`, `/kite/contracts/events`), тож валідація події не
потребує копії файла в репозиторії — так це і перевіряє `ContractsWiringTest`.

Пакет живе в GitHub Packages, а він віддає Maven лише з токеном. У CI
`settings.xml` розставляє спільний workflow; локально потрібен свій:

```xml
<!-- ~/.m2/settings.xml -->
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>ВАШ-ЛОГІН</username>
      <password>ghp_… (scope read:packages)</password>
    </server>
  </servers>
</settings>
```

Порядок наскрізної зміни (5.6): спершу адитивна зміна в `kite-contracts` з
новим тегом, потім версія тут, і тільки потім продюсер починає слати нове
поле.

## Події

### Публікація: outbox (5.4, п. 2)

Подія пишеться в таблицю `outbox` **у тій самій транзакції**, що й зміна, тож
зміни без події чи події без зміни не буває. Поза транзакцією `append` падає.

```java
@Transactional
public void sort(UUID shipmentId) {
    Shipment shipment = shipments.findById(shipmentId).orElseThrow();
    shipment.markSorted();
    outbox.append(OutgoingEvent.of(KITE_SHIPMENT_STATUS_CHANGED_V1, data)
            .subject(shipment.getTrackingNumber())   // номер, код мішка, рейс
            .occurredAt(scan.occurredAt()));          // без нього — час запису
}
```

Конверт CloudEvents (9.7) складається сам: `id` (UUID v7), `source`
(`/kite/<сервіс>`), `time`, `kitereceivedat`, `kiterequestid` (з MDC
`requestId`), `kitepartitionkey`. Топік і поле ключа розділу беруться з
`events/registry.json` у jar `kite-contracts`: для `status-changed` це
`shipment.events` і `shipmentId`. Якщо поля ключа в `data` немає, `append` падає.

`OutboxPublisher` кожні 200 мс забирає неопубліковане в порядку `seq` і шле в
Kafka (`acks=all`, ідемпотентний продюсер):

- **Один активний публікатор на сервіс** — під `pg_try_advisory_xact_lock`.
  Репліки, які не взяли лок, чекають; так події однієї сутності не обганяють
  одна одну.
- **Збій зупиняє пачку.** Подія, яку Kafka не прийняла, лишається в черзі з
  `attempts` і `last_error`, а наступні не йдуть повз неї. Порядок важливіший.
- **Щонайменше раз.** Подія може піти вдруге (Kafka прийняла, позначка в базі
  не встигла), тому кожен споживач ідемпотентний.
- Опубліковане видаляється через `kite.events.outbox.retention` (7 діб):
  outbox — транспорт, а не архів (8.1).

Метрики: `kite_outbox_pending`, `kite_outbox_oldest_age_seconds` (алерт —
на вік), `kite_outbox_published_total`, `kite_outbox_publish_failures_total`.

### Споживання: processed_event (5.4, п. 3)

Усе тіло обробки — всередині `Inbox.handleOnce`. Позначка в `processed_event`
і зміни обробника комітяться разом: повтор пропускається, а якщо обробник
упав, відкочується і позначка.

```java
@KafkaListener(id = CONSUMER, idIsGroup = false, topics = "shipment.events")
void on(ConsumerRecord<String, String> record) {
    inbox.handleOnce(CONSUMER, record, event -> {
        if (!KITE_SHIPMENT_STATUS_CHANGED_V1.getValue().equals(event.type())) return;
        var data = event.dataAs(ShipmentStatusChangedData.class, mapper);
        // …
    });
}
```

- `CONSUMER` — стабільне ім'я обробника, ключ у `processed_event` разом з id
  події. Змінив ім'я — всі події оброблятимуться наново.
- Група Kafka — ім'я сервіса (`spring.kafka.consumer.group-id`).
- Виняток з обробника — три повтори (0,5 → 1 → 2 с), потім `dlt.<сервіс>`.
  Битий запис (не JSON, без `id` чи `type`) — у DLT одразу. Звідки прийшов
  запис і чому впав, лежить у заголовках `kafka_dlt-*`. Сервіс сам оголошує
  свій DLT-топік: автостворення на стенді вимкнене.
- `processed_event` чиститься через `kite.events.inbox.retention` (14 діб) —
  довше за ретеншен топіків, щоб старий повтор не пройшов як новий.

Робочий приклад споживача — `example/ShipmentEventsListener` у
`template-java-service`; у цьому сервісі споживачів поки немає.

### Тести

`OutboxIT`, `OutboxPublisherFailureIT` і `InboxIT` перевіряють на справжніх
Postgres і Kafka: подія доходить у конверті CloudEvents, відкат транзакції не
лишає події, порядок у межах ключа зберігається, збій Kafka не дає наступним
обігнати, другий публікатор чекає на лок, повтор обробляється один раз, битий
запис і обробник, що падає, потрапляють у DLT. Новий IT сервіса —
`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
