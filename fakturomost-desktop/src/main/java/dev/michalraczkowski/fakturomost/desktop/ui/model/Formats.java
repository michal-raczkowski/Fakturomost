package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.domain.Address;
import dev.michalraczkowski.fakturomost.core.domain.CurrencyCode;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import dev.michalraczkowski.fakturomost.core.domain.VatBucket;
import dev.michalraczkowski.fakturomost.core.domain.VatRate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How values are shown in the desktop UI. Everything is pinned here instead of taken from the
 * machine's defaults (lesson from M3: a Windows runner guessed differently than Linux):
 * <ul>
 *   <li>Amounts use Polish notation — decimal comma, a space between thousands — because the
 *       invoices are Polish and so are the spreadsheets they end up in. The thousands space is a
 *       plain U+0020, not the no-break space the JDK's Polish locale uses, so a copied value pastes
 *       the same everywhere.</li>
 *   <li>Amounts are never rounded: all decimals written in the invoice are shown, padded to at least
 *       two for totals.</li>
 *   <li>Dates are ISO ({@code 2026-10-02}): unambiguous, and they sort correctly as text.</li>
 * </ul>
 * UI labels are English for now (like the CLI); Polish labels are an open decision.
 */
public final class Formats {

    /** The locale every number in the UI is formatted with. */
    public static final Locale NUMBER_LOCALE = Locale.forLanguageTag("pl-PL");

    private Formats() {
    }

    /** An amount of money: at least two decimals, never rounded, e.g. {@code 5 460,00}, {@code -492,00}. */
    public static String amount(BigDecimal value) {
        return value == null ? "" : decimal(value, 2);
    }

    /** {@code 5 460,00 PLN}. */
    public static String money(BigDecimal value, CurrencyCode currency) {
        return value == null ? "" : amount(value) + " " + currency.value();
    }

    /** A quantity or unit price: exactly the decimals written, e.g. {@code 3}, {@code 1,50}, {@code 0,12345678}. */
    public static String number(BigDecimal value) {
        return value == null ? "" : decimal(value, 0);
    }

    public static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    /** {@code 4,2 kB}; bytes below 1 kB as {@code 812 B}. Sizes only, so rounding is fine here. */
    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        BigDecimal kib = BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(1024), 1, RoundingMode.HALF_UP);
        if (kib.compareTo(BigDecimal.valueOf(1024)) < 0) {
            return decimal(kib, 1) + " kB";
        }
        BigDecimal mib = BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(1024L * 1024), 1, RoundingMode.HALF_UP);
        return decimal(mib, 1) + " MB";
    }

    /** {@code NIP 9999999999}, {@code VAT EU DE000000000}, {@code Tax ID US 123}, or {@code no tax ID}. */
    public static String taxId(TaxIdentifier id) {
        return switch (id) {
            case TaxIdentifier.Nip nip -> "NIP " + nip.value();
            case TaxIdentifier.EuVatNumber eu -> "VAT EU " + eu.display();
            case TaxIdentifier.OtherTaxId other -> "Tax ID " + other.display();
            case TaxIdentifier.None none -> "no tax ID";
        };
    }

    /** The party's name, or a placeholder (the buyer's name is optional on simplified invoices). */
    public static String name(Party party) {
        return party.name() == null || party.name().isBlank() ? "(no name)" : party.name();
    }

    /** Address lines as written, with the country code on the last line. Empty if there is no address. */
    public static List<String> address(Address address) {
        if (address == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add(address.line1());
        if (address.line2() != null && !address.line2().isBlank()) {
            lines.add(address.line2());
        }
        lines.add(address.countryCode());
        return List.copyOf(lines);
    }

    /** {@code VAT}, {@code Correction (KOR)}, ... — the FA(3) code always included. */
    public static String type(InvoiceType type) {
        return switch (type) {
            case VAT -> "VAT";
            case CORRECTION -> "Correction (KOR)";
            case ADVANCE -> "Advance (ZAL)";
            case SETTLEMENT -> "Settlement (ROZ)";
            case SIMPLIFIED -> "Simplified (UPR)";
            case ADVANCE_CORRECTION -> "Advance correction (KOR_ZAL)";
            case SETTLEMENT_CORRECTION -> "Settlement correction (KOR_ROZ)";
        };
    }

    /** Numeric rates as {@code 23%}; codes ({@code zw}, {@code 0 WDT}, {@code np I}, ...) as written. */
    public static String vatRate(VatRate rate) {
        if (rate == null) {
            return "";
        }
        return rate.code().chars().allMatch(Character::isDigit) ? rate.code() + "%" : rate.code();
    }

    /** What a tax bucket ({@code P_13_x}) means, with the FA(3) field for reference. */
    public static String vatBucket(VatBucket bucket) {
        return switch (bucket) {
            case BASIC_RATE -> "Basic rate 23% / 22% (P_13_1)";
            case FIRST_REDUCED_RATE -> "Reduced rate 8% / 7% (P_13_2)";
            case SECOND_REDUCED_RATE -> "Reduced rate 5% (P_13_3)";
            case TAXI_FLAT_RATE -> "Taxi flat rate (P_13_4)";
            case OSS_PROCEDURE -> "OSS procedure (P_13_5)";
            case ZERO_DOMESTIC -> "0% domestic (P_13_6_1)";
            case ZERO_INTRA_EU -> "0% intra-EU supply, WDT (P_13_6_2)";
            case ZERO_EXPORT -> "0% export (P_13_6_3)";
            case EXEMPT -> "Exempt, zw (P_13_7)";
            case OUTSIDE_COUNTRY -> "Outside the country, np I (P_13_8)";
            case ART_100_SERVICES -> "Art. 100 services, np II (P_13_9)";
            case REVERSE_CHARGE -> "Reverse charge, oo (P_13_10)";
            case MARGIN -> "Margin scheme (P_13_11)";
        };
    }

    /** Replaces control characters, like core does for source names, so text cannot break a line. */
    public static String singleLine(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(c -> result.appendCodePoint(Character.isISOControl(c) ? ' ' : c));
        return result.toString();
    }

    private static String decimal(BigDecimal value, int minimumDecimals) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(NUMBER_LOCALE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('-');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        format.setGroupingSize(3);
        // Exactly the decimals written (trailing zeros included: 4000.00 stays 4 000,00), padded to the minimum.
        int decimals = Math.max(minimumDecimals, value.scale());
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        format.setRoundingMode(RoundingMode.UNNECESSARY); // never round: would throw instead
        return format.format(value);
    }
}
