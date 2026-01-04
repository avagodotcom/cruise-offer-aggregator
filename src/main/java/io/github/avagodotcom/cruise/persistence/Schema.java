package io.github.avagodotcom.cruise.persistence;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Schema {
    private final Db db;

    public Schema(Db db) {
        this.db = db;
    }

    public void init() throws Exception {
        String sql;
        try (var in = getClass().getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IllegalStateException("Missing /schema.sql");
            try (var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                sql = br.lines().reduce("", (a, b) -> a + "\n" + b);
            }
        }

        try (var c = db.getConnection(); var st = c.createStatement()) {
            for (String stmt : sql.split(";")) {
                String s = stmt.trim();
                if (!s.isEmpty()) st.execute(s);
            }
        }
    }
}
