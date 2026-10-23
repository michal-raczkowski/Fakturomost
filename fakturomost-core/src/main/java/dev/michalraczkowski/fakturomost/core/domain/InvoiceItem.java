package dev.michalraczkowski.fakturomost.core.domain;

import java.math.BigDecimal;

/**
 * One line item ({@code FaWiersz}). Almost every field is optional in FA(3), so most components may
 * be {@code null}. Amounts are copied as written.
 *
 * <p>{@code lineNumber} is <strong>not</strong> unique within an invoice: corrections repeat the
 * number for the "before" and "after" rows (see {@code beforeCorrection}).
 *
 * @param lineNumber       {@code NrWierszaFa}
 * @param description      {@code P_7}
 * @param unit             {@code P_8A}; free text (e.g. "szt.", "0")
 * @param quantity         {@code P_8B}
 * @param unitNetPrice     {@code P_9A}
 * @param unitGrossPrice   {@code P_9B}; used instead of net price e.g. on margin invoices
 * @param netAmount        {@code P_11}
 * @param grossAmount      {@code P_11A}; used instead of net amount e.g. on margin invoices
 * @param vatAmount        {@code P_11Vat}
 * @param vatRate          {@code P_12}
 * @param ossVatRatePercent {@code P_12_XII}; VAT rate in percent under the OSS procedure
 * @param beforeCorrection {@code StanPrzed = 1}: this row shows the state before a correction
 */
public record InvoiceItem(
        long lineNumber,
        String description,
        String unit,
        BigDecimal quantity,
        BigDecimal unitNetPrice,
        BigDecimal unitGrossPrice,
        BigDecimal netAmount,
        BigDecimal grossAmount,
        BigDecimal vatAmount,
        VatRate vatRate,
        BigDecimal ossVatRatePercent,
        boolean beforeCorrection) {
}
