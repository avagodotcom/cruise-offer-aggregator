package io.github.avagodotcom.cruise.domain;

import java.time.LocalDate;

public record SearchRequest(
        String shipCode,
        LocalDate sailDate,
        int adults,
        int children,
        String cabinClass
) {}
