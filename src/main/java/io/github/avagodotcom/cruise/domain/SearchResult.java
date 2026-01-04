package io.github.avagodotcom.cruise.domain;

import java.util.List;

public record SearchResult(
        List<Offer> offers,
        List<SupplierStatus> statuses
) {}
