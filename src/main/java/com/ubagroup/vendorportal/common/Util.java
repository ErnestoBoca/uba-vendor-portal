package com.ubagroup.vendorportal.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Util {
    private Util() { }

    /** p("a", 1, "b", null) -> mapa ordenado que aceita null. */
    public static Map<String, Object> p(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    public static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    public static boolean bool(Object o) {
        return o instanceof Boolean b ? b : o instanceof Number n && n.intValue() != 0;
    }

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public static String nullIfBlank(String s) {
        return blank(s) ? null : s.trim();
    }
}
