package io.github.avagodotcom.cruise.domain;

import java.time.Instant;
import java.time.LocalDate;

public record Offer(
        String vendor,
        String shipCode,
        LocalDate sailDate,
        int nights,
        String cabinClass,
        Money price,
        Instant retrievedAt,
        String rawOfferId
) {}
