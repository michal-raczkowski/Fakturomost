package dev.michalraczkowski.fakturomost.core.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Reference from a corrective invoice to an invoice it corrects ({@code DaneFaKorygowanej}).
 *
 * @param issueDate  {@code DataWystFaKorygowanej}
 * @param number     {@code NrFaKorygowanej}
 * @param ksefNumber {@code NrKSeFFaKorygowanej} as written, or {@code null} when the corrected
 *                   invoice was issued outside KSeF. Plain text on purpose, not {@link KsefNumber}:
 *                   the schema checks only its pattern, and even the official MF samples contain
 *                   numbers whose checksum does not match
 */
public record CorrectedInvoice(LocalDate issueDate, String number, String ksefNumber) {

    public CorrectedInvoice {
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(number, "number");
    }
}
