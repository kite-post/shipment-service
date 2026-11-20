package ua.kite.shipment.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import ua.kite.contracts.model.FieldError;
import ua.kite.contracts.model.Problem;
import ua.kite.shipment.events.outbox.Outbox;
import ua.kite.shipment.shipment.ShipmentRuleException;

/** Помилки у форматі {@code application/problem+json} (RFC 9457) з полями з 9.1. */
@RestControllerAdvice
public class ProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> invalid(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getCode()).message(f.getDefaultMessage()))
                .toList();
        return respond(problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Запит не пройшов перевірку", request)
                .errors(errors));
    }

    // Тіло не JSON контракту, немає обов'язкового заголовка (Idempotency-Key) або він не UUID.
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<Problem> unreadable(HttpServletRequest request) {
        return respond(problem(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Запит не відповідає контракту: тіло, заголовки або параметри",
                request));
    }

    @ExceptionHandler(ShipmentRuleException.class)
    ResponseEntity<Problem> rule(ShipmentRuleException e, HttpServletRequest request) {
        return respond(problem(HttpStatus.UNPROCESSABLE_ENTITY, e.code(), e.getMessage(), request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Problem> conflict(DataIntegrityViolationException e, HttpServletRequest request) {
        // Деталі обмеження — в лог, не клієнту: там можуть бути значення колонок.
        log.warn("Порушено обмеження бази: {}", e.getMostSpecificCause().getMessage());
        return respond(problem(HttpStatus.CONFLICT, "SHIPMENT_CONFLICT", "Зміна суперечить наявним даним", request));
    }

    private static Problem problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        return new Problem("about:blank", status.getReasonPhrase(), status.value())
                .code(code)
                .detail(detail)
                .instance(request.getRequestURI())
                .requestId(MDC.get(Outbox.REQUEST_ID_MDC_KEY));
    }

    private static ResponseEntity<Problem> respond(Problem problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(PROBLEM_JSON)
                .body(problem);
    }
}
