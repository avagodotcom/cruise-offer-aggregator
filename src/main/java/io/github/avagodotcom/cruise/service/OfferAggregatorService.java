package io.github.avagodotcom.cruise.service;

import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SearchRequest;
import io.github.avagodotcom.cruise.domain.SearchResult;
import io.github.avagodotcom.cruise.domain.SupplierStatus;
import io.github.avagodotcom.cruise.persistence.OfferRepository;
import io.github.avagodotcom.cruise.persistence.RunRepository;
import io.github.avagodotcom.cruise.persistence.VendorHealthRepository;
import io.github.avagodotcom.cruise.suppliers.SupplierClient;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

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
        return search(req, 900, null);
    }

    public SearchResult search(SearchRequest req, long timeoutMs, Consumer<SupplierUpdate> onUpdate) throws Exception {
        long runId = runRepo.insertRun(req);

        ExecutorService exec = Executors.newFixedThreadPool(Math.min(4, suppliers.size()));
        CompletionService<SupplierCallResult> cs = new ExecutorCompletionService<>(exec);

        Map<Future<SupplierCallResult>, SupplierClient> futureToSupplier = new HashMap<>();
        for (SupplierClient s : suppliers) {
            Future<SupplierCallResult> f = cs.submit(() -> callSupplier(s, req));
            futureToSupplier.put(f, s);
        }

        List<Offer> allOffers = new ArrayList<>();
        List<SupplierStatus> statuses = new ArrayList<>();

        long deadline = System.currentTimeMillis() + timeoutMs;

        try {
            while (!futureToSupplier.isEmpty()) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;

                Future<SupplierCallResult> done = cs.poll(remaining, TimeUnit.MILLISECONDS);
                if (done == null) break; // timed out waiting for any more completions

                SupplierClient supplier = futureToSupplier.remove(done);

                try {
                    SupplierCallResult r = done.get(); // already completed
                    SupplierStatus st = new SupplierStatus(r.vendor, "OK", r.elapsedMs, null);
                    statuses.add(st);
                    healthRepo.markSuccess(r.vendor);
                    allOffers.addAll(r.offers);

                    if (onUpdate != null) {
                        onUpdate.accept(new SupplierUpdate(r.vendor, r.offers, st));
                    }
                } catch (ExecutionException ex) {
                    String msg = ex.getCause() == null ? ex.toString() : ex.getCause().toString();
                    SupplierStatus st = new SupplierStatus(supplier.vendorName(), "ERROR", 0, msg);
                    statuses.add(st);
                    healthRepo.markError(supplier.vendorName(), msg);

                    if (onUpdate != null) {
                        onUpdate.accept(new SupplierUpdate(supplier.vendorName(), List.of(), st));
                    }
                }
            }

            for (Map.Entry<Future<SupplierCallResult>, SupplierClient> e : futureToSupplier.entrySet()) {
                e.getKey().cancel(true);
                SupplierClient s = e.getValue();

                SupplierStatus st = new SupplierStatus(s.vendorName(), "TIMEOUT", timeoutMs, "Timed out");
                statuses.add(st);
                healthRepo.markError(s.vendorName(), "Timeout");

                if (onUpdate != null) {
                    onUpdate.accept(new SupplierUpdate(s.vendorName(), List.of(), st));
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
