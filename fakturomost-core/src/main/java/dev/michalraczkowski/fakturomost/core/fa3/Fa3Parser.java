package dev.michalraczkowski.fakturomost.core.fa3;

import dev.michalraczkowski.fakturomost.core.domain.Address;
import dev.michalraczkowski.fakturomost.core.domain.CorrectedInvoice;
import dev.michalraczkowski.fakturomost.core.domain.CurrencyCode;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceItem;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import dev.michalraczkowski.fakturomost.core.domain.VatBreakdown;
import dev.michalraczkowski.fakturomost.core.domain.VatBucket;
import dev.michalraczkowski.fakturomost.core.domain.VatRate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Validates one FA(3) XML document against the official schema and extracts an {@link Invoice}.
 *
 * <p>Steps: size limit → hardened XML parsing → format detection ({@code Unsupported} for anything
 * that is not FA(3) 1-0E) → XSD validation against the bundled schemas (no network) → extraction.
 *
 * <p>Never checks business rules or arithmetic — amounts and dates are reported as written.
 *
 * <p>Stateless and thread-safe; one instance can be shared.
 */
public final class Fa3Parser {

    /**
     * Default upper bound on document size. Well above what KSeF accepts for a single invoice,
     * low enough that a DOM of the document cannot exhaust a desktop heap.
     */
    public static final int DEFAULT_MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

    private final int maxDocumentBytes;
    private final Fa3Schema schema;

    public Fa3Parser() {
        this(DEFAULT_MAX_DOCUMENT_BYTES);
    }

    public Fa3Parser(int maxDocumentBytes) {
        this(maxDocumentBytes, Fa3Schema.bundled());
    }

    private Fa3Parser(int maxDocumentBytes, Fa3Schema schema) {
        if (maxDocumentBytes <= 0) {
            throw new IllegalArgumentException("maxDocumentBytes must be positive");
        }
        this.maxDocumentBytes = maxDocumentBytes;
        this.schema = schema;
    }

    /**
     * A parser that skips XSD validation. <strong>Tests only</strong>: it exists so the extraction
     * code's own defensive checks can be exercised with documents the schema would reject first.
     */
    static Fa3Parser withoutSchemaValidation() {
        return new Fa3Parser(DEFAULT_MAX_DOCUMENT_BYTES, null);
    }

    /**
     * @param xml the raw document bytes, exactly as downloaded; not modified
     * @throws UnsupportedFormatException well-formed XML that is not FA(3) 1-0E
     * @throws InvalidInvoiceException    malformed, oversized, schema-invalid, or not extractable
     */
    public Invoice parse(byte[] xml) throws InvoiceParseException {
        Objects.requireNonNull(xml, "xml");
        if (xml.length > maxDocumentBytes) {
            throw new InvalidInvoiceException(
                    "Document is larger than the limit of " + maxDocumentBytes + " bytes");
        }
        Document document = SecureXml.parse(xml);
        Fa3Element root = checkFormat(document.getDocumentElement());
        if (schema != null) {
            schema.validate(document);
        }

        Fa3Element fa = root.requiredChild("Fa");
        InvoiceType type = invoiceType(fa);
        return new Invoice(
                type,
                fa.requiredText("P_2"),
                fa.requiredDate("P_1"),
                fa.date("P_6"),
                currency(fa),
                seller(root.requiredChild("Podmiot1")),
                buyer(root.requiredChild("Podmiot2")),
                vatBreakdown(fa),
                fa.requiredDecimal("P_15"),
                items(fa),
                fa.text("PrzyczynaKorekty"),
                correctedInvoices(fa));
    }

    private static Fa3Element checkFormat(Element rootElement) throws InvoiceParseException {
        String namespace = rootElement.getNamespaceURI();
        String localName = rootElement.getLocalName();
        if (!Fa3Format.NAMESPACE.equals(namespace) || !Fa3Format.ROOT_ELEMENT.equals(localName)) {
            throw new UnsupportedFormatException(
                    "Not an FA(3) invoice: root element is {" + namespace + "}" + localName,
                    namespace, localName);
        }
        Fa3Element root = Fa3Element.root(rootElement);
        Fa3Element formCode = root.requiredChild("Naglowek").requiredChild("KodFormularza");
        String systemCode = formCode.attribute("kodSystemowy");
        String schemaVersion = formCode.attribute("wersjaSchemy");
        if (!Fa3Format.SYSTEM_CODE.equals(systemCode) || !Fa3Format.SCHEMA_VERSION.equals(schemaVersion)) {
            throw new UnsupportedFormatException(
                    "Unsupported form version: kodSystemowy=" + systemCode + ", wersjaSchemy=" + schemaVersion,
                    namespace, localName);
        }
        return root;
    }

    private static InvoiceType invoiceType(Fa3Element fa) throws InvalidInvoiceException {
        try {
            return InvoiceType.fromCode(fa.requiredText("RodzajFaktury"));
        } catch (IllegalArgumentException e) {
            throw fa.invalid("RodzajFaktury", "Unknown invoice type");
        }
    }

    private static CurrencyCode currency(Fa3Element fa) throws InvalidInvoiceException {
        try {
            return new CurrencyCode(fa.requiredText("KodWaluty"));
        } catch (IllegalArgumentException e) {
            throw fa.invalid("KodWaluty", "Malformed currency code");
        }
    }

    private static Party seller(Fa3Element podmiot1) throws InvalidInvoiceException {
        Fa3Element id = podmiot1.requiredChild("DaneIdentyfikacyjne");
        return new Party(
                new TaxIdentifier.Nip(id.requiredText("NIP")),
                id.requiredText("Nazwa"),
                address(podmiot1.requiredChild("Adres")));
    }

    private static Party buyer(Fa3Element podmiot2) throws InvalidInvoiceException {
        Fa3Element id = podmiot2.requiredChild("DaneIdentyfikacyjne");
        Fa3Element address = podmiot2.child("Adres");
        return new Party(
                buyerTaxId(id),
                id.text("Nazwa"),
                address == null ? null : address(address));
    }

    /** {@code TPodmiot2} offers a choice: NIP | KodUE + NrVatUE | [KodKraju] + NrID | BrakID. */
    private static TaxIdentifier buyerTaxId(Fa3Element id) throws InvalidInvoiceException {
        if (id.has("NIP")) {
            return new TaxIdentifier.Nip(id.requiredText("NIP"));
        }
        if (id.has("NrVatUE")) {
            return new TaxIdentifier.EuVatNumber(id.requiredText("KodUE"), id.requiredText("NrVatUE"));
        }
        if (id.has("NrID")) {
            return new TaxIdentifier.OtherTaxId(id.text("KodKraju"), id.requiredText("NrID"));
        }
        if (id.has("BrakID")) {
            return new TaxIdentifier.None();
        }
        throw new InvalidInvoiceException(
                "Missing buyer identifier (NIP, NrVatUE, NrID or BrakID) in " + id.path());
    }

    private static Address address(Fa3Element adres) throws InvalidInvoiceException {
        return new Address(adres.requiredText("KodKraju"), adres.requiredText("AdresL1"), adres.text("AdresL2"));
    }

    /** Net, VAT and VAT-in-PLN field names per bucket, in schema order; {@code null} = no such field. */
    private enum BucketFields {
        P_13_1(VatBucket.BASIC_RATE, "P_13_1", "P_14_1", "P_14_1W"),
        P_13_2(VatBucket.FIRST_REDUCED_RATE, "P_13_2", "P_14_2", "P_14_2W"),
        P_13_3(VatBucket.SECOND_REDUCED_RATE, "P_13_3", "P_14_3", "P_14_3W"),
        P_13_4(VatBucket.TAXI_FLAT_RATE, "P_13_4", "P_14_4", "P_14_4W"),
        P_13_5(VatBucket.OSS_PROCEDURE, "P_13_5", "P_14_5", null),
        P_13_6_1(VatBucket.ZERO_DOMESTIC, "P_13_6_1", null, null),
        P_13_6_2(VatBucket.ZERO_INTRA_EU, "P_13_6_2", null, null),
        P_13_6_3(VatBucket.ZERO_EXPORT, "P_13_6_3", null, null),
        P_13_7(VatBucket.EXEMPT, "P_13_7", null, null),
        P_13_8(VatBucket.OUTSIDE_COUNTRY, "P_13_8", null, null),
        P_13_9(VatBucket.ART_100_SERVICES, "P_13_9", null, null),
        P_13_10(VatBucket.REVERSE_CHARGE, "P_13_10", null, null),
        P_13_11(VatBucket.MARGIN, "P_13_11", null, null);

        final VatBucket bucket;
        final String net;
        final String vat;
        final String vatInPln;

        BucketFields(VatBucket bucket, String net, String vat, String vatInPln) {
            this.bucket = bucket;
            this.net = net;
            this.vat = vat;
            this.vatInPln = vatInPln;
        }
    }

    private static List<VatBreakdown> vatBreakdown(Fa3Element fa) throws InvalidInvoiceException {
        List<VatBreakdown> result = new ArrayList<>();
        for (BucketFields fields : BucketFields.values()) {
            BigDecimal net = fa.decimal(fields.net);
            if (net == null) {
                continue; // In every bucket the net field is the one that is always present.
            }
            BigDecimal vat = fields.vat == null ? null : fa.decimal(fields.vat);
            BigDecimal vatInPln = fields.vatInPln == null ? null : fa.decimal(fields.vatInPln);
            result.add(new VatBreakdown(fields.bucket, net, vat, vatInPln));
        }
        return result;
    }

    private static List<InvoiceItem> items(Fa3Element fa) throws InvalidInvoiceException {
        List<InvoiceItem> result = new ArrayList<>();
        for (Fa3Element row : fa.children("FaWiersz")) {
            result.add(new InvoiceItem(
                    row.requiredNaturalNumber("NrWierszaFa"),
                    row.text("P_7"),
                    row.text("P_8A"),
                    row.decimal("P_8B"),
                    row.decimal("P_9A"),
                    row.decimal("P_9B"),
                    row.decimal("P_11"),
                    row.decimal("P_11A"),
                    row.decimal("P_11Vat"),
                    vatRate(row),
                    row.decimal("P_12_XII"),
                    "1".equals(row.text("StanPrzed"))));
        }
        return result;
    }

    private static VatRate vatRate(Fa3Element row) throws InvalidInvoiceException {
        String code = row.text("P_12");
        if (code == null) {
            return null;
        }
        try {
            return VatRate.fromCode(code);
        } catch (IllegalArgumentException e) {
            throw row.invalid("P_12", "Unknown VAT rate");
        }
    }

    private static List<CorrectedInvoice> correctedInvoices(Fa3Element fa) throws InvalidInvoiceException {
        List<CorrectedInvoice> result = new ArrayList<>();
        for (Fa3Element corrected : fa.children("DaneFaKorygowanej")) {
            result.add(new CorrectedInvoice(
                    corrected.requiredDate("DataWystFaKorygowanej"),
                    corrected.requiredText("NrFaKorygowanej"),
                    corrected.text("NrKSeFFaKorygowanej")));
        }
        return result;
    }
}
