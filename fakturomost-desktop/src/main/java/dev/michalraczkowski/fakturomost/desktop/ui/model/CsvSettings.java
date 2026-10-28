package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.CsvOptions;
import dev.michalraczkowski.fakturomost.core.application.ExportColumn;
import java.util.List;
import java.util.Optional;

/**
 * The CSV file settings the export dialog lets the user change. Converted to core's
 * {@link CsvOptions} together with a {@link ColumnLayout}; core decides what is allowed.
 *
 * @param separator        field separator
 * @param decimalSeparator {@code ,} or {@code .}
 * @param byteOrderMark    UTF-8 BOM (Excel needs it to show Polish letters)
 * @param headerRow        write the column names as the first row
 * @param crlf             Windows line endings ({@code false}: LF)
 */
public record CsvSettings(char separator, char decimalSeparator, boolean byteOrderMark, boolean headerRow,
                          boolean crlf) {

    /** A separator the dialog offers. */
    public record Choice(char value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public static final List<Choice> SEPARATORS = List.of(
            new Choice(';', "Semicolon  ;"),
            new Choice(',', "Comma  ,"),
            new Choice('\t', "Tab"),
            new Choice('|', "Vertical bar  |"));

    public static final List<Choice> DECIMAL_SEPARATORS = List.of(
            new Choice(',', "Comma  1234,50"),
            new Choice('.', "Point  1234.50"));

    public static CsvSettings of(CsvOptions options) {
        return new CsvSettings(options.separator(), options.decimalSeparator(), options.byteOrderMark(),
                options.headerRow(), CsvOptions.CRLF.equals(options.lineEnding()));
    }

    /** These settings with the default columns; throws {@link IllegalArgumentException} if core refuses them. */
    public CsvOptions toOptions() {
        return CsvOptions.excelPl()
                .withDecimalSeparator('.').withSeparator(';') // neutral start: every change below is legal alone
                .withDecimalSeparator(decimalSeparator)
                .withSeparator(separator)
                .withByteOrderMark(byteOrderMark)
                .withHeaderRow(headerRow)
                .withLineEnding(crlf ? CsvOptions.CRLF : CsvOptions.LF)
                .withColumns(ExportColumn.defaults());
    }

    /** Why core would refuse these settings, in the dialog's words; empty when they are fine. */
    public Optional<String> problem() {
        if (separator == decimalSeparator) {
            return Optional.of("The separator and the decimal sign cannot be the same character; "
                    + "choose a semicolon separator or a decimal point.");
        }
        try {
            toOptions();
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of(e.getMessage() + ".");
        }
    }

    public CsvSettings withSeparator(char value) {
        return new CsvSettings(value, decimalSeparator, byteOrderMark, headerRow, crlf);
    }

    public CsvSettings withDecimalSeparator(char value) {
        return new CsvSettings(separator, value, byteOrderMark, headerRow, crlf);
    }

    public CsvSettings withByteOrderMark(boolean value) {
        return new CsvSettings(separator, decimalSeparator, value, headerRow, crlf);
    }

    public CsvSettings withHeaderRow(boolean value) {
        return new CsvSettings(separator, decimalSeparator, byteOrderMark, value, crlf);
    }

    public CsvSettings withCrlf(boolean value) {
        return new CsvSettings(separator, decimalSeparator, byteOrderMark, headerRow, value);
    }
}
