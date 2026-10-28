package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.ExportColumn;

/**
 * English names and short explanations of the exportable invoice fields, for the column chooser.
 * The switches have no {@code default}, so a column added to core without a label here does not
 * compile.
 */
public final class ExportFields {

    private ExportFields() {
    }

    /** What the field is, e.g. {@code Issue date}. Not the column name in the file. */
    public static String label(ExportColumn column) {
        return switch (column) {
            case KSEF_NUMBER -> "KSeF number";
            case INVOICE_NUMBER -> "Invoice number";
            case TYPE -> "Invoice type";
            case ISSUE_DATE -> "Issue date";
            case SALE_DATE -> "Sale date";
            case SELLER_TAX_ID -> "Seller NIP";
            case SELLER_NAME -> "Seller name";
            case BUYER_TAX_ID -> "Buyer tax ID";
            case BUYER_NAME -> "Buyer name";
            case CURRENCY -> "Currency";
            case NET_TOTAL -> "Net total";
            case VAT_TOTAL -> "VAT total";
            case GROSS_TOTAL -> "Gross total";
            case DOCUMENT_ID -> "Document ID (SHA-256)";
            case SELLER_COUNTRY -> "Seller country";
            case SELLER_ADDRESS_LINE1 -> "Seller address, line 1";
            case SELLER_ADDRESS_LINE2 -> "Seller address, line 2";
            case BUYER_TAX_ID_TYPE -> "Buyer tax ID kind";
            case BUYER_COUNTRY -> "Buyer country";
            case BUYER_ADDRESS_LINE1 -> "Buyer address, line 1";
            case BUYER_ADDRESS_LINE2 -> "Buyer address, line 2";
            case NET_BASIC_RATE -> "Net at 23%";
            case VAT_BASIC_RATE -> "VAT at 23%";
            case NET_FIRST_REDUCED_RATE -> "Net at 8%";
            case VAT_FIRST_REDUCED_RATE -> "VAT at 8%";
            case NET_SECOND_REDUCED_RATE -> "Net at 5%";
            case VAT_SECOND_REDUCED_RATE -> "VAT at 5%";
            case NET_ZERO_RATE -> "Net at 0%";
            case NET_EXEMPT -> "Net, exempt";
            case NET_REVERSE_CHARGE -> "Net, reverse charge";
            case VAT_TOTAL_PLN -> "VAT total in PLN";
            case CORRECTION_REASON -> "Correction reason";
            case CORRECTED_INVOICE_NUMBERS -> "Corrected invoice numbers";
            case CORRECTED_KSEF_NUMBERS -> "Corrected invoices' KSeF numbers";
        };
    }

    /** One line about where the value comes from; shown under the lists and as a tooltip. */
    public static String hint(ExportColumn column) {
        return switch (column) {
            case KSEF_NUMBER -> "From the file name of a KSeF download; empty when unknown.";
            case INVOICE_NUMBER -> "P_2, as written on the invoice.";
            case TYPE -> "FA(3) code: VAT, KOR (correction), ZAL (advance), ROZ, UPR, KOR_ZAL, KOR_ROZ.";
            case ISSUE_DATE -> "P_1, written as yyyy-MM-dd.";
            case SALE_DATE -> "P_6, written as yyyy-MM-dd; empty when the invoice gives none.";
            case SELLER_TAX_ID -> "Podmiot1 NIP.";
            case SELLER_NAME -> "Podmiot1 name.";
            case BUYER_TAX_ID -> "NIP, EU VAT number with country prefix, other tax ID, or empty.";
            case BUYER_NAME -> "Podmiot2 name; may be empty on simplified invoices.";
            case CURRENCY -> "KodWaluty, e.g. PLN or EUR.";
            case NET_TOTAL -> "Sum of the stated net amounts (P_13_x); empty when none is stated.";
            case VAT_TOTAL -> "Sum of the stated VAT amounts (P_14_x) in the invoice currency.";
            case GROSS_TOTAL -> "P_15, as written; negative on corrections that reduce the amount.";
            case DOCUMENT_ID -> "SHA-256 of the original XML file; identifies the document.";
            case SELLER_COUNTRY, BUYER_COUNTRY -> "Country code of the address (KodKraju).";
            case SELLER_ADDRESS_LINE1, BUYER_ADDRESS_LINE1 -> "AdresL1: street, or the whole address.";
            case SELLER_ADDRESS_LINE2, BUYER_ADDRESS_LINE2 -> "AdresL2: usually postcode and town; may be empty.";
            case BUYER_TAX_ID_TYPE -> "NIP, EU_VAT, OTHER or NONE.";
            case NET_BASIC_RATE -> "P_13_1: basic rate (23%, or 22% under older rules).";
            case VAT_BASIC_RATE -> "P_14_1: VAT at the basic rate.";
            case NET_FIRST_REDUCED_RATE -> "P_13_2: first reduced rate (8%, or 7%).";
            case VAT_FIRST_REDUCED_RATE -> "P_14_2: VAT at the first reduced rate.";
            case NET_SECOND_REDUCED_RATE -> "P_13_3: second reduced rate (5%).";
            case VAT_SECOND_REDUCED_RATE -> "P_14_3: VAT at the second reduced rate.";
            case NET_ZERO_RATE -> "P_13_6_1 + P_13_6_2 + P_13_6_3: 0% domestic, intra-EU and export.";
            case NET_EXEMPT -> "P_13_7: exempt supplies (zw).";
            case NET_REVERSE_CHARGE -> "P_13_10: domestic reverse charge.";
            case VAT_TOTAL_PLN -> "Sum of P_14_xW: VAT converted to PLN on foreign-currency invoices.";
            case CORRECTION_REASON -> "PrzyczynaKorekty; empty unless the invoice is a correction.";
            case CORRECTED_INVOICE_NUMBERS -> "Numbers of the corrected invoices, separated by \", \".";
            case CORRECTED_KSEF_NUMBERS -> "KSeF numbers of the corrected invoices that have one, separated by \", \".";
        };
    }

    /** Small text after the label in the lists: {@code number}, {@code date} or {@code text}. */
    public static String valueKind(ExportColumn column) {
        return column.isNumber() ? "number" : column.isDate() ? "date" : "text";
    }
}
