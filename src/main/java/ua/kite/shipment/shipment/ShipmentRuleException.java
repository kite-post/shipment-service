package ua.kite.shipment.shipment;

/**
 * Запит синтаксично правильний, але правила домену його не пускають — відповідь 422 з
 * машинним {@code code} у problem+json (9.1).
 */
public class ShipmentRuleException extends RuntimeException {

    private final String code;

    public ShipmentRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
