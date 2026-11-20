package ua.kite.shipment.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.kite.contracts.api.ShipmentsApi;
import ua.kite.contracts.model.CreateShipmentRequest;
import ua.kite.contracts.model.Shipment;
import ua.kite.shipment.shipment.ShipmentDrafts;
import ua.kite.shipment.shipment.ShipmentViews;

/**
 * Відправлення клієнта (9.3). Інтерфейс, шляхи й моделі — з kite-contracts; методи, яких тут
 * немає, інтерфейс за замовчуванням відповідає 501.
 */
@RestController
@RequestMapping("/api/v1")
public class ShipmentsController implements ShipmentsApi {

    private final ShipmentDrafts drafts;

    public ShipmentsController(ShipmentDrafts drafts) {
        this.drafts = drafts;
    }

    @Override
    public ResponseEntity<Shipment> createShipment(UUID idempotencyKey, CreateShipmentRequest request) {
        // Idempotency-Key поки не зберігається: повтор створить другу чернетку. Окрема задача.
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        var shipment = drafts.create(request, userId);
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + shipment.getId()))
                .eTag(String.valueOf(shipment.getVersion()))
                .body(ShipmentViews.toApi(shipment));
    }
}
