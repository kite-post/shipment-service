package ua.kite.shipment.shipment;

import ua.kite.contracts.model.Dimensions;
import ua.kite.contracts.model.LocationRef;
import ua.kite.contracts.model.Money;
import ua.kite.contracts.model.Party;

/** Сутність → модель REST-контракту. Сутності назовні не виходять (5.4, п. 6). */
public final class ShipmentViews {

    private ShipmentViews() {}

    public static ua.kite.contracts.model.Shipment toApi(Shipment shipment) {
        var api = new ua.kite.contracts.model.Shipment()
                .id(shipment.getId())
                .trackingNumber(shipment.getTrackingNumber())
                .type(shipment.getType())
                .status(shipment.getStatus())
                .sender(party(shipment.getSender()))
                .recipient(party(shipment.getRecipient()))
                // Назву, тип і місто точки знає network-service; поки віддаємо лише id.
                .origin(new LocationRef().id(shipment.getOriginLocationId()))
                .destination(new LocationRef().id(shipment.getDestinationLocationId()))
                .payer(shipment.getPayer())
                .declaredValue(money(shipment.getDeclaredValue()))
                .codAmount(money(shipment.getCodAmount()))
                .priceTotal(money(shipment.getPriceTotal()))
                .paid(false)
                .version(shipment.getVersion())
                .createdAt(shipment.getCreatedAt())
                .updatedAt(shipment.getUpdatedAt());
        for (Parcel parcel : shipment.getParcels()) {
            api.addParcelsItem(new ua.kite.contracts.model.Parcel()
                    .id(parcel.getId())
                    .seq(parcel.getSeq())
                    .barcode(parcel.getBarcode())
                    .declaredWeightGrams(parcel.getDeclaredWeightGrams())
                    .declaredDimensions(dimensions(parcel.getDeclaredDimensions()))
                    .status(shipment.getStatus()));
        }
        return api;
    }

    static Money money(Long amountMinor) {
        return amountMinor == null ? null : new Money(amountMinor, Money.CurrencyEnum.UAH);
    }

    private static Party party(PartySnapshot p) {
        return new Party()
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .middleName(p.getMiddleName())
                .phone(p.getPhone())
                .email(p.getEmail());
    }

    private static Dimensions dimensions(ParcelDimensions d) {
        return new Dimensions(d.getLengthMm(), d.getWidthMm(), d.getHeightMm());
    }
}
