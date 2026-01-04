package io.github.avagodotcom.cruise.suppliers;

import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SearchRequest;
import io.github.avagodotcom.cruise.suppliers.parsers.SoapOfferParser;
import java.io.InputStream;
import java.util.List;

public class FixtureSoapSupplier implements SupplierClient {
    private final String resourcePath;
    private final String vendor;
    private final SoapOfferParser parser = new SoapOfferParser();

    public FixtureSoapSupplier(String resourcePath, String vendor) {
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
