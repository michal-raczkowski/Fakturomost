package dev.michalraczkowski.fakturomost.core.application;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How the CSV export is written. Start from a preset — {@link #excelPl()} (the default) or
 * {@link #standard()} — and adjust with the {@code with...} methods. Immutable.
 *
 * <p>Numbers and dates are never quoted, so the separator may not be a character that can occur
 * in them (digits, {@code -}, the decimal separator), nor {@code "} or a line break. Text cells are
 * quoted when needed and escaped against spreadsheet formula injection whatever the options.
 *
 * @param separator        field separator, e.g. {@code ;}, {@code ,}, {@code |} or tab
 * @param decimalSeparator {@code .} or {@code ,}
 * @param byteOrderMark    write a UTF-8 BOM (Excel needs it to detect UTF-8 and show Polish letters)
 * @param lineEnding       {@code "\r\n"} or {@code "\n"}
 * @param headerRow        write a header row
 * @param columns          columns in output order; at least one, no duplicates
 * @param headers          header text for every column
 */
public record CsvOptions(
        char separator,
        char decimalSeparator,
        boolean byteOrderMark,
        String lineEnding,
        boolean headerRow,
        List<ExportColumn> columns,
        Map<ExportColumn, String> headers) {

    public static final String CRLF = "\r\n";
    public static final String LF = "\n";

    public CsvOptions {
        if (decimalSeparator != '.' && decimalSeparator != ',') {
            throw new IllegalArgumentException("Decimal separator must be '.' or ','");
        }
        if (separator == '"' || separator == '-' || separator == decimalSeparator || Character.isDigit(separator)
                || (Character.isISOControl(separator) && separator != '\t')) {
            throw new IllegalArgumentException("Separator '" + printable(separator) + "' is not allowed"
                    + (separator == decimalSeparator ? " together with the same decimal separator" : ""));
        }
        if (!CRLF.equals(lineEnding) && !LF.equals(lineEnding)) {
            throw new IllegalArgumentException("Line ending must be CRLF or LF");
        }
        columns = List.copyOf(columns);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one column is required");
        }
        if (new HashSet<>(columns).size() != columns.size()) {
            throw new IllegalArgumentException("A column may appear only once");
        }
        Map<ExportColumn, String> complete = new EnumMap<>(ExportColumn.class);
        for (ExportColumn column : ExportColumn.values()) {
            String header = headers.get(column);
            if (header == null || header.isBlank()) {
                throw new IllegalArgumentException("Missing header text for " + column);
            }
            complete.put(column, header);
        }
        headers = Map.copyOf(complete);
    }

    /**
     * Default. Opens correctly by double-click in Excel with Polish regional settings: {@code ;},
     * decimal comma, UTF-8 BOM, CRLF, Polish headers, the {@linkplain ExportColumn#defaults() default columns}.
     */
    public static CsvOptions excelPl() {
        return new CsvOptions(';', ',', true, CRLF, true, ExportColumn.defaults(), polishHeaders());
    }

    /** RFC 4180 style for other software: {@code ,}, decimal point, no BOM, CRLF, Polish headers, default columns. */
    public static CsvOptions standard() {
        return new CsvOptions(',', '.', false, CRLF, true, ExportColumn.defaults(), polishHeaders());
    }

    public CsvOptions withSeparator(char value) {
        return new CsvOptions(value, decimalSeparator, byteOrderMark, lineEnding, headerRow, columns, headers);
    }

    public CsvOptions withDecimalSeparator(char value) {
        return new CsvOptions(separator, value, byteOrderMark, lineEnding, headerRow, columns, headers);
    }

    public CsvOptions withByteOrderMark(boolean value) {
        return new CsvOptions(separator, decimalSeparator, value, lineEnding, headerRow, columns, headers);
    }

    public CsvOptions withLineEnding(String value) {
        return new CsvOptions(separator, decimalSeparator, byteOrderMark, value, headerRow, columns, headers);
    }

    public CsvOptions withHeaderRow(boolean value) {
        return new CsvOptions(separator, decimalSeparator, byteOrderMark, lineEnding, value, columns, headers);
    }

    public CsvOptions withColumns(List<ExportColumn> value) {
        return new CsvOptions(separator, decimalSeparator, byteOrderMark, lineEnding, headerRow, value, headers);
    }

    public CsvOptions withColumns(ExportColumn... value) {
        return withColumns(Arrays.asList(value));
    }

    /** Replaces the header text of one column; the others keep theirs. */
    public CsvOptions withHeader(ExportColumn column, String text) {
        Objects.requireNonNull(column, "column");
        Map<ExportColumn, String> changed = new EnumMap<>(headers);
        changed.put(column, text);
        return new CsvOptions(separator, decimalSeparator, byteOrderMark, lineEnding, headerRow, columns, changed);
    }

    /** Replaces the header text of the given columns; columns not in {@code texts} keep theirs. */
    public CsvOptions withHeaders(Map<ExportColumn, String> texts) {
        Map<ExportColumn, String> changed = new EnumMap<>(headers);
        changed.putAll(texts);
        return new CsvOptions(separator, decimalSeparator, byteOrderMark, lineEnding, headerRow, columns, changed);
    }

    /** All headers back to the Polish defaults. */
    public CsvOptions withPolishHeaders() {
        return new CsvOptions(separator, decimalSeparator, byteOrderMark, lineEnding, headerRow, columns, polishHeaders());
    }

    /** All headers to the English, machine-friendly names (same keys as the JSON export). */
    public CsvOptions withEnglishHeaders() {
        Map<ExportColumn, String> english = new EnumMap<>(ExportColumn.class);
        for (ExportColumn column : ExportColumn.values()) {
            english.put(column, column.englishHeader());
        }
        return new CsvOptions(separator, decimalSeparator, byteOrderMark, lineEnding, headerRow, columns, english);
    }

    private static Map<ExportColumn, String> polishHeaders() {
        Map<ExportColumn, String> polish = new EnumMap<>(ExportColumn.class);
        for (ExportColumn column : ExportColumn.values()) {
            polish.put(column, column.polishHeader());
        }
        return polish;
    }

    private static String printable(char c) {
        return Character.isISOControl(c) ? String.format("\\u%04x", (int) c) : String.valueOf(c);
    }
}
