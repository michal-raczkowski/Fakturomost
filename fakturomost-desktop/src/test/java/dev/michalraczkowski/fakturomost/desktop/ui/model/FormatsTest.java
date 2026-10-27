package dev.michalraczkowski.fakturomost.desktop.ui.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.michalraczkowski.fakturomost.core.domain.Address;
import dev.michalraczkowski.fakturomost.core.domain.CurrencyCode;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import dev.michalraczkowski.fakturomost.core.domain.VatBucket;
import dev.michalraczkowski.fakturomost.core.domain.VatRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class FormatsTest {

    @Test
    void amountsKeepEveryWrittenDecimalAndPadToTwo() {
        assertThat(Formats.amount(new BigDecimal("5460.00"))).isEqualTo("5 460,00");
        assertThat(Formats.amount(new BigDecimal("2051"))).isEqualTo("2 051,00");
        assertThat(Formats.amount(new BigDecimal("1666.66"))).isEqualTo("1 666,66");
        assertThat(Formats.amount(new BigDecimal("1234567.5"))).isEqualTo("1 234 567,50");
        assertThat(Formats.amount(new BigDecimal("0.123456"))).isEqualTo("0,123456");
        assertThat(Formats.amount(new BigDecimal("-492.00"))).isEqualTo("-492,00");
        assertThat(Formats.amount(new BigDecimal("-50000"))).isEqualTo("-50 000,00");
        assertThat(Formats.amount(null)).isEmpty();
    }

    @Test
    void quantitiesAndUnitPricesShowExactlyTheWrittenDecimals() {
        assertThat(Formats.number(new BigDecimal("3"))).isEqualTo("3");
        assertThat(Formats.number(new BigDecimal("4000.00"))).isEqualTo("4 000,00");
        assertThat(Formats.number(new BigDecimal("1.500"))).isEqualTo("1,500");
        assertThat(Formats.number(new BigDecimal("0.12345678"))).isEqualTo("0,12345678");
        assertThat(Formats.number(new BigDecimal("1E+3"))).isEqualTo("1 000");
    }

    @Test
    void thousandsSeparatorIsAPlainSpace() {
        // The JDK's Polish locale uses U+00A0; a plain space pastes the same everywhere.
        assertThat(Formats.amount(new BigDecimal("1000"))).isEqualTo("1" + ' ' + "000,00").doesNotContain(" ");
    }

    @Test
    void formattingDoesNotDependOnTheDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            for (Locale locale : new Locale[] {Locale.US, Locale.GERMANY, Locale.forLanguageTag("ar-EG"),
                    Locale.forLanguageTag("hi-IN-u-nu-deva")}) {
                Locale.setDefault(locale);
                assertThat(Formats.amount(new BigDecimal("-1234.5"))).as(locale.toString()).isEqualTo("-1 234,50");
                assertThat(Formats.date(LocalDate.of(2026, 10, 2))).as(locale.toString()).isEqualTo("2026-10-02");
            }
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void moneyAppendsTheCurrencyCode() {
        assertThat(Formats.money(new BigDecimal("2500.00"), new CurrencyCode("EUR"))).isEqualTo("2 500,00 EUR");
    }

    @Test
    void sizes() {
        assertThat(Formats.size(812)).isEqualTo("812 B");
        assertThat(Formats.size(2150)).isEqualTo("2,1 kB");
        assertThat(Formats.size(3 * 1024 * 1024)).isEqualTo("3,0 MB");
    }

    @Test
    void taxIdentifiersNameTheirKind() {
        assertThat(Formats.taxId(new TaxIdentifier.Nip("9999999999"))).isEqualTo("NIP 9999999999");
        assertThat(Formats.taxId(new TaxIdentifier.EuVatNumber("DE", "000000000"))).isEqualTo("VAT EU DE000000000");
        assertThat(Formats.taxId(new TaxIdentifier.OtherTaxId("US", "123"))).isEqualTo("Tax ID US 123");
        assertThat(Formats.taxId(new TaxIdentifier.None())).isEqualTo("no tax ID");
    }

    @Test
    void vatRatesShowPercentOnlyForNumericCodes() {
        assertThat(Formats.vatRate(VatRate.RATE_23)).isEqualTo("23%");
        assertThat(Formats.vatRate(VatRate.ZERO_INTRA_EU)).isEqualTo("0 WDT");
        assertThat(Formats.vatRate(VatRate.EXEMPT)).isEqualTo("zw");
        assertThat(Formats.vatRate(VatRate.NOT_SUBJECT_ABROAD)).isEqualTo("np I");
        assertThat(Formats.vatRate(null)).isEmpty();
    }

    @Test
    void everyTypeAndBucketHasALabelNamingItsFa3Code() {
        for (InvoiceType type : InvoiceType.values()) {
            assertThat(Formats.type(type)).contains(type.code());
        }
        for (VatBucket bucket : VatBucket.values()) {
            assertThat(Formats.vatBucket(bucket)).contains("(P_13_");
        }
    }

    @Test
    void partiesWithoutNameOrAddressGetPlaceholders() {
        assertThat(Formats.name(new Party(new TaxIdentifier.None(), null, null))).isEqualTo("(no name)");
        assertThat(Formats.address(null)).isEmpty();
        assertThat(Formats.address(new Address("PL", "ul. A 1", null))).containsExactly("ul. A 1", "PL");
        assertThat(Formats.address(new Address("PL", "ul. A 1", "00-001 W"))).containsExactly("ul. A 1", "00-001 W", "PL");
    }

    @Test
    void singleLineReplacesControlCharacters() {
        assertThat(Formats.singleLine("a\r\nb\u001b[31m")).isEqualTo("a  b [31m");
        assertThat(Formats.singleLine(null)).isEmpty();
    }
}
