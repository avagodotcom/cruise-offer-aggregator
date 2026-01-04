package io.github.avagodotcom.cruise.cli;

import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SearchRequest;
import io.github.avagodotcom.cruise.persistence.Db;
import io.github.avagodotcom.cruise.persistence.OfferRepository;
import io.github.avagodotcom.cruise.persistence.RunRepository;
import io.github.avagodotcom.cruise.persistence.VendorHealthRepository;
import io.github.avagodotcom.cruise.service.OfferAggregatorService;
import io.github.avagodotcom.cruise.suppliers.DelayedSupplier;
import io.github.avagodotcom.cruise.suppliers.FixtureRestSupplier;
import io.github.avagodotcom.cruise.suppliers.FixtureSoapSupplier;
import io.github.avagodotcom.cruise.suppliers.SupplierClient;

import java.time.LocalDate;
import java.util.*;

public class Cli {
    private final Db db;

    public Cli(Db db) {
        this.db = db;
    }

    static boolean isOnboard(LocalDate depart, int nights, LocalDate onDate) {
        LocalDate arrive = depart.plusDays(nights);
        return depart.isBefore(onDate) && onDate.isBefore(arrive);
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
            case "onboard" -> handleOnboard(rest);
            default -> {
                System.err.println("Unknown command: " + cmd);
                printUsage();
            }
        }
    }

    private void handleSearch(String[] args) throws Exception {
        ArgMap am = ArgMap.parse(args);

        LocalDate date = LocalDate.parse(am.req("--date"));
        String ship = am.optOrDefault("--ship", "*");
        String cabin = am.optOrDefault("--cabin", "*");
        int adults = Integer.parseInt(am.optOrDefault("--adults", "2"));
        int children = Integer.parseInt(am.optOrDefault("--children", "0"));

        SearchRequest req = new SearchRequest(ship, date, adults, children, cabin);

        var suppliers = createSuppliers();
        var svc = buildService(suppliers);

        System.out.println("---- Supplier Status (streaming) ----");
        for (var s : suppliers) {
            System.out.println(s.vendorName() + " => fetching...");
        }

        var result = svc.search(req, 900, update -> {
            var st = update.status();
            System.out.printf("%s => %s (%d ms)%s%n",
                    st.vendor(), st.status(), st.elapsedMs(),
                    st.errorMessage() == null ? "" : " | " + st.errorMessage());

            if (!update.offers().isEmpty()) {
                for (var o : update.offers()) {
                    System.out.printf("  %s | %s %s | %s | %d nights | %s | rawId=%s%n",
                            o.vendor(), o.shipCode(), o.sailDate(), o.cabinClass(), o.nights(),
                            o.price().format(), o.rawOfferId());
                }
            }
        });

        System.out.println("\n---- Offers (final sorted by price) ----");
        result.offers().forEach(o -> System.out.printf(
                "%s | %s %s | %s | %d nights | %s | rawId=%s%n",
                o.vendor(), o.shipCode(), o.sailDate(), o.cabinClass(), o.nights(),
                o.price().format(), o.rawOfferId()
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

    private void handleOnboard(String[] args) throws Exception {
        Map<String, Offer> allMatches = new LinkedHashMap<>();
        ArgMap am = ArgMap.parse(args);

        LocalDate onDate = LocalDate.parse(am.req("--on"));
        int maxNights = Integer.parseInt(am.optOrDefault("--maxNights", "14"));

        String ship = am.optOrDefault("--ship", "*");
        String cabin = am.optOrDefault("--cabin", "*");
        int adults = Integer.parseInt(am.optOrDefault("--adults", "2"));
        int children = Integer.parseInt(am.optOrDefault("--children", "0"));

        long timeoutMs = Long.parseLong(am.optOrDefault("--timeoutMs", "900"));

        var suppliers = createSuppliers();
        var svc = buildService(suppliers);

        LocalDate from = onDate.minusDays(maxNights);
        LocalDate to = onDate.minusDays(1);

        Set<String> printed = new HashSet<>();

        System.out.println("---- ONBOARD streaming search ----");
        System.out.println("Onboard date: " + onDate + " | departure window: " + from + " .. " + to);
        System.out.println("Filters: ship=" + ship + " cabin=" + cabin + " | timeout=" + timeoutMs + "ms\n");

        for (LocalDate depart = from; !depart.isAfter(to); depart = depart.plusDays(1)) {
            SearchRequest req = new SearchRequest(ship, depart, adults, children, cabin);

            System.out.println("== Departing " + depart + " (searching providers...) ==");

            svc.search(req, timeoutMs, update -> {
                var st = update.status();
                System.out.printf("  %s => %s (%d ms)%s%n",
                        st.vendor(), st.status(), st.elapsedMs(),
                        st.errorMessage() == null ? "" : " | " + st.errorMessage());

                // Print matching offers immediately as soon as that provider returns
                for (Offer o : update.offers()) {
                    if (isOnboard(o.sailDate(), o.nights(), onDate)) {
                        String key = o.vendor() + "|" + o.rawOfferId(); // unique enough for demo
                        allMatches.putIfAbsent(key, o);
                        if (printed.add(key)) {
                            LocalDate debark = o.sailDate().plusDays(o.nights());
                            System.out.printf("    MATCH: %s | %s depart=%s nights=%d debark=%s | %s | %s | rawId=%s%n",
                                    o.vendor(), o.shipCode(), o.sailDate(), o.nights(), debark,
                                    o.cabinClass(), o.price().format(), o.rawOfferId());
                        }
                    }
                }
            });

            System.out.println(); // blank line between departure days
        }

        System.out.println("---- End of onboard streaming search ----");

        System.out.println();
        System.out.println("---- Cruises ONBOARD on " + onDate + "(strict: not embark/debark day) (final summary, sorted by price) ----");

        if (allMatches.isEmpty()) {
            System.out.println("(no matches)");
        } else {
            List<Offer> sorted = new ArrayList<>(allMatches.values());
            sorted.sort(Comparator.comparingLong(o -> o.price().cents()));

            for (Offer o : sorted) {
                LocalDate debark = o.sailDate().plusDays(o.nights());
                System.out.printf("%s | %s depart=%s nights=%d debark=%s | %s | %s | rawId=%s%n",
                        o.vendor(), o.shipCode(), o.sailDate(), o.nights(), debark,
                        o.cabinClass(), o.price().format(), o.rawOfferId());
            }
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


    private static void printUsage() {
        System.out.println("""
                Cruise Offer Aggregator (CLI)

                Rule:
                  - Options shown in [brackets] are optional. If omitted, no filtering is applied (i.e., "include all").
                  - Dates use ISO format: YYYY-MM-DD

                Commands:

                  search
                    Departure-centric search (single sail date).
                    Usage:
                      search --date YYYY-MM-DD [--ship CODE] [--cabin CLASS] [--adults N] [--children N]
                    Defaults: adults=2, children=0, timeoutMs=1500
                    Example:
                      search --date 2026-07-25 --ship FR --cabin INTERIOR --adults 2 --children 1

                  onboard
                    Find cruises where you are ONBOARD on a date (excludes embark/debark day).
                    Searches departure window: [onDate - maxNights .. onDate - 1]
                    Usage:
                      onboard --on YYYY-MM-DD [--maxNights N] [--ship CODE] [--cabin CLASS] [--adults N] [--children N]
                    Defaults: maxNights=14, adults=2, children=0, timeoutMs=1500
                    Example:
                      onboard --on 2026-07-29 --ship FR --cabin INTERIOR --maxNights 14

                  history
                    Show persisted offer history for ship + sail date.
                    Usage:
                      history --ship CODE --date YYYY-MM-DD [--limit N]
                    Default: limit=50
                    Example:
                      history --ship FR --date 2026-07-25 --limit 20

                  vendors
                    Show vendor health (last success/error).
                    Usage:
                      vendors
                """);
    }




    private static String[] slice(String[] a, int start) {
        if (start >= a.length) return new String[0];
        String[] out = new String[a.length - start];
        System.arraycopy(a, start, out, 0, out.length);
        return out;
    }

    private List<SupplierClient> createSuppliers() {
        return List.of(
                //new FixtureRestSupplier("/fixtures/vendorA_rest.json", "VendorA"),
                new DelayedSupplier(new FixtureRestSupplier("/fixtures/vendorA_rest.json", "VendorA"), 200),
                new FixtureSoapSupplier("/fixtures/vendorB_soap.xml", "VendorB"),
                new DelayedSupplier(new FixtureRestSupplier("/fixtures/legacy_gds_rest.json", "LEGACY_GDS"), 1000)
        );
    }

    private OfferAggregatorService buildService(List<SupplierClient> suppliers) {

        var runRepo = new RunRepository(db);
        var offerRepo = new OfferRepository(db);
        var healthRepo = new VendorHealthRepository(db);

        return new OfferAggregatorService(suppliers, offerRepo, runRepo, healthRepo);
    }
}
