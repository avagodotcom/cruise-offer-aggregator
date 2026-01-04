package io.github.avagodotcom.cruise.suppliers;

import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SearchRequest;

import java.util.List;

public interface SupplierClient {
    String vendorName();
    List<Offer> fetchOffers(SearchRequest req) throws Exception;
}
