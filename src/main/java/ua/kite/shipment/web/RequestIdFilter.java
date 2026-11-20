package ua.kite.shipment.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ua.kite.shipment.events.outbox.Outbox;

/**
 * {@code X-Request-Id} від шлюзу → MDC (9.1, «Трасування»). Звідти його бере {@link Outbox} для
 * {@code kiterequestid} у події і обробник помилок для {@code requestId} у problem+json.
 * Без заголовка (запит не через шлюз) id генерується тут.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    // Чужий заголовок потрапляє в логи й події — пускаємо лише короткий і без керівних символів.
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !SAFE.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(Outbox.REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Outbox.REQUEST_ID_MDC_KEY);
        }
    }
}
