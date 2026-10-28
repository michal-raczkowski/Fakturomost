package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.CsvOptions;
import dev.michalraczkowski.fakturomost.core.application.ExportColumn;
import dev.michalraczkowski.fakturomost.core.application.JsonOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Which invoice fields an export writes, in which order, under which column names. Immutable; every
 * change returns a new layout. A renamed column still carries the same field's values.
 *
 * <p>Kept free of JavaFX: the export dialog shows {@link #columns()} as "Exported columns" and
 * {@link #available()} as "Available fields".
 *
 * @param names   which default names new and reset columns get
 * @param columns the exported columns in output order; each field at most once
 */
public record ColumnLayout(Names names, List<Column> columns) {

    /** Longest column name accepted; real import templates stay far below this. */
    public static final int MAX_NAME_LENGTH = 100;

    /** Default column names: Polish (CSV default) or the English machine-friendly keys (JSON default). */
    public enum Names {
        POLISH("Polish"),
        ENGLISH("English (machine-friendly)");

        private final String label;

        Names(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String of(ExportColumn field) {
            return this == POLISH ? field.polishHeader() : field.englishHeader();
        }
    }

    /**
     * One exported column.
     *
     * @param field the invoice value written in this column
     * @param name  the column's header (CSV) or key (JSON), as typed; trimmed when exporting
     */
    public record Column(ExportColumn field, String name) {
        public Column {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(name, "name");
        }

        /** Whether the name differs from both default names of the field. */
        public boolean renamed() {
            String trimmed = name.strip();
            return !trimmed.equals(field.polishHeader()) && !trimmed.equals(field.englishHeader());
        }
    }

    public ColumnLayout {
        Objects.requireNonNull(names, "names");
        columns = List.copyOf(columns);
        Set<ExportColumn> seen = EnumSet.noneOf(ExportColumn.class);
        for (Column column : columns) {
            if (!seen.add(column.field())) {
                throw new IllegalArgumentException("A field may appear only once: " + column.field());
            }
        }
    }

    /** The columns the presets write, in their order, with default names. */
    public static ColumnLayout defaults(Names names) {
        return new ColumnLayout(names, ExportColumn.defaults().stream().map(f -> new Column(f, names.of(f))).toList());
    }

    /** Fields not exported yet, in their natural order (the order of {@link ExportColumn}). */
    public List<ExportColumn> available() {
        Set<ExportColumn> used = EnumSet.noneOf(ExportColumn.class);
        columns.forEach(c -> used.add(c.field()));
        return Arrays.stream(ExportColumn.values()).filter(f -> !used.contains(f)).toList();
    }

    public List<ExportColumn> fields() {
        return columns.stream().map(Column::field).toList();
    }

    public int indexOf(ExportColumn field) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).field() == field) {
                return i;
            }
        }
        return -1;
    }

    // --- Changes ---------------------------------------------------------------------------------

    /**
     * Adds fields with their default names, after position {@code afterIndex} (−1 or out of range:
     * at the end). Fields already exported are skipped.
     */
    public ColumnLayout add(List<ExportColumn> fields, int afterIndex) {
        List<Column> changed = new ArrayList<>(columns);
        int at = afterIndex >= 0 && afterIndex < columns.size() ? afterIndex + 1 : columns.size();
        for (ExportColumn field : fields) {
            if (indexOf(field) < 0 && changed.stream().noneMatch(c -> c.field() == field)) {
                changed.add(at++, new Column(field, names.of(field)));
            }
        }
        return new ColumnLayout(names, changed);
    }

    public ColumnLayout remove(List<ExportColumn> fields) {
        return new ColumnLayout(names, columns.stream().filter(c -> !fields.contains(c.field())).toList());
    }

    /** Every field, in natural order; columns already exported keep their place and name. */
    public ColumnLayout addAll() {
        return add(available(), -1);
    }

    public ColumnLayout removeAll() {
        return new ColumnLayout(names, List.of());
    }

    /**
     * Moves the column at {@code index} by {@code delta} places (negative: up); stops at the ends.
     * Returns this layout unchanged if {@code index} is out of range.
     */
    public ColumnLayout move(int index, int delta) {
        if (index < 0 || index >= columns.size()) {
            return this;
        }
        int target = Math.max(0, Math.min(columns.size() - 1, index + delta));
        List<Column> changed = new ArrayList<>(columns);
        changed.add(target, changed.remove(index));
        return new ColumnLayout(names, changed);
    }

    public ColumnLayout rename(int index, String name) {
        List<Column> changed = new ArrayList<>(columns);
        changed.set(index, new Column(columns.get(index).field(), name == null ? "" : name));
        return new ColumnLayout(names, changed);
    }

    /**
     * Switches the default names. Columns still carrying a default name get the new default; columns
     * the user renamed keep their name.
     */
    public ColumnLayout withNames(Names value) {
        return new ColumnLayout(value, columns.stream()
                .map(c -> c.renamed() ? c : new Column(c.field(), value.of(c.field())))
                .toList());
    }

    /** Every column back to its default name; order and choice stay. */
    public ColumnLayout resetNames() {
        return new ColumnLayout(names, columns.stream().map(c -> new Column(c.field(), names.of(c.field()))).toList());
    }

    // --- Checks and use --------------------------------------------------------------------------

    /**
     * What stops this layout from being exported, in display order; empty when it is fine. Names are
     * compared trimmed and case-insensitively: {@code Data} and {@code data } count as the same name,
     * which a spreadsheet or a JSON reader would confuse.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (columns.isEmpty()) {
            problems.add("Choose at least one column to export.");
            return problems;
        }
        Map<String, Integer> firstUse = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            String name = column.name().strip();
            String where = "Column " + (i + 1) + " (" + ExportFields.label(column.field()) + ")";
            if (name.isEmpty()) {
                problems.add(where + " has no name.");
                continue;
            }
            if (name.codePoints().anyMatch(Character::isISOControl)) {
                problems.add(where + ": names cannot contain line breaks or tabs.");
            }
            if (name.length() > MAX_NAME_LENGTH) {
                problems.add(where + ": name is longer than " + MAX_NAME_LENGTH + " characters.");
            }
            Integer earlier = firstUse.putIfAbsent(name.toLowerCase(Locale.ROOT), i);
            if (earlier != null) {
                problems.add("Columns " + (earlier + 1) + " and " + (i + 1) + " have the same name \""
                        + Formats.singleLine(name) + "\".");
            }
        }
        return problems;
    }

    public boolean isValid() {
        return problems().isEmpty();
    }

    /** The CSV options of {@code settings} with these columns and names. */
    public CsvOptions toCsv(CsvOptions settings) {
        requireValid();
        Map<ExportColumn, String> headers = new EnumMap<>(ExportColumn.class);
        columns.forEach(c -> headers.put(c.field(), c.name().strip()));
        return settings.withColumns(fields()).withHeaders(headers);
    }

    public JsonOptions toJson() {
        requireValid();
        Map<ExportColumn, String> keys = new EnumMap<>(ExportColumn.class);
        columns.forEach(c -> keys.put(c.field(), c.name().strip()));
        return new JsonOptions(fields(), keys);
    }

    private void requireValid() {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException(problems.getFirst());
        }
    }
}
