package io.github.avagodotcom.cruise.cli;

import io.github.avagodotcom.cruise.domain.SearchRequest;
import io.github.avagodotcom.cruise.persistence.Db;
import io.github.avagodotcom.cruise.persistence.OfferRepository;
import io.github.avagodotcom.cruise.persistence.RunRepository;
import io.github.avagodotcom.cruise.persistence.VendorHealthRepository;
import io.github.avagodotcom.cruise.service.OfferAggregatorService;
import io.github.avagodotcom.cruise.suppliers.FixtureRestSupplier;
import io.github.avagodotcom.cruise.suppliers.FixtureSoapSupplier;

import java.time.LocalDate;
import java.util.List;

public class Cli {
    private final Db db;

    public Cli(Db db) {
        this.db = db;
    }

    public void run(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String cmd = args[0].toLowerCase();
        String[] rest = slice(args, 1);

        switch (cmd) {
            case "search" -> handleSearch(rest);
            case "history" -> handleHistory(rest);
            case "vendors" -> handleVendors();
            case "special" -> handleSpecial(rest);
            default -> {
                System.err.println("Unknown command: " + cmd);
                printUsage();
            }
        }
    }

    private void handleSearch(String[] args) throws Exception {
        ArgMap am = ArgMap.parse(args);

        SearchRequest req = new SearchRequest(
                am.req("--ship"),
                LocalDate.parse(am.req("--date")),
                am.reqInt("--adults"),
                am.reqInt("--children"),
                am.req("--cabin")
        );

        var suppliers = List.of(
                new FixtureRestSupplier("/fixtures/vendorA_rest.json", "VendorA"),
                new FixtureSoapSupplier("/fixtures/vendorB_soap.xml", "VendorB")
        );

        var runRepo = new RunRepository(db);
        var offerRepo = new OfferRepository(db);
        var healthRepo = new VendorHealthRepository(db);

        var svc = new OfferAggregatorService(suppliers, offerRepo, runRepo, healthRepo);
        var result = svc.search(req);

        System.out.println("---- Offers (sorted by price) ----");
        if (result.offers().isEmpty()) {
            System.out.println("(no offers returned)");
        } else {
            result.offers().forEach(o -> System.out.printf(
                    "%s | %s %s | %s | %d nights | %s | rawId=%s%n",
                    o.vendor(), o.shipCode(), o.sailDate(), o.cabinClass(),
                    o.nights(), o.price().format(), o.rawOfferId()
            ));
        }

        System.out.println("\n---- Supplier Status ----");
        result.statuses().forEach(s -> System.out.printf(
                "%s => %s (%d ms)%s%n",
                s.vendor(), s.status(), s.elapsedMs(),
                (s.errorMessage() == null ? "" : " | " + s.errorMessage())
        ));
    }

    private void handleHistory(String[] args) throws Exception {
        ArgMap am = ArgMap.parse(args);
        String ship = am.req("--ship");
        LocalDate date = LocalDate.parse(am.req("--date"));

        var repo = new OfferRepository(db);
        var rows = repo.history(ship, date);

        System.out.println("---- Price history (last 50) ----");
        if (rows.isEmpty()) {
            System.out.println("(no history yet - run 'search' a few times)");
        } else {
            rows.forEach(r -> System.out.printf(
                    "%s | %s | %s | %s%n",
                    r.retrievedAt(), r.vendor(), r.cabinClass(), r.price().format()
            ));
        }
    }

    private void handleVendors() throws Exception {
        var repo = new VendorHealthRepository(db);
        var rows = repo.list();
        System.out.println("---- Vendor health ----");
        if (rows.isEmpty()) {
            System.out.println("(no vendor health yet - run 'search')");
        } else {
            rows.forEach(v -> System.out.printf(
                    "%s | lastSuccess=%s | lastError=%s | %s%n",
                    v.vendor(), v.lastSuccessAt(), v.lastErrorAt(),
                    (v.lastErrorMessage() == null ? "" : v.lastErrorMessage())
            ));
        }
    }

    private void handleSpecial(String[] args) throws Exception {
        ArgMap am = ArgMap.parse(args);

        var onDate = LocalDate.parse(am.req("--on"));
        String ship = am.opt("--ship");     // optional
        String cabin = am.opt("--cabin");   // optional

        var repo = new OfferRepository(db);
        var rows = repo.sailingOnDate(onDate, ship, cabin);

        System.out.println("---- Cruises where you are ONBOARD on " + onDate + " (excluding embark/debark) ----");
        if (rows.isEmpty()) {
            System.out.println("(no matches yet - run 'search' for multiple sail dates first)");
            return;
        }

        rows.forEach(r -> System.out.printf(
                "%s | %s %s | %s | %d nights | onboard=%s..%s | %s%n",
                r.vendor(),
                r.shipCode(),
                r.sailDate(),
                r.cabinClass(),
                r.nights(),
                r.sailDate().plusDays(1),
                r.debarkDate().minusDays(1),
                r.price().format()
        ));
    }


    private void printUsage() {
        System.out.println("""
                Usage:
                  search  --ship FR --date 2026-07-25 --adults 2 --children 1 --cabin INTERIOR
                  history --ship FR --date 2026-07-25
                  vendors
                  special --on 2026-07-29 [--ship FR] [--cabin INTERIOR]
                """);
    }


    private static String[] slice(String[] a, int start) {
        if (start >= a.length) return new String[0];
        String[] out = new String[a.length - start];
        System.arraycopy(a, start, out, 0, out.length);
        return out;
    }
}
