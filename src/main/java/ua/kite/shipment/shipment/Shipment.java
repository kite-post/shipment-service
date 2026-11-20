package ua.kite.shipment.shipment;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import ua.kite.contracts.model.PayerType;
import ua.kite.contracts.model.ShipmentStatus;
import ua.kite.contracts.model.ShipmentType;

/**
 * Відправлення — накладна з місцями. Статус змінює тільки цей сервіс (5, вступ),
 * і лише переходами стейт-машини 7.2.
 */
@Entity
@Table(name = "shipment")
public class Shipment {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "firstName", column = @Column(name = "sender_first_name", nullable = false)),
        @AttributeOverride(name = "lastName", column = @Column(name = "sender_last_name", nullable = false)),
        @AttributeOverride(name = "middleName", column = @Column(name = "sender_middle_name")),
        @AttributeOverride(name = "phone", column = @Column(name = "sender_phone", nullable = false)),
        @AttributeOverride(name = "email", column = @Column(name = "sender_email"))
    })
    private PartySnapshot sender;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "firstName", column = @Column(name = "recipient_first_name", nullable = false)),
        @AttributeOverride(name = "lastName", column = @Column(name = "recipient_last_name", nullable = false)),
        @AttributeOverride(name = "middleName", column = @Column(name = "recipient_middle_name")),
        @AttributeOverride(name = "phone", column = @Column(name = "recipient_phone", nullable = false)),
        @AttributeOverride(name = "email", column = @Column(name = "recipient_email"))
    })
    private PartySnapshot recipient;

    @Column(nullable = false)
    private UUID originLocationId;

    @Column(nullable = false)
    private UUID destinationLocationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayerType payer;

    /** Копійки, UAH (6.1). */
    private Long declaredValue;

    private Long codAmount;

    /** Рахує pricing-service; до підтвердження (T1) може бути порожнім. */
    private Long priceTotal;

    private String comment;

    /** sub з JWT — користувач Keycloak, який створив накладну. */
    @Column(nullable = false)
    private String createdBy;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "shipment_id", nullable = false)
    @OrderBy("seq")
    private List<Parcel> parcels = new ArrayList<>();

    @Version
    private Long version;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected Shipment() {}

    /** Нова чернетка (7.1, DRAFT). Підтвердження — окремий перехід T1. */
    static Shipment draft(
            UUID id,
            String trackingNumber,
            ShipmentType type,
            PartySnapshot sender,
            PartySnapshot recipient,
            UUID originLocationId,
            UUID destinationLocationId,
            PayerType payer,
            Long declaredValue,
            Long codAmount,
            String comment,
            String createdBy,
            OffsetDateTime now) {
        Shipment shipment = new Shipment();
        shipment.id = id;
        shipment.trackingNumber = trackingNumber;
        shipment.type = type;
        shipment.status = ShipmentStatus.DRAFT;
        shipment.sender = sender;
        shipment.recipient = recipient;
        shipment.originLocationId = originLocationId;
        shipment.destinationLocationId = destinationLocationId;
        shipment.payer = payer;
        shipment.declaredValue = declaredValue;
        shipment.codAmount = codAmount;
        shipment.comment = comment;
        shipment.createdBy = createdBy;
        shipment.createdAt = now;
        shipment.updatedAt = now;
        return shipment;
    }

    /** Додає місце з наступним порядковим номером і штрих-кодом {@code <номер>-NN}. */
    void addParcel(long declaredWeightGrams, ParcelDimensions declaredDimensions) {
        int seq = parcels.size() + 1;
        parcels.add(
                new Parcel(seq, TrackingNumbers.barcode(trackingNumber, seq), declaredWeightGrams, declaredDimensions));
    }

    public boolean isDraft() {
        return status == ShipmentStatus.DRAFT;
    }

    public UUID getId() {
        return id;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShipmentType getType() {
        return type;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public PartySnapshot getSender() {
        return sender;
    }

    public PartySnapshot getRecipient() {
        return recipient;
    }

    public UUID getOriginLocationId() {
        return originLocationId;
    }

    public UUID getDestinationLocationId() {
        return destinationLocationId;
    }

    public PayerType getPayer() {
        return payer;
    }

    public Long getDeclaredValue() {
        return declaredValue;
    }

    public Long getCodAmount() {
        return codAmount;
    }

    public Long getPriceTotal() {
        return priceTotal;
    }

    public String getComment() {
        return comment;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public List<Parcel> getParcels() {
        return Collections.unmodifiableList(parcels);
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
