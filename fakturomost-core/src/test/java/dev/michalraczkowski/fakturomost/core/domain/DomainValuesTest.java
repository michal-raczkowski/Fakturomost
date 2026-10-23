package dev.michalraczkowski.fakturomost.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainValuesTest {

    @Test
    void currencyCodeAcceptsAnyThreeUpperCaseLetters() {
        assertEquals("PLN", new CurrencyCode("PLN").toCurrency().orElseThrow().getCurrencyCode());
        assertTrue(new CurrencyCode("GGP").toCurrency().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new CurrencyCode("pln"));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyCode("PLNX"));
    }

    @Test
    void codesRoundTrip() {
        for (InvoiceType type : InvoiceType.values()) {
            assertEquals(type, InvoiceType.fromCode(type.code()));
        }
        for (VatRate rate : VatRate.values()) {
            assertEquals(rate, VatRate.fromCode(rate.code()));
        }
        assertEquals(new BigDecimal("23"), VatRate.RATE_23.percent().orElseThrow());
        assertTrue(VatRate.REVERSE_CHARGE.percent().isEmpty());
    }

    @Test
    void invoiceListsAreDefensiveCopies() {
        List<InvoiceItem> items = new ArrayList<>();
        Party party = new Party(new TaxIdentifier.Nip("9999999999"), "X", null);
        Invoice invoice = new Invoice(InvoiceType.VAT, "1", LocalDate.of(2026, 1, 1), null, CurrencyCode.PLN,
                party, party, List.of(), BigDecimal.ZERO, items, null, List.of());

        items.add(new InvoiceItem(1, null, null, null, null, null, null, null, null, null, null, false));

        assertTrue(invoice.items().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> invoice.items().add(null));
    }
}
