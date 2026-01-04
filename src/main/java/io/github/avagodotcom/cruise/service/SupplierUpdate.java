package io.github.avagodotcom.cruise.service;

import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SupplierStatus;

import java.util.List;

public record SupplierUpdate(
        String vendor,
        List<Offer> offers,
        SupplierStatus status
) {}
