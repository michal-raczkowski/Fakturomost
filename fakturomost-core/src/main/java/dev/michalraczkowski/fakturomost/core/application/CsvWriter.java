package dev.michalraczkowski.fakturomost.core.application;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Writes invoices as CSV according to {@link CsvOptions}. */
final class CsvWriter {

    /** Leading characters that make spreadsheets treat a cell as a formula (OWASP CSV injection). */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private CsvWriter() {
    }

    static void write(List<InvoiceEntry> invoices, CsvOptions options, OutputStream out) throws IOException {
        if (options.byteOrderMark()) {
            out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        }
        // Not closed here: the caller owns the stream.
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        if (options.headerRow()) {
            writeRow(writer, options, options.columns().stream()
                    .map(column -> text(options.headers().get(column), options))
                    .toList());
        }
        for (InvoiceEntry invoice : invoices) {
            writeRow(writer, options, options.columns().stream()
                    .map(column -> cell(column, column.valueOf(invoice), options))
                    .toList());
        }
        writer.flush();
    }

    private static void writeRow(Writer writer, CsvOptions options, List<String> cells) throws IOException {
        writer.write(String.join(String.valueOf(options.separator()), cells));
        writer.write(options.lineEnding());
    }

    private static String cell(ExportColumn column, Object value, CsvOptions options) {
        if (value == null) {
            return "";
        }
        return switch (column.kind()) {
            case NUMBER -> number((BigDecimal) value, options);
            case DATE -> ((LocalDate) value).toString(); // ISO yyyy-MM-dd
            case TEXT -> text((String) value, options);
        };
    }

    /** Plain notation, scale as stated, no thousands separator; negative values stay numbers. */
    private static String number(BigDecimal value, CsvOptions options) {
        return value.toPlainString().replace('.', options.decimalSeparator());
    }

    /**
     * Neutralises formula triggers by prefixing an apostrophe (OWASP's recommendation; Excel hides
     * it), then quotes the cell if it contains the separator, a quote or a line break.
     */
    static String text(String value, CsvOptions options) {
        String safe = !value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean needsQuotes = safe.indexOf(options.separator()) >= 0
                || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        return needsQuotes ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
    }
}
