package io.github.avagodotcom.cruise.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaTest {

    @Test
    void initializesSchema(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("test.db");
        Db db = new Db("jdbc:sqlite:" + dbFile.toAbsolutePath());
        new Schema(db).init();

        try (var c = db.getConnection();
             var ps = c.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {

            assertTrue(exists(ps, "runs"));
            assertTrue(exists(ps, "offers"));
            assertTrue(exists(ps, "vendor_health"));
        }
    }

    private boolean exists(java.sql.PreparedStatement ps, String table) throws Exception {
        ps.setString(1, table);
        try (var rs = ps.executeQuery()) {
            return rs.next();
        }
    }
}
