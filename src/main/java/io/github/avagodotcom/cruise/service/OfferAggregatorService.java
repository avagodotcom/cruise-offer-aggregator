package io.github.avagodotcom.cruise.service;

import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SearchRequest;
import io.github.avagodotcom.cruise.domain.SearchResult;
import io.github.avagodotcom.cruise.domain.SupplierStatus;
import io.github.avagodotcom.cruise.persistence.OfferRepository;
import io.github.avagodotcom.cruise.persistence.RunRepository;
import io.github.avagodotcom.cruise.persistence.VendorHealthRepository;
import io.github.avagodotcom.cruise.suppliers.SupplierClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

public class OfferAggregatorService {
    private final List<SupplierClient> suppliers;
    private final OfferRepository offerRepo;
    private final RunRepository runRepo;
    private final VendorHealthRepository healthRepo;

    public OfferAggregatorService(List<SupplierClient> suppliers,
                                  OfferRepository offerRepo,
                                  RunRepository runRepo,
                                  VendorHealthRepository healthRepo) {
        this.suppliers = suppliers;
        this.offerRepo = offerRepo;
        this.runRepo = runRepo;
        this.healthRepo = healthRepo;
    }

    public SearchResult search(SearchRequest req) throws Exception {
        long runId = runRepo.insertRun(req);

        ExecutorService exec = Executors.newFixedThreadPool(Math.min(4, suppliers.size()));
        List<Callable<SupplierCallResult>> tasks = new ArrayList<>();

        for (SupplierClient s : suppliers) {
            tasks.add(() -> callSupplier(s, req));
        }

        List<Offer> allOffers = new ArrayList<>();
        List<SupplierStatus> statuses = new ArrayList<>();

        long globalTimeoutMs = 1500;

        try {
            List<Future<SupplierCallResult>> futures = exec.invokeAll(tasks, globalTimeoutMs, TimeUnit.MILLISECONDS);

            for (int i = 0; i < futures.size(); i++) {
                SupplierClient supplier = suppliers.get(i);
                Future<SupplierCallResult> f = futures.get(i);

                if (f.isCancelled()) {
                    statuses.add(new SupplierStatus(supplier.vendorName(), "TIMEOUT", globalTimeoutMs, "Timed out"));
                    healthRepo.markError(supplier.vendorName(), "Timeout");
                    continue;
                }

                try {
                    SupplierCallResult r = f.get();
                    statuses.add(new SupplierStatus(r.vendor, "OK", r.elapsedMs, null));
                    healthRepo.markSuccess(r.vendor);
                    allOffers.addAll(r.offers);
                } catch (ExecutionException ex) {
                    String msg = ex.getCause() == null ? ex.toString() : ex.getCause().toString();
                    statuses.add(new SupplierStatus(supplier.vendorName(), "ERROR", 0, msg));
                    healthRepo.markError(supplier.vendorName(), msg);
                }
            }
        } finally {
            exec.shutdownNow();
        }

        allOffers.sort(Comparator.comparingLong(o -> o.price().cents()));
        offerRepo.insertOffers(runId, allOffers);

        return new SearchResult(allOffers, statuses);
    }

    private SupplierCallResult callSupplier(SupplierClient s, SearchRequest req) throws Exception {
        long t0 = System.nanoTime();
        List<Offer> offers = s.fetchOffers(req);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        return new SupplierCallResult(s.vendorName(), offers, elapsedMs);
    }

    private record SupplierCallResult(String vendor, List<Offer> offers, long elapsedMs) {}
}
