package com.ruleforge.datasource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * V5.23 — Variable container passed into and returned from a data source.
 *
 * <p>Mirrors the contract used by {@code lib/ruleforge-decision}'s flow context vars, but
 * kept as a self-contained type so this module has zero dependency on the decision module.
 * Decision callers map their own context vars to/from this type at the call boundary.
 *
 * <h2>Type safety</h2>
 * <p>This is intentionally NOT a {@code Map<String, Object>}. Subclasses of
 * {@link BaseApiDataSource} interact with the data through typed accessors ({@link #getStr},
 * {@link #getInt}, {@link #getBool}, ...). This mirrors the V5.18 decision to drop
 * {@code LazyGeneralEntity} (see CLAUDE.md "决策系统架构" — strong typing at the boundary).
 *
 * <h2>Null semantics</h2>
 * <p>{@code null} is allowed as a value. {@code getStr} / {@code getInt} return {@code null}
 * for missing keys, never throw. Callers must check for {@code null} explicitly.
 *
 * <h2>Thread safety</h2>
 * <p>Not thread-safe. Each {@code fetch()} call receives its own {@code Vars} instance.
 */
public final class Vars {

    private final Map<String, Object> data;

    public Vars() {
        this.data = new LinkedHashMap<>();
    }

    public Vars(Map<String, Object> initial) {
        this.data = new LinkedHashMap<>(initial == null ? Map.of() : initial);
    }

    // ====== put / get generic ======

    public Vars put(String key, Object value) {
        data.put(Objects.requireNonNull(key, "key"), value);
        return this;
    }

    public Vars putAll(Vars other) {
        if (other != null) {
            data.putAll(other.data);
        }
        return this;
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }

    public int size() {
        return data.size();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public Map<String, Object> toMap() {
        return new HashMap<>(data);
    }

    // ====== typed accessors ======

    public String getStr(String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }

    public Integer getInt(String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    public Long getLong(String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    public Double getDouble(String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    public Boolean getBool(String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            if ("true".equalsIgnoreCase(s) || "Y".equalsIgnoreCase(s) || "1".equals(s)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(s) || "N".equalsIgnoreCase(s) || "0".equals(s)) return Boolean.FALSE;
        }
        return null;
    }

    public Object get(String key) {
        return data.get(key);
    }
}
