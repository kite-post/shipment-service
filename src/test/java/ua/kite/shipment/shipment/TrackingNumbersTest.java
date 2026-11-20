package ua.kite.shipment.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrackingNumbersTest {

    @Test
    void nomerZPrykladuDokumentatsii() {
        // 10.1 і api.schema.json: KT123456785UA.
        assertThat(TrackingNumbers.of(12_345_678)).isEqualTo("KT123456785UA");
    }

    @Test
    void zalyshokDesiatDaieNul() {
        // 8 × 7 = 56, 56 mod 11 = 1, 11 − 1 = 10 → 0.
        assertThat(TrackingNumbers.checkDigit("00000008")).isZero();
    }

    @Test
    void zalyshokNulDaiePiat() {
        // 1 × 8 + 1 × 3 = 11, 11 mod 11 = 0, 11 − 0 = 11 → 5.
        assertThat(TrackingNumbers.checkDigit("10001000")).isEqualTo(5);
    }

    @Test
    void nomerVidpovidaieShabluS10() {
        assertThat(TrackingNumbers.of(1)).matches("^KT\\d{9}UA$");
        assertThat(TrackingNumbers.of(TrackingNumbers.MAX_SERIAL)).matches("^KT\\d{9}UA$");
    }

    @Test
    void poriadkovyiNomerPozaMezhamyVidkhyliaietsia() {
        assertThatThrownBy(() -> TrackingNumbers.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingNumbers.of(100_000_000L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shtrykhKodMistsia() {
        assertThat(TrackingNumbers.barcode("KT123456785UA", 1)).isEqualTo("KT123456785UA-01");
        assertThat(TrackingNumbers.barcode("KT123456785UA", 20)).isEqualTo("KT123456785UA-20");
        assertThatThrownBy(() -> TrackingNumbers.barcode("KT123456785UA", 21))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kheshTelefonuIzShablonuPodii() {
        assertThat(PhoneHash.of("+380501234567")).matches("^[a-f0-9]{64}$");
    }
}
