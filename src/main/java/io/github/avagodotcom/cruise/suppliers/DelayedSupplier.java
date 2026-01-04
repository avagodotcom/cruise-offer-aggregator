package io.github.avagodotcom.cruise.suppliers;

import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SearchRequest;

import java.util.List;

public class DelayedSupplier implements SupplierClient {
    private final SupplierClient delegate;
    private final long delayMs;

    public DelayedSupplier(SupplierClient delegate, long delayMs) {
        this.delegate = delegate;
        this.delayMs = delayMs;
    }

    @Override
    public String vendorName() {
        return delegate.vendorName();
    }

    @Override
    public List<Offer> fetchOffers(SearchRequest req) throws Exception {
        // This will get interrupted when the global timeout cancels tasks.
        Thread.sleep(delayMs);
        return delegate.fetchOffers(req);
    }
}