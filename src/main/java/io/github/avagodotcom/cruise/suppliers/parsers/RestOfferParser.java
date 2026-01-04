package io.github.avagodotcom.cruise.suppliers.parsers;

import io.github.avagodotcom.cruise.domain.Money;
import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RestOfferParser {
    private final ObjectMapper om = new ObjectMapper();

    public List<Offer> parse(InputStream in, SearchRequest req) throws Exception {
        JsonNode root = om.readTree(in);
        String vendor = root.path("vendor").asText("VendorA");

        List<Offer> out = new ArrayList<>();
        for (JsonNode n : root.path("offers")) {
            String ship = n.path("shipCode").asText();
            LocalDate date = LocalDate.parse(n.path("sailDate").asText());
            String cabin = n.path("cabinClass").asText();

            if (!matchesFilter(req.shipCode(), ship)) continue;
            if (!date.equals(req.sailDate())) continue;
            if (!matchesFilter(req.cabinClass(), cabin)) continue;

            String id = n.path("id").asText();
            int nights = n.path("nights").asInt();
            long priceCents = n.path("price").asLong();
            String currency = n.path("currency").asText("USD");

            out.add(new Offer(
                    vendor,
                    ship,
                    date,
                    nights,
                    cabin,
                    new Money(priceCents, currency),
                    Instant.now(),
                    id
            ));
        }
        return out;
    }

    private static boolean matchesFilter(String filter, String value) {
        if (filter == null || filter.isBlank() || "*".equals(filter)) return true;
        return filter.equalsIgnoreCase(value);
    }

}
