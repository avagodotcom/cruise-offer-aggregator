package io.github.avagodotcom.cruise.suppliers.parsers;

import io.github.avagodotcom.cruise.domain.SearchRequest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class RestOfferParserTest {

    @Test
    void parsesAndFiltersOffers() throws Exception {
        SearchRequest req = new SearchRequest("FR", LocalDate.parse("2026-07-25"), 2, 1, "INTERIOR");

        try (InputStream in = getClass().getResourceAsStream("/fixtures/vendorA_rest.json")) {
            assertNotNull(in);
            RestOfferParser p = new RestOfferParser();
            var offers = p.parse(in, req);

            assertEquals(1, offers.size());
            assertEquals("VendorA", offers.get(0).vendor());
            assertEquals("INTERIOR", offers.get(0).cabinClass());
            assertEquals(174268, offers.get(0).price().cents());
        }
    }
}
