package dev.michalraczkowski.fakturomost.core.fa3;

import java.nio.charset.StandardCharsets;

/**
 * Builds small, schema-shaped FA(3) documents for tests. Defaults form a minimal valid invoice;
 * each setter replaces one fragment. All data is fictitious.
 */
final class TestInvoiceXml {

    static final String NS = "http://crd.gov.pl/wzor/2025/06/25/13775/";

    private String rootOpen = "<Faktura xmlns=\"" + NS + "\">";
    private String formCode = "<KodFormularza kodSystemowy=\"FA (3)\" wersjaSchemy=\"1-0E\">FA</KodFormularza>";
    private String buyerIdentification = "<NIP>2222222222</NIP><Nazwa>Nabywca Testowy</Nazwa>";
    private String buyerAddress = "<Adres><KodKraju>PL</KodKraju><AdresL1>ul. Zmyślona 2</AdresL1></Adres>";
    private String currency = "PLN";
    private String number = "T/1";
    private String issueDate = "2026-10-01";
    private String totals = "<P_13_1>100</P_13_1><P_14_1>23</P_14_1><P_15>123</P_15>";
    private String type = "VAT";
    private String afterType = "";
    private String items = "<FaWiersz><NrWierszaFa>1</NrWierszaFa><P_7>Towar</P_7><P_8A>szt.</P_8A>"
            + "<P_8B>1</P_8B><P_9A>100</P_9A><P_11>100</P_11><P_12>23</P_12></FaWiersz>";

    static TestInvoiceXml minimal() {
        return new TestInvoiceXml();
    }

    TestInvoiceXml rootOpen(String value) {
        rootOpen = value;
        return this;
    }

    TestInvoiceXml formCode(String value) {
        formCode = value;
        return this;
    }

    TestInvoiceXml buyerIdentification(String value) {
        buyerIdentification = value;
        return this;
    }

    TestInvoiceXml buyerAddress(String value) {
        buyerAddress = value;
        return this;
    }

    TestInvoiceXml currency(String value) {
        currency = value;
        return this;
    }

    TestInvoiceXml number(String value) {
        number = value;
        return this;
    }

    TestInvoiceXml issueDate(String value) {
        issueDate = value;
        return this;
    }

    /** Everything between {@code P_2} and {@code Adnotacje}: sale date, buckets and {@code P_15}. */
    TestInvoiceXml totals(String value) {
        totals = value;
        return this;
    }

    TestInvoiceXml type(String value) {
        type = value;
        return this;
    }

    /** Elements between {@code RodzajFaktury} and the line items, e.g. correction data. */
    TestInvoiceXml afterType(String value) {
        afterType = value;
        return this;
    }

    TestInvoiceXml items(String value) {
        items = value;
        return this;
    }

    String xml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + rootOpen
                + "<Naglowek>" + formCode
                + "<WariantFormularza>3</WariantFormularza>"
                + "<DataWytworzeniaFa>2026-10-01T10:00:00Z</DataWytworzeniaFa></Naglowek>"
                + "<Podmiot1><DaneIdentyfikacyjne><NIP>9999999999</NIP><Nazwa>Sprzedawca Testowy</Nazwa>"
                + "</DaneIdentyfikacyjne><Adres><KodKraju>PL</KodKraju><AdresL1>ul. Fikcyjna 1</AdresL1>"
                + "</Adres></Podmiot1>"
                + "<Podmiot2><DaneIdentyfikacyjne>" + buyerIdentification + "</DaneIdentyfikacyjne>"
                + buyerAddress + "<JST>2</JST><GV>2</GV></Podmiot2>"
                + "<Fa><KodWaluty>" + currency + "</KodWaluty><P_1>" + issueDate + "</P_1>"
                + "<P_2>" + number + "</P_2>" + totals
                + "<Adnotacje><P_16>2</P_16><P_17>2</P_17><P_18>2</P_18><P_18A>2</P_18A>"
                + "<Zwolnienie><P_19N>1</P_19N></Zwolnienie><NoweSrodkiTransportu><P_22N>1</P_22N>"
                + "</NoweSrodkiTransportu><P_23>2</P_23><PMarzy><P_PMarzyN>1</P_PMarzyN></PMarzy>"
                + "</Adnotacje><RodzajFaktury>" + type + "</RodzajFaktury>" + afterType + items
                + "</Fa></Faktura>";
    }

    byte[] bytes() {
        return xml().getBytes(StandardCharsets.UTF_8);
    }
}
