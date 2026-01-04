package io.github.avagodotcom.cruise.cli;

import java.util.HashMap;
import java.util.Map;

public class ArgMap {
    private final Map<String, String> map = new HashMap<>();

    public static ArgMap parse(String[] args) {
        ArgMap am = new ArgMap();
        for (int i = 0; i < args.length; i++) {
            String k = args[i];
            if (!k.startsWith("--")) continue;
            if (i + 1 >= args.length) break;

            String v = args[i + 1];
            if (v.startsWith("--")) continue;

            am.map.put(k, v);
            i++;
        }
        return am;
    }

    public String req(String key) {
        String v = map.get(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing required arg: " + key);
        return v.trim();
    }

    public int reqInt(String key) {
        return Integer.parseInt(req(key));
    }

    public String opt(String key) {
        String v = map.get(key);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    public Integer optInt(String key) {
        String v = opt(key);
        return (v == null) ? null : Integer.parseInt(v);
    }
}
