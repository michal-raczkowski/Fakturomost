package dev.michalraczkowski.fakturomost.core.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Reference from a corrective invoice to an invoice it corrects ({@code DaneFaKorygowanej}).
 *
 * @param issueDate  {@code DataWystFaKorygowanej}
 * @param number     {@code NrFaKorygowanej}
 * @param ksefNumber {@code NrKSeFFaKorygowanej}, or {@code null} when the corrected invoice was
 *                   issued outside KSeF
 */
public record CorrectedInvoice(LocalDate issueDate, String number, String ksefNumber) {

    public CorrectedInvoice {
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(number, "number");
    }
}
