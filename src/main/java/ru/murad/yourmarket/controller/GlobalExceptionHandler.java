package ru.murad.yourmarket.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.murad.yourmarket.dto.response.ErrorResponse;
import ru.murad.yourmarket.exception.*;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AdvertisementNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "ADVERTISEMENT_NOT_FOUND", ex, request);
    }

    @ExceptionHandler({InvalidAdvertisementStateException.class, InvalidPaymentStateException.class})
    ResponseEntity<ErrorResponse> conflict(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "INVALID_STATE", ex, request);
    }

    @ExceptionHandler(TelegramPublicationException.class)
    ResponseEntity<ErrorResponse> telegram(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_GATEWAY, "TELEGRAM_ERROR", ex, request);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ErrorResponse> unexpected(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                new RuntimeException("Внутренняя ошибка сервера"), request);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, RuntimeException ex,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(), status.value(), code, ex.getMessage(), request.getRequestURI()));
    }
}
