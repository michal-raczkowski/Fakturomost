package dev.michalraczkowski.fakturomost.core.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One tax bucket as stated on the invoice. Amounts are copied as written: never recomputed,
 * rounded or checked against the line items or the gross total.
 *
 * @param bucket   which {@code P_13_x} group this is
 * @param net      net amount ({@code P_13_x}); negative on corrections
 * @param vat      VAT in the invoice currency ({@code P_14_x}), or {@code null} for buckets that
 *                 carry no VAT field (0%, exempt, margin, ...) or when the optional OSS VAT is absent
 * @param vatInPln VAT converted to PLN ({@code P_14_xW}), or {@code null} when not given
 */
public record VatBreakdown(VatBucket bucket, BigDecimal net, BigDecimal vat, BigDecimal vatInPln) {

    public VatBreakdown {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(net, "net");
    }
}
