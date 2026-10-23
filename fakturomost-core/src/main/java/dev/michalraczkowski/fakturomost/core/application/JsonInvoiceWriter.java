package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.Address;
import dev.michalraczkowski.fakturomost.core.domain.CorrectedInvoice;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceItem;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import dev.michalraczkowski.fakturomost.core.domain.VatBreakdown;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.DefaultPrettyPrinter;

/**
 * Writes invoices as JSON with Jackson's streaming generator. The format is defined here, field by
 * field — it is a contract with other software, versioned by {@link #FORMAT_VERSION}, and does not
 * follow Java field names automatically.
 *
 * <pre>{@code
 * {"format": "fakturomost-invoices", "formatVersion": 1, "invoices": [ {...}, ... ]}
 * }</pre>
 *
 * <p>Amounts are JSON numbers with the scale as written (never exponent notation); dates are
 * {@code yyyy-MM-dd} strings; absent values are {@code null}, so every invoice has the same keys.
 * The local source path is deliberately not exported.
 */
final class JsonInvoiceWriter {

    static final String FORMAT = "fakturomost-invoices";
    static final int FORMAT_VERSION = 1;

    private static final JsonFactory FACTORY = JsonFactory.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET) // the caller owns the stream
            .build();

    private static final ObjectWriteContext PRETTY = new ObjectWriteContext.Base() {
        @Override
        public PrettyPrinter getPrettyPrinter() {
            return new DefaultPrettyPrinter();
        }
    };

    private JsonInvoiceWriter() {
    }

    static void write(List<InvoiceEntry> invoices, OutputStream out) {
        try (JsonGenerator json = FACTORY.createGenerator(PRETTY, out)) {
            json.writeStartObject();
            json.writeStringProperty("format", FORMAT);
            json.writeNumberProperty("formatVersion", FORMAT_VERSION);
            json.writeArrayPropertyStart("invoices");
            for (InvoiceEntry entry : invoices) {
                writeInvoice(json, entry);
            }
            json.writeEndArray();
            json.writeEndObject();
        }
    }

    private static void writeInvoice(JsonGenerator json, InvoiceEntry entry) {
        Invoice invoice = entry.invoice();
        json.writeStartObject();
        json.writeStringProperty("documentId", entry.id().sha256Hex());
        json.writeStringProperty("ksefNumber", entry.ksefNumber() == null ? null : entry.ksefNumber().value());
        json.writeStringProperty("type", invoice.type().code());
        json.writeStringProperty("number", invoice.number());
        date(json, "issueDate", invoice.issueDate());
        date(json, "saleDate", invoice.saleDate());
        json.writeStringProperty("currency", invoice.currency().value());
        party(json, "seller", invoice.seller());
        party(json, "buyer", invoice.buyer());
        json.writeArrayPropertyStart("vatBreakdown");
        for (VatBreakdown bucket : invoice.vatBreakdown()) {
            json.writeStartObject();
            json.writeStringProperty("bucket", bucket.bucket().name());
            number(json, "net", bucket.net());
            number(json, "vat", bucket.vat());
            number(json, "vatInPln", bucket.vatInPln());
            json.writeEndObject();
        }
        json.writeEndArray();
        number(json, "grossTotal", invoice.grossTotal());
        json.writeArrayPropertyStart("items");
        for (InvoiceItem item : invoice.items()) {
            item(json, item);
        }
        json.writeEndArray();
        json.writeStringProperty("correctionReason", invoice.correctionReason());
        json.writeArrayPropertyStart("correctedInvoices");
        for (CorrectedInvoice corrected : invoice.correctedInvoices()) {
            json.writeStartObject();
            date(json, "issueDate", corrected.issueDate());
            json.writeStringProperty("number", corrected.number());
            json.writeStringProperty("ksefNumber", corrected.ksefNumber());
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeEndObject();
    }

    private static void party(JsonGenerator json, String name, Party party) {
        json.writeObjectPropertyStart(name);
        json.writeObjectPropertyStart("taxId");
        switch (party.taxId()) {
            case TaxIdentifier.Nip nip -> {
                json.writeStringProperty("type", "NIP");
                json.writeStringProperty("countryCode", "PL");
                json.writeStringProperty("value", nip.value());
            }
            case TaxIdentifier.EuVatNumber eu -> {
                json.writeStringProperty("type", "EU_VAT");
                json.writeStringProperty("countryCode", eu.countryCode());
                json.writeStringProperty("value", eu.number());
            }
            case TaxIdentifier.OtherTaxId other -> {
                json.writeStringProperty("type", "OTHER");
                json.writeStringProperty("countryCode", other.countryCode());
                json.writeStringProperty("value", other.value());
            }
            case TaxIdentifier.None none -> {
                json.writeStringProperty("type", "NONE");
                json.writeStringProperty("countryCode", null);
                json.writeStringProperty("value", null);
            }
        }
        json.writeEndObject();
        json.writeStringProperty("name", party.name());
        Address address = party.address();
        if (address == null) {
            json.writeNullProperty("address");
        } else {
            json.writeObjectPropertyStart("address");
            json.writeStringProperty("countryCode", address.countryCode());
            json.writeStringProperty("line1", address.line1());
            json.writeStringProperty("line2", address.line2());
            json.writeEndObject();
        }
        json.writeEndObject();
    }

    private static void item(JsonGenerator json, InvoiceItem item) {
        json.writeStartObject();
        json.writeNumberProperty("lineNumber", item.lineNumber());
        json.writeStringProperty("description", item.description());
        json.writeStringProperty("unit", item.unit());
        number(json, "quantity", item.quantity());
        number(json, "unitNetPrice", item.unitNetPrice());
        number(json, "unitGrossPrice", item.unitGrossPrice());
        number(json, "netAmount", item.netAmount());
        number(json, "grossAmount", item.grossAmount());
        number(json, "vatAmount", item.vatAmount());
        json.writeStringProperty("vatRate", item.vatRate() == null ? null : item.vatRate().code());
        number(json, "ossVatRatePercent", item.ossVatRatePercent());
        json.writeBooleanProperty("beforeCorrection", item.beforeCorrection());
        json.writeEndObject();
    }

    private static void number(JsonGenerator json, String name, BigDecimal value) {
        if (value == null) {
            json.writeNullProperty(name);
        } else {
            json.writeNumberProperty(name, value);
        }
    }

    private static void date(JsonGenerator json, String name, LocalDate value) {
        json.writeStringProperty(name, value == null ? null : value.toString());
    }
}
