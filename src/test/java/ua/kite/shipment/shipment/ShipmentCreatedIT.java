package ua.kite.shipment.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ua.kite.shipment.KafkaProbe;
import ua.kite.shipment.TestJwt;
import ua.kite.shipment.TestcontainersConfiguration;
import ua.kite.shipment.events.outbox.Outbox;
import ua.kite.shipment.events.outbox.OutgoingEvent;

/**
 * Критерій готовності задачі: після HTTP-запиту подія з'являється в топіку, а якщо транзакція
 * відкотилась — не з'являється. Справжній HTTP, справжні Postgres і Kafka, без моків бізнес-логіки.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestJwt.class})
class ShipmentCreatedIT {

    private static final String TOPIC = "shipment.events";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ObjectMapper mapper;

    /** Справжній Outbox, лише з підрахунком викликів — щоб довести, що подію таки записали. */
    @MockitoSpyBean
    Outbox outbox;

    @Test
    void chernetkaZHttpDokhodytDoTopikuYakKiteShipmentCreated() throws Exception {
        String requestId = "req-" + UUID.randomUUID();

        ResponseEntity<String> response = post(draftRequest(), TestJwt.bearer("user-1"), requestId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = mapper.readTree(response.getBody());
        String shipmentId = body.get("id").asText();
        String trackingNumber = body.get("trackingNumber").asText();
        assertThat(body.get("status").asText()).isEqualTo("DRAFT");
        assertThat(trackingNumber).matches("^KT\\d{9}UA$");
        assertThat(body.at("/parcels/0/barcode").asText()).isEqualTo(trackingNumber + "-01");
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/shipments/" + shipmentId);

        List<ConsumerRecord<String, String>> records = KafkaProbe.await(TOPIC, shipmentId, 1, WAIT);
        assertThat(records).hasSize(1);
        JsonNode event = mapper.readTree(records.getFirst().value());
        assertThat(event.get("type").asText()).isEqualTo("kite.shipment.created.v1");
        assertThat(event.get("source").asText()).isEqualTo("/kite/shipment-service");
        assertThat(event.get("subject").asText()).isEqualTo(trackingNumber);
        // X-Request-Id запиту наскрізь доходить до події (9.1).
        assertThat(event.get("kiterequestid").asText()).isEqualTo(requestId);
        assertThat(event.at("/data/shipmentId").asText()).isEqualTo(shipmentId);
        assertThat(event.at("/data/status").asText()).isEqualTo("DRAFT");
        assertThat(event.at("/data/draft").asBoolean()).isTrue();
        assertThat(event.at("/data/parcelCount").asInt()).isEqualTo(1);
        assertThat(event.at("/data/recipientPhoneHash").asText()).isEqualTo(PhoneHash.of("+380671112233"));
        // Події не носять самих номерів.
        assertThat(records.getFirst().value()).doesNotContain("+380671112233").doesNotContain("+380501234567");
    }

    @Test
    void vidkatTranzaktsiiNeLyshaiePodiiVTopiku() throws Exception {
        ResponseEntity<String> first = post(draftRequest(), TestJwt.bearer("user-2"), null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String takenNumber =
                mapper.readTree(first.getBody()).get("trackingNumber").asText();
        long takenSerial = Long.parseLong(takenNumber.substring(2, 10));

        // Наступний запит отримає вже зайнятий номер: обмеження унікальності спрацює на flush —
        // уже після того, як подію поклали в outbox. Транзакція відкочується разом із подією.
        jdbc.sql("SELECT setval('tracking_number_seq', :serial, false)")
                .param("serial", takenSerial)
                .query()
                .singleValue();
        ResponseEntity<String> second;
        try {
            second = post(draftRequest(), TestJwt.bearer("user-2"), null);
        } finally {
            restoreSequence();
        }

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(mapper.readTree(second.getBody()).get("code").asText()).isEqualTo("SHIPMENT_CONFLICT");

        // Подію справді записали в outbox обидва запити — а лишилась тільки перша.
        verify(outbox, times(2)).append(any(OutgoingEvent.class));
        assertThat(outboxRowsFor(takenNumber)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM shipment WHERE tracking_number = :n", takenNumber))
                .isEqualTo(1);

        // Публікатор розібрав чергу — і в топіку з цим номером лише подія першої накладної.
        await().atMost(WAIT).until(() -> count("SELECT count(*) FROM outbox WHERE published_at IS NULL", null) == 0);
        assertThat(eventsWithSubject(takenNumber, Duration.ofSeconds(3))).hasSize(1);
    }

    @Test
    void odrazuPidtverdzhenaNakladnaPokyVidkhyliaietsiaBezPodii() throws Exception {
        long outboxBefore = count("SELECT count(*) FROM outbox", null);
        String request = draftRequest().replace("\"draft\": true", "\"draft\": false");

        ResponseEntity<String> response = post(request, TestJwt.bearer("user-3"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mapper.readTree(response.getBody()).get("code").asText())
                .isEqualTo(ShipmentDrafts.CONFIRMATION_REQUIRES_PRICE);
        assertThat(count("SELECT count(*) FROM outbox", null)).isEqualTo(outboxBefore);
    }

    @Test
    void bezTokenaNeProkhodyt() {
        ResponseEntity<String> response = post(draftRequest(), null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void zapytBezMistsVidkhyliaietsiaValidatsiieiu() throws Exception {
        String request = draftRequest().replaceAll("(?s)\"parcels\": \\[.*?]", "\"parcels\": []");

        ResponseEntity<String> response = post(request, TestJwt.bearer("user-4"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(response.getBody()).get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void bezIdempotencyKeyVidkhyliaietsia() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, TestJwt.bearer("user-5"));

        ResponseEntity<String> response =
                rest.postForEntity("/api/v1/shipments", new HttpEntity<>(draftRequest(), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(response.getBody()).get("code").asText()).isEqualTo("MALFORMED_REQUEST");
    }

    private ResponseEntity<String> post(String json, String authorization, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Обов'язковий для створення відправлень за контрактом (9.1).
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (requestId != null) {
            headers.set("X-Request-Id", requestId);
        }
        return rest.postForEntity("/api/v1/shipments", new HttpEntity<>(json, headers), String.class);
    }

    private static String draftRequest() {
        return """
                {
                  "draft": true,
                  "type": "PARCEL",
                  "sender": {"firstName": "Олена", "lastName": "Коваль", "phone": "+380501234567"},
                  "recipient": {"firstName": "Іван", "lastName": "Мельник", "phone": "+380671112233"},
                  "originLocationId": "%s",
                  "destinationLocationId": "%s",
                  "payer": "SENDER",
                  "declaredValue": {"amountMinor": 150000, "currency": "UAH"},
                  "parcels": [
                    {"declaredWeightGrams": 1200, "declaredDimensions": {"lengthMm": 300, "widthMm": 200, "heightMm": 100}}
                  ]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    /** Після навмисного дубля повертає послідовність за найбільший виданий номер. */
    private void restoreSequence() {
        jdbc.sql("""
                        SELECT setval('tracking_number_seq',
                                      coalesce((SELECT max(substring(tracking_number, 3, 8)::bigint) FROM shipment), 1))
                        """).query().singleValue();
    }

    private long outboxRowsFor(String trackingNumber) {
        return count("SELECT count(*) FROM outbox WHERE payload ->> 'subject' = :n", trackingNumber);
    }

    private long count(String sql, String trackingNumber) {
        var statement = jdbc.sql(sql);
        if (trackingNumber != null) {
            statement = statement.param("n", trackingNumber);
        }
        return statement.query(Long.class).single();
    }

    /** Усі записи топіку з цим subject — незалежно від ключа, бо id відкоченої накладної невідомий. */
    private List<JsonNode> eventsWithSubject(String subject, Duration window) throws Exception {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                TestcontainersConfiguration.KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false);
        List<JsonNode> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.nanoTime() + window.toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    JsonNode event = mapper.readTree(record.value());
                    if (subject.equals(event.path("subject").asText())) {
                        found.add(event);
                    }
                }
            }
        }
        return found;
    }
}
