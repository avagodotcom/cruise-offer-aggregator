package io.github.avagodotcom.cruise.persistence;

import io.github.avagodotcom.cruise.domain.SearchRequest;

import java.sql.Statement;
import java.time.Instant;

public class RunRepository {
    private final Db db;

    public RunRepository(Db db) {
        this.db = db;
    }

    public long insertRun(SearchRequest req) throws Exception {
        String sql = """
          INSERT INTO runs(requested_at, ship_code, sail_date, adults, children, cabin_class)
          VALUES(?,?,?,?,?,?)
        """;

        try (var c = db.getConnection();
             var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, Instant.now().toString());
            ps.setString(2, req.shipCode());
            ps.setString(3, req.sailDate().toString());
            ps.setInt(4, req.adults());
            ps.setInt(5, req.children());
            ps.setString(6, req.cabinClass());
            ps.executeUpdate();

            try (var rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }

        throw new IllegalStateException("No generated key for run");
    }
}
