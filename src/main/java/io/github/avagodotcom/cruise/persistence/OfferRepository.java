package io.github.avagodotcom.cruise.persistence;

import io.github.avagodotcom.cruise.domain.Money;
import io.github.avagodotcom.cruise.domain.Offer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class OfferRepository {
    private final Db db;

    public OfferRepository(Db db) {
        this.db = db;
    }

    public void insertOffers(long runId, List<Offer> offers) throws Exception {
        String sql = """
          INSERT INTO offers(run_id, vendor, ship_code, sail_date, cabin_class, nights,
                             price_cents, currency, retrieved_at, raw_offer_id)
          VALUES(?,?,?,?,?,?,?,?,?,?)
        """;

        try (var c = db.getConnection(); var ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);

            for (Offer o : offers) {
                ps.setLong(1, runId);
                ps.setString(2, o.vendor());
                ps.setString(3, o.shipCode());
                ps.setString(4, o.sailDate().toString());
                ps.setString(5, o.cabinClass());
                ps.setInt(6, o.nights());
                ps.setLong(7, o.price().cents());
                ps.setString(8, o.price().currency());
                ps.setString(9, o.retrievedAt().toString());
                ps.setString(10, o.rawOfferId());
                ps.addBatch();
            }

            ps.executeBatch();
            c.commit();
        }
    }

    public List<HistoryRow> history(String ship, LocalDate date) throws Exception {
        String sql = """
          SELECT retrieved_at, vendor, cabin_class, price_cents, currency
          FROM offers
          WHERE ship_code = ? AND sail_date = ?
          ORDER BY retrieved_at DESC
          LIMIT 50
        """;

        List<HistoryRow> out = new ArrayList<>();
        try (var c = db.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, ship);
            ps.setString(2, date.toString());
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new HistoryRow(
                            Instant.parse(rs.getString(1)),
                            rs.getString(2),
                            rs.getString(3),
                            new Money(rs.getLong(4), rs.getString(5))
                    ));
                }
            }
        }
        return out;
    }

    public List<SailingRow> sailingOnDate(LocalDate onDate, String shipOpt, String cabinOpt) throws Exception {
        // Strictly onboard: sail_date < onDate < debark_date (debark = sail_date + nights)
        StringBuilder sql = new StringBuilder("""
        SELECT vendor, ship_code, sail_date, cabin_class, nights, price_cents, currency
        FROM offers
        WHERE date(?) > date(sail_date)
          AND date(?) < date(sail_date, '+' || nights || ' days')
    """);

        List<Object> params = new ArrayList<>();
        params.add(onDate.toString());
        params.add(onDate.toString());

        if (shipOpt != null && !shipOpt.isBlank()) {
            sql.append(" AND ship_code = ?");
            params.add(shipOpt);
        }
        if (cabinOpt != null && !cabinOpt.isBlank()) {
            sql.append(" AND cabin_class = ?");
            params.add(cabinOpt);
        }

        sql.append(" ORDER BY price_cents ASC LIMIT 200");

        List<SailingRow> out = new ArrayList<>();
        try (var c = db.getConnection(); var ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String vendor = rs.getString(1);
                    String ship = rs.getString(2);
                    LocalDate sail = LocalDate.parse(rs.getString(3));
                    String cabin = rs.getString(4);
                    int nights = rs.getInt(5);
                    long priceCents = rs.getLong(6);
                    String currency = rs.getString(7);

                    out.add(new SailingRow(
                            vendor,
                            ship,
                            sail,
                            cabin,
                            nights,
                            sail.plusDays(nights),
                            new Money(priceCents, currency)
                    ));
                }
            }
        }
        return out;
    }

    public record HistoryRow(Instant retrievedAt, String vendor, String cabinClass, Money price) {}

    public record SailingRow(
            String vendor,
            String shipCode,
            LocalDate sailDate,
            String cabinClass,
            int nights,
            LocalDate debarkDate,
            Money price
    ) {}
}
