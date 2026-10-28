package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Filter for {@link InvoiceCatalog#search}. Every criterion is optional ({@code null} or empty =
 * not applied); all given criteria must match. Build with {@link #all()} and the {@code with...}
 * methods.
 *
 * <p>{@link #matches} is the single definition of the semantics. A store that filters natively
 * (e.g. in SQL) must give the same answers; the simplest correct implementation narrows the
 * candidates cheaply and then applies {@code matches}.
 *
 * @param issuedFrom earliest issue date ({@code P_1}), inclusive
 * @param issuedTo   latest issue date, inclusive
 * @param taxId      seller or buyer tax identifier; separators, spaces and case are ignored, and a
 *                   {@code PL} prefix matches a Polish NIP ({@code PL999-999-99-99} finds
 *                   {@code 9999999999}). Exact match, not substring
 * @param text       case-insensitive substring of the invoice number, seller or buyer name, or
 *                   KSeF number; inner whitespace is collapsed
 * @param grossFrom  smallest gross total ({@code P_15}), inclusive, in the invoice's own currency
 * @param grossTo    largest gross total, inclusive
 * @param types      invoice types to include; empty = all
 */
public record InvoiceQuery(
        LocalDate issuedFrom,
        LocalDate issuedTo,
        String taxId,
        String text,
        BigDecimal grossFrom,
        BigDecimal grossTo,
        Set<InvoiceType> types) {

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public InvoiceQuery {
        if (issuedFrom != null && issuedTo != null && issuedFrom.isAfter(issuedTo)) {
            throw new IllegalArgumentException("issuedFrom is after issuedTo");
        }
        if (grossFrom != null && grossTo != null && grossFrom.compareTo(grossTo) > 0) {
            throw new IllegalArgumentException("grossFrom is greater than grossTo");
        }
        taxId = blankToNull(taxId);
        text = blankToNull(text);
        types = types == null || types.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(types));
    }

    /** Matches every invoice. */
    public static InvoiceQuery all() {
        return new InvoiceQuery(null, null, null, null, null, null, Set.of());
    }

    public InvoiceQuery withIssuedBetween(LocalDate from, LocalDate to) {
        return new InvoiceQuery(from, to, taxId, text, grossFrom, grossTo, types);
    }

    public InvoiceQuery withTaxId(String value) {
        return new InvoiceQuery(issuedFrom, issuedTo, value, text, grossFrom, grossTo, types);
    }

    public InvoiceQuery withText(String value) {
        return new InvoiceQuery(issuedFrom, issuedTo, taxId, value, grossFrom, grossTo, types);
    }

    public InvoiceQuery withGrossBetween(BigDecimal from, BigDecimal to) {
        return new InvoiceQuery(issuedFrom, issuedTo, taxId, text, from, to, types);
    }

    public InvoiceQuery withTypes(Set<InvoiceType> value) {
        return new InvoiceQuery(issuedFrom, issuedTo, taxId, text, grossFrom, grossTo, value);
    }

    public boolean matches(InvoiceEntry entry) {
        Invoice invoice = entry.invoice();
        return (issuedFrom == null || !invoice.issueDate().isBefore(issuedFrom))
                && (issuedTo == null || !invoice.issueDate().isAfter(issuedTo))
                && (grossFrom == null || invoice.grossTotal().compareTo(grossFrom) >= 0)
                && (grossTo == null || invoice.grossTotal().compareTo(grossTo) <= 0)
                && (types.isEmpty() || types.contains(invoice.type()))
                && (taxId == null || matchesTaxId(invoice.seller()) || matchesTaxId(invoice.buyer()))
                && (text == null || matchesText(entry));
    }

    private boolean matchesTaxId(Party party) {
        String wanted = normalizeId(taxId);
        String candidate = normalizeId(party.taxId().display());
        if (candidate.isEmpty()) {
            return false;
        }
        return candidate.equals(wanted)
                || (party.taxId() instanceof TaxIdentifier.Nip && wanted.equals("PL" + candidate));
    }

    private boolean matchesText(InvoiceEntry entry) {
        String wanted = normalizeText(text);
        Invoice invoice = entry.invoice();
        return contains(invoice.number(), wanted)
                || contains(invoice.seller().name(), wanted)
                || contains(invoice.buyer().name(), wanted)
                || (entry.ksefNumber() != null && contains(entry.ksefNumber().value(), wanted));
    }

    private static boolean contains(String haystack, String normalizedNeedle) {
        return haystack != null && normalizeText(haystack).contains(normalizedNeedle);
    }

    private static String normalizeId(String value) {
        return NOT_ALPHANUMERIC.matcher(value.toUpperCase(Locale.ROOT)).replaceAll("");
    }

    private static String normalizeText(String value) {
        return WHITESPACE.matcher(value.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
