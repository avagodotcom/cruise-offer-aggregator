package io.github.avagodotcom.cruise.suppliers;

import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SearchRequest;
import io.github.avagodotcom.cruise.suppliers.parsers.RestOfferParser;

import java.io.InputStream;
import java.util.List;

public class FixtureRestSupplier implements SupplierClient {
    private final String resourcePath;
    private final String vendor;
    private final RestOfferParser parser = new RestOfferParser();

    public FixtureRestSupplier(String resourcePath, String vendor) {
        this.resourcePath = resourcePath;
        this.vendor = vendor;
    }

    @Override public String vendorName() { return vendor; }

    @Override
    public List<Offer> fetchOffers(SearchRequest req) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + resourcePath);
            return parser.parse(in, req);
        }
    }
}