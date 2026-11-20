package ua.kite.shipment.shipment;

import static ua.kite.contracts.model.KiteEventType.KITE_SHIPMENT_CREATED_V1;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.kite.contracts.model.CreateShipmentRequest;
import ua.kite.contracts.model.Money;
import ua.kite.contracts.model.ParcelRequest;
import ua.kite.contracts.model.Party;
import ua.kite.contracts.model.ShipmentCreatedData;
import ua.kite.shipment.common.UuidV7;
import ua.kite.shipment.events.outbox.Outbox;
import ua.kite.shipment.events.outbox.OutgoingEvent;

/**
 * Створення відправлення — {@code POST /shipments} (9.3).
 *
 * <p>Поки що сервіс уміє лише чернетку (DRAFT). Одразу підтверджене відправлення — це перехід T1,
 * а його умова «є ціна» (7.2) потребує pricing-service; до того {@code draft: false} отримує 422.
 */
@Service
public class ShipmentDrafts {

    /** Машинний код відмови для {@code draft: false}, поки немає розрахунку ціни. */
    public static final String CONFIRMATION_REQUIRES_PRICE = "SHIPMENT_CONFIRMATION_REQUIRES_PRICE";

    /** Відправник поки не підставляється з профілю — це зона customer-service. */
    public static final String SENDER_REQUIRED = "SHIPMENT_SENDER_REQUIRED";

    private final ShipmentRepository shipments;
    private final Outbox outbox;
    private final JdbcClient jdbc;
    private final Clock clock;

    public ShipmentDrafts(ShipmentRepository shipments, Outbox outbox, JdbcClient jdbc, Clock clock) {
        this.shipments = shipments;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Зберігає чернетку з місцями і кладе {@code kite.shipment.created.v1} в outbox — в одній
     * транзакції (5.4, п. 2): або є і накладна, і подія, або немає нічого.
     *
     * @param createdBy sub з JWT
     */
    @Transactional
    public Shipment create(CreateShipmentRequest request, String createdBy) {
        if (!Boolean.TRUE.equals(request.getDraft())) {
            throw new ShipmentRuleException(
                    CONFIRMATION_REQUIRES_PRICE,
                    "Одразу підтвердити відправлення (T1) поки не можна: для цього потрібна ціна, а її рахує"
                            + " pricing-service. Створи чернетку з \"draft\": true.");
        }
        if (request.getSender() == null) {
            throw new ShipmentRuleException(
                    SENDER_REQUIRED, "Вкажи відправника: з профілю він поки не підставляється.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        Shipment shipment = Shipment.draft(
                UuidV7.next(clock),
                TrackingNumbers.of(nextSerial()),
                request.getType(),
                snapshot(request.getSender()),
                snapshot(request.getRecipient()),
                request.getOriginLocationId(),
                request.getDestinationLocationId(),
                request.getPayer(),
                amount(request.getDeclaredValue()),
                amount(request.getCodAmount()),
                request.getComment(),
                createdBy,
                now);
        for (ParcelRequest parcel : request.getParcels()) {
            var d = parcel.getDeclaredDimensions();
            shipment.addParcel(
                    parcel.getDeclaredWeightGrams(),
                    new ParcelDimensions(d.getLengthMm(), d.getWidthMm(), d.getHeightMm()));
        }

        shipments.save(shipment);
        outbox.append(OutgoingEvent.of(KITE_SHIPMENT_CREATED_V1, createdData(shipment))
                .subject(shipment.getTrackingNumber())
                .occurredAt(now));
        // Рядки накладної Hibernate пише на flush. Робимо його тут, а не на коміті, щоб порушення
        // обмежень (унікальний номер, CHECK) вилетіло зі звичним DataIntegrityViolationException.
        // Подія вже лежить в outbox і відкотиться разом з усім.
        shipments.flush();
        return shipment;
    }

    private long nextSerial() {
        return jdbc.sql("SELECT nextval('tracking_number_seq')")
                .query(Long.class)
                .single();
    }

    private static ShipmentCreatedData createdData(Shipment shipment) {
        return new ShipmentCreatedData()
                .shipmentId(shipment.getId())
                .trackingNumber(shipment.getTrackingNumber())
                .type(shipment.getType())
                .status(shipment.getStatus())
                .draft(shipment.isDraft())
                // senderCustomerId — це customer.id, а в нас лише sub з Keycloak; зв'язок між ними
                // знає customer-service (12.3), тож поле поки порожнє.
                .senderPhoneHash(PhoneHash.of(shipment.getSender().getPhone()))
                .recipientPhoneHash(PhoneHash.of(shipment.getRecipient().getPhone()))
                .originLocationId(shipment.getOriginLocationId())
                .destinationLocationId(shipment.getDestinationLocationId())
                .payer(shipment.getPayer())
                .parcelCount(shipment.getParcels().size())
                .declaredValue(ShipmentViews.money(shipment.getDeclaredValue()))
                .codAmount(ShipmentViews.money(shipment.getCodAmount()))
                .priceTotal(ShipmentViews.money(shipment.getPriceTotal()));
    }

    private static PartySnapshot snapshot(Party party) {
        return new PartySnapshot(
                party.getFirstName(), party.getLastName(), party.getMiddleName(), party.getPhone(), party.getEmail());
    }

    private static Long amount(Money money) {
        return money == null ? null : money.getAmountMinor();
    }
}
