package dev.michalraczkowski.fakturomost.desktop.ui.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.desktop.DemoData;
import dev.michalraczkowski.fakturomost.desktop.ui.model.FilterInput.Field;
import dev.michalraczkowski.fakturomost.desktop.ui.model.FilterInput.Problem;
import dev.michalraczkowski.fakturomost.desktop.ui.model.FilterInput.Result;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FilterInputTest {

    // --- parsing ----------------------------------------------------------------------------------

    @Test
    void emptyInputMatchesEverything() {
        assertThat(FilterInput.EMPTY.isEmpty()).isTrue();
        assertThat(query(FilterInput.EMPTY)).isEqualTo(InvoiceQuery.all());
        assertThat(new FilterInput(null, null, null, null, null, null, null).isEmpty()).isTrue();
    }

    @Test
    void datesInIsoOrPolishNotation() {
        InvoiceQuery q = query(new FilterInput("", "", "2026-10-02", " 15.10.2026 ", "", "", null));

        assertThat(q.issuedFrom()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(q.issuedTo()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void invalidDatesAreReportedPerField() {
        assertThat(problems(new FilterInput("", "", "2026-02-30", "10/02/2026", "", "", null)))
                .extracting(Problem::field).containsExactly(Field.ISSUED_FROM, Field.ISSUED_TO);
    }

    @Test
    void amountsInPolishOrPlainNotationWithThousandsSpaces() {
        assertThat(query(filter("1 234,50", "")).grossFrom()).isEqualByComparingTo("1234.50");
        assertThat(query(filter("1 234,50", "")).grossFrom()).isEqualByComparingTo("1234.50");
        assertThat(query(filter("1234.5", "")).grossFrom()).isEqualByComparingTo("1234.5");
        assertThat(query(filter("-492", "")).grossFrom()).isEqualByComparingTo("-492");
    }

    @Test
    void ambiguousOrMalformedAmountsAreRejectedNotGuessed() {
        assertThat(problems(filter("1.234,50", ""))).containsExactly(
                new Problem(Field.GROSS_FROM, "Use one decimal separator, e.g. 1234,50."));
        assertThat(problems(filter("", "12a"))).containsExactly(
                new Problem(Field.GROSS_TO, "Use an amount like 1234,50."));
        assertThat(problems(filter("1e3", ""))).extracting(Problem::field).containsExactly(Field.GROSS_FROM);
    }

    @Test
    void reversedRangesAreReportedInsteadOfThrowing() {
        assertThat(problems(new FilterInput("", "", "2026-10-20", "2026-10-01", "", "", null)))
                .extracting(Problem::field).containsExactly(Field.ISSUED_TO);
        assertThat(problems(filter("500", "100"))).extracting(Problem::field).containsExactly(Field.GROSS_TO);
    }

    @Test
    void parsingDoesNotDependOnTheDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            assertThat(query(new FilterInput("", "", "02.10.2026", "", "1 234,50", "", null)).grossFrom())
                    .isEqualByComparingTo("1234.50");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void typeAndTaxIdArePassedThrough() {
        InvoiceQuery q = query(new FilterInput(" FV/2026 ", "PL 999-999-99-99", "", "", "", "", InvoiceType.CORRECTION));

        assertThat(q.types()).isEqualTo(Set.of(InvoiceType.CORRECTION));
        assertThat(q.taxId()).isEqualTo("PL 999-999-99-99");
        assertThat(q.text()).isEqualTo(" FV/2026 ");
        assertThat(problems(new FilterInput("", "--", "", "", "", "", null))).extracting(Problem::field)
                .containsExactly(Field.TAX_ID);
    }

    @Test
    void countText() {
        assertThat(FilterInput.countText(7, 7, false)).isEqualTo("7 invoices");
        assertThat(FilterInput.countText(3, 7, true)).isEqualTo("3 of 7 invoices match");
        assertThat(FilterInput.countText(1, 1, false)).isEqualTo("1 invoice");
    }

    // --- against the demo data, through the real catalog ----------------------------------------

    @Test
    void filtersTheDemoInvoices() {
        DemoData.Services services = DemoData.Services.create();
        services.importDemoFolder();

        assertThat(numbers(services, new FilterInput("kawiarnia", "", "", "", "", "", null)))
                .containsExactly("FK/2026/10/0001", "FV/2026/10/0001");
        assertThat(numbers(services, new FilterInput("", "555-000-00-01", "", "", "", "", null)))
                .containsExactly("FK/2026/10/0001", "FV/2026/10/0001");
        assertThat(numbers(services, new FilterInput("", "", "10.10.2026", "2026-10-16", "", "", null)))
                .containsExactly("S/45/2026", "FV/2026/10/0003", "112/10/2026");
        assertThat(numbers(services, new FilterInput("", "", "", "", "", "0", null)))
                .containsExactly("FK/2026/10/0001");
        assertThat(numbers(services, new FilterInput("", "", "", "", "1 000", "5 000,00", InvoiceType.VAT)))
                .containsExactly("FV/2026/10/0004", "FV/2026/10/0002");
        assertThat(numbers(services, new FilterInput("", "DE000000000", "", "", "", "", null)))
                .containsExactly("FV/2026/10/0004");
    }

    // --- helpers ----------------------------------------------------------------------------------

    private static FilterInput filter(String grossFrom, String grossTo) {
        return new FilterInput("", "", "", "", grossFrom, grossTo, null);
    }

    private static InvoiceQuery query(FilterInput input) {
        Result result = input.toQuery();
        assertThat(result).isInstanceOf(Result.Valid.class);
        return ((Result.Valid) result).query();
    }

    private static List<Problem> problems(FilterInput input) {
        Result result = input.toQuery();
        assertThat(result).isInstanceOf(Result.Invalid.class);
        return ((Result.Invalid) result).problems();
    }

    private static List<String> numbers(DemoData.Services services, FilterInput input) {
        return services.catalog().search(query(input)).stream()
                .map(InvoiceEntry::invoice).map(i -> i.number()).toList();
    }
}
