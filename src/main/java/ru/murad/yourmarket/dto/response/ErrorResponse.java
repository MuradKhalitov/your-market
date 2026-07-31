package ru.murad.yourmarket.dto.response;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String errorCode, String message, String path) {}
