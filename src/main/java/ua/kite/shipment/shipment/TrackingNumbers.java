package ua.kite.shipment.shipment;

/**
 * Номер відправлення у форматі UPU S10 і штрих-коди місць (10.1).
 *
 * <p>{@code KT} + 8 цифр + контрольна цифра + {@code UA}. Контрольна: ваги 8, 6, 4, 2, 3, 5, 9, 7
 * до восьми цифр, сума за модулем 11; результат = 11 − залишок, де 10 → 0 і 11 → 5.
 */
public final class TrackingNumbers {

    public static final int MAX_SERIAL = 99_999_999;
    public static final int MAX_PARCELS = 20;

    private static final int[] WEIGHTS = {8, 6, 4, 2, 3, 5, 9, 7};

    private TrackingNumbers() {}

    /** {@code 12345678} → {@code KT123456785UA}. */
    public static String of(long serial) {
        if (serial < 1 || serial > MAX_SERIAL) {
            throw new IllegalArgumentException("Порядковий номер поза межами S10: " + serial);
        }
        String digits = String.format("%08d", serial);
        return "KT" + digits + checkDigit(digits) + "UA";
    }

    /** {@code KT123456785UA}, 1 → {@code KT123456785UA-01}. */
    public static String barcode(String trackingNumber, int seq) {
        if (seq < 1 || seq > MAX_PARCELS) {
            throw new IllegalArgumentException("Номер місця має бути від 1 до 20: " + seq);
        }
        return trackingNumber + "-" + String.format("%02d", seq);
    }

    static int checkDigit(String eightDigits) {
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (eightDigits.charAt(i) - '0') * WEIGHTS[i];
        }
        int check = 11 - sum % 11;
        return switch (check) {
            case 10 -> 0;
            case 11 -> 5;
            default -> check;
        };
    }
}
