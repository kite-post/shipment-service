-- Накладна і її місця (6.2). Домовленості 6.1: ключі — UUID v7 із застосунку,
-- гроші — bigint у копійках (валюта завжди UAH), вага — грами, розміри —
-- міліметри, переліки — text з CHECK, у відправлення — колонка version.
--
-- Поки що тут лише те, що потрібно для створення чернетки. Послуги, маршрут,
-- дії з доставкою і коди видачі (shipment_service, shipment_route,
-- delivery_action, authorized_recipient, pickup_code) прийдуть своїми міграціями.

-- Порядкові номери для UPU S10: KT + 8 цифр + контрольна + UA (10.1).
-- Номер видає база, тож дві репліки не роздадуть однаковий.
CREATE SEQUENCE tracking_number_seq START WITH 1 MINVALUE 1 MAXVALUE 99999999 NO CYCLE;

CREATE TABLE shipment
(
    id                      uuid PRIMARY KEY,
    tracking_number         text        NOT NULL,
    type                    text        NOT NULL
        CHECK (type IN ('DOCUMENTS', 'PARCEL', 'CARGO')),
    status                  text        NOT NULL
        CHECK (status IN ('DRAFT', 'CREATED', 'CANCELLED', 'ACCEPTED', 'IN_TRANSIT', 'AT_HUB', 'SORTED',
                          'ON_HOLD', 'READY_FOR_PICKUP', 'DELIVERED', 'RETURNED', 'LOST')),

    -- Відправник і отримувач — знімок на момент створення: профіль клієнта
    -- може змінитися, а накладна — ні.
    sender_first_name       text        NOT NULL,
    sender_last_name        text        NOT NULL,
    sender_middle_name      text,
    sender_phone            text        NOT NULL,       -- E.164
    sender_email            text,
    recipient_first_name    text        NOT NULL,
    recipient_last_name     text        NOT NULL,
    recipient_middle_name   text,
    recipient_phone         text        NOT NULL,       -- E.164
    recipient_email         text,

    -- Точки network-service: між базами зовнішніх ключів немає (6, вступ).
    origin_location_id      uuid        NOT NULL,
    destination_location_id uuid        NOT NULL,

    payer                   text        NOT NULL
        CHECK (payer IN ('SENDER', 'RECIPIENT', 'BUSINESS')),
    declared_value          bigint CHECK (declared_value >= 0),
    cod_amount              bigint CHECK (cod_amount >= 0),
    price_total             bigint CHECK (price_total >= 0), -- рахує pricing-service, до T1 його немає
    comment                 text CHECK (length(comment) <= 500),

    -- Хто створив: sub з JWT (користувач Keycloak). Це не customer.id —
    -- той знає customer-service.
    created_by              text        NOT NULL,

    version                 bigint      NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL
);

-- 6.3: номер відправлення унікальний.
CREATE UNIQUE INDEX shipment_tracking_number_uq ON shipment (tracking_number);
CREATE INDEX shipment_created_by_idx ON shipment (created_by, created_at DESC);

CREATE TABLE parcel
(
    id                    uuid PRIMARY KEY,
    shipment_id           uuid    NOT NULL REFERENCES shipment (id),
    seq                   integer NOT NULL CHECK (seq BETWEEN 1 AND 20),
    barcode               text    NOT NULL,             -- KT…UA-01 (10.1)
    declared_weight_grams bigint  NOT NULL CHECK (declared_weight_grams > 0),
    declared_length_mm    integer NOT NULL CHECK (declared_length_mm > 0),
    declared_width_mm     integer NOT NULL CHECK (declared_width_mm > 0),
    declared_height_mm    integer NOT NULL CHECK (declared_height_mm > 0),
    -- Фактичні — після зважування на прийомі.
    actual_weight_grams   bigint CHECK (actual_weight_grams > 0),
    actual_length_mm      integer CHECK (actual_length_mm > 0),
    actual_width_mm       integer CHECK (actual_width_mm > 0),
    actual_height_mm      integer CHECK (actual_height_mm > 0),
    UNIQUE (shipment_id, seq)
);

-- 6.3: штрих-код місця унікальний.
CREATE UNIQUE INDEX parcel_barcode_uq ON parcel (barcode);
