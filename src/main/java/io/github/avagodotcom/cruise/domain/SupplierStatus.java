package io.github.avagodotcom.cruise.domain;

public record SupplierStatus(
        String vendor,
        String status,        // OK / TIMEOUT / ERROR
        long elapsedMs,
        String errorMessage
) {}
