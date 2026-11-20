package ua.kite.shipment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Keycloak у тестах не потрібен: токен {@code test.<sub>} розбирається в JWT з цим sub, решта
 * отримує 401. Замінює справжній {@link JwtDecoder}, тож ланцюжок безпеки сервіса той самий.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwt {

    // Символи — з дозволених для Bearer (RFC 6750): двокрапку резолвер токена відкидає.
    private static final String PREFIX = "test.";

    public static String bearer(String subject) {
        return "Bearer " + PREFIX + subject;
    }

    @Bean
    JwtDecoder testJwtDecoder() {
        return token -> {
            if (!token.startsWith(PREFIX) || token.length() == PREFIX.length()) {
                throw new BadJwtException("Не тестовий токен");
            }
            Instant now = Instant.now();
            return new Jwt(
                    token,
                    now,
                    now.plusSeconds(300),
                    Map.of("alg", "none"),
                    Map.of("sub", token.substring(PREFIX.length()), "aud", List.of("kite-api")));
        };
    }
}
