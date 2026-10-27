package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the user typed into the filter bar, and how it becomes an {@link InvoiceQuery}. Kept free
 * of JavaFX so the parsing rules are unit-tested. Every field is optional; blank = not applied.
 *
 * <p>Accepted input, pinned (not taken from the OS locale):
 * <ul>
 *   <li>Dates: {@code 2026-10-02} (as shown in the table) or the Polish {@code 02.10.2026}.</li>
 *   <li>Amounts: {@code 1234.50}, {@code 1234,50}, {@code 1 234,50} (spaces, also no-break spaces,
 *       as thousands separators), {@code -492}. Both {@code ,} and {@code .} in one value is
 *       rejected rather than guessed.</li>
 *   <li>Tax ID: the full NIP or EU VAT number in any notation ({@code 999-999-99-99},
 *       {@code PL9999999999}) — core matches it exactly against seller or buyer.</li>
 * </ul>
 *
 * @param text       part of the invoice number, seller/buyer name or KSeF number
 * @param taxId      full NIP / VAT ID of seller or buyer
 * @param issuedFrom earliest issue date, inclusive
 * @param issuedTo   latest issue date, inclusive
 * @param grossFrom  smallest gross amount, inclusive
 * @param grossTo    largest gross amount, inclusive
 * @param type       invoice type, or {@code null} for all
 */
public record FilterInput(String text, String taxId, String issuedFrom, String issuedTo, String grossFrom,
                          String grossTo, InvoiceType type) {

    /** Which input a problem is about, so the UI can mark that field. */
    public enum Field { TEXT, TAX_ID, ISSUED_FROM, ISSUED_TO, GROSS_FROM, GROSS_TO, TYPE }

    public record Problem(Field field, String message) {
        public Problem {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(message, "message");
        }
    }

    /** Either a query to run or the problems to show; never both. */
    public sealed interface Result {
        record Valid(InvoiceQuery query) implements Result {
        }

        record Invalid(List<Problem> problems) implements Result {
            public Invalid {
                problems = List.copyOf(problems);
            }
        }
    }

    public static final FilterInput EMPTY = new FilterInput("", "", "", "", "", "", null);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter POLISH = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u202F]");
    private static final Pattern AMOUNT = Pattern.compile("-?\\d+(\\.\\d+)?");

    public FilterInput {
        text = nullToEmpty(text);
        taxId = nullToEmpty(taxId);
        issuedFrom = nullToEmpty(issuedFrom);
        issuedTo = nullToEmpty(issuedTo);
        grossFrom = nullToEmpty(grossFrom);
        grossTo = nullToEmpty(grossTo);
    }

    public boolean isEmpty() {
        return equals(EMPTY);
    }

    public Result toQuery() {
        List<Problem> problems = new ArrayList<>();
        LocalDate from = date(issuedFrom, Field.ISSUED_FROM, problems);
        LocalDate to = date(issuedTo, Field.ISSUED_TO, problems);
        BigDecimal min = amount(grossFrom, Field.GROSS_FROM, problems);
        BigDecimal max = amount(grossTo, Field.GROSS_TO, problems);
        if (from != null && to != null && from.isAfter(to)) {
            problems.add(new Problem(Field.ISSUED_TO, "The end date is before the start date."));
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            problems.add(new Problem(Field.GROSS_TO, "The largest amount is smaller than the smallest."));
        }
        if (!taxId.isBlank() && taxId.chars().noneMatch(Character::isLetterOrDigit)) {
            problems.add(new Problem(Field.TAX_ID, "Enter a NIP or VAT number."));
        }
        if (!problems.isEmpty()) {
            return new Result.Invalid(problems);
        }
        return new Result.Valid(InvoiceQuery.all()
                .withText(text)
                .withTaxId(taxId)
                .withIssuedBetween(from, to)
                .withGrossBetween(min, max)
                .withTypes(type == null ? Set.of() : Set.of(type)));
    }

    /** {@code 7 invoices} without a filter, {@code 3 of 7 invoices match} with one. */
    public static String countText(int shown, int total, boolean filtered) {
        String invoices = total == 1 ? " invoice" : " invoices";
        return filtered ? shown + " of " + total + invoices + " match" : total + invoices;
    }

    private static LocalDate date(String value, Field field, List<Problem> problems) {
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : List.of(ISO, POLISH)) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        problems.add(new Problem(field, "Use a date like 2026-10-02 or 02.10.2026."));
        return null;
    }

    private static BigDecimal amount(String value, Field field, List<Problem> problems) {
        String compact = SPACES.matcher(value).replaceAll("");
        if (compact.isEmpty()) {
            return null;
        }
        if (compact.contains(",") && compact.contains(".")) {
            problems.add(new Problem(field, "Use one decimal separator, e.g. 1234,50."));
            return null;
        }
        String normalized = compact.replace(',', '.');
        if (!AMOUNT.matcher(normalized).matches()) {
            problems.add(new Problem(field, "Use an amount like 1234,50."));
            return null;
        }
        return new BigDecimal(normalized);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
