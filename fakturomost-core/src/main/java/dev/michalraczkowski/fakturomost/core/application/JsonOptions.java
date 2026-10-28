package dev.michalraczkowski.fakturomost.core.application;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JSON export with chosen fields: one flat object per invoice holding only the given columns, in
 * the given order, under the given keys. For the complete invoice structure (line items, VAT
 * buckets, addresses as objects) use {@link InvoiceExports#writeJson(List, java.io.OutputStream)}
 * instead. Immutable.
 *
 * <pre>{@code
 * {"format": "fakturomost-invoice-fields", "formatVersion": 1,
 *  "invoices": [ {"Data21": "2026-10-02", "Brutto": 5460.00}, ... ]}
 * }</pre>
 *
 * <p>Values: amounts are JSON numbers with the scale as written, dates {@code yyyy-MM-dd} strings,
 * text strings; a value the invoice does not state is {@code null}, so every object has every key.
 *
 * @param columns columns in output order; at least one, no duplicates
 * @param keys    key for every chosen column; not blank, all different
 */
public record JsonOptions(List<ExportColumn> columns, Map<ExportColumn, String> keys) {

    public JsonOptions {
        columns = List.copyOf(columns);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one column is required");
        }
        if (new HashSet<>(columns).size() != columns.size()) {
            throw new IllegalArgumentException("A column may appear only once");
        }
        Map<ExportColumn, String> chosen = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (ExportColumn column : columns) {
            String key = keys.get(column);
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Missing key for " + column);
            }
            if (!used.add(key)) {
                throw new IllegalArgumentException("Key '" + key + "' is used for more than one column");
            }
            chosen.put(column, key);
        }
        keys = Collections.unmodifiableMap(chosen);
    }

    /** The given columns under their {@linkplain ExportColumn#englishHeader() English keys}. */
    public static JsonOptions of(List<ExportColumn> columns) {
        Map<ExportColumn, String> keys = new LinkedHashMap<>();
        for (ExportColumn column : columns) {
            keys.put(column, column.englishHeader());
        }
        return new JsonOptions(columns, keys);
    }

    public static JsonOptions of(ExportColumn... columns) {
        return of(Arrays.asList(columns));
    }

    /** Default: the {@linkplain ExportColumn#defaults() default columns} with English keys. */
    public static JsonOptions defaults() {
        return of(ExportColumn.defaults());
    }

    /** Replaces the key of one chosen column. */
    public JsonOptions withKey(ExportColumn column, String key) {
        Objects.requireNonNull(column, "column");
        if (!keys.containsKey(column)) {
            throw new IllegalArgumentException(column + " is not one of the chosen columns");
        }
        Map<ExportColumn, String> changed = new LinkedHashMap<>(keys);
        changed.put(column, key);
        return new JsonOptions(columns, changed);
    }
}
