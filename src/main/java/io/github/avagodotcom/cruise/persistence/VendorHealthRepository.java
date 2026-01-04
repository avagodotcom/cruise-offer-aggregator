package io.github.avagodotcom.cruise.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class VendorHealthRepository {
    private final Db db;

    public VendorHealthRepository(Db db) {
        this.db = db;
    }

    public void markSuccess(String vendor) throws Exception {
        upsert(vendor, Instant.now().toString(), null, null);
    }

    public void markError(String vendor, String msg) throws Exception {
        upsert(vendor, null, Instant.now().toString(), msg);
    }

    private void upsert(String vendor, String successAt, String errorAt, String errorMsg) throws Exception {
        String sql = """
          INSERT INTO vendor_health(vendor, last_success_at, last_error_at, last_error_message)
          VALUES(?,?,?,?)
          ON CONFLICT(vendor) DO UPDATE SET
            last_success_at = COALESCE(excluded.last_success_at, vendor_health.last_success_at),
            last_error_at = COALESCE(excluded.last_error_at, vendor_health.last_error_at),
            last_error_message = COALESCE(excluded.last_error_message, vendor_health.last_error_message)
        """;

        try (var c = db.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, vendor);
            ps.setString(2, successAt);
            ps.setString(3, errorAt);
            ps.setString(4, errorMsg);
            ps.executeUpdate();
        }
    }

    public List<VendorHealthRow> list() throws Exception {
        String sql = "SELECT vendor, last_success_at, last_error_at, last_error_message FROM vendor_health ORDER BY vendor";
        List<VendorHealthRow> out = new ArrayList<>();
        try (var c = db.getConnection(); var st = c.createStatement(); var rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new VendorHealthRow(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4)
                ));
            }
        }
        return out;
    }

    public record VendorHealthRow(String vendor, String lastSuccessAt, String lastErrorAt, String lastErrorMessage) {}
}
