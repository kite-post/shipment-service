package ua.kite.shipment.shipment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 телефону в hex — так телефон їде в подіях (common.schema.json, PhoneHash): події
 * не носять самих номерів, а tracking-service за хешем будує дашборд «мені / від мене» (6.2).
 * Хешується номер у E.164 як є, тож усі сервіси отримують той самий хеш.
 */
public final class PhoneHash {

    private PhoneHash() {}

    public static String of(String e164) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(e164.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 є в кожній JVM", e);
        }
    }
}
