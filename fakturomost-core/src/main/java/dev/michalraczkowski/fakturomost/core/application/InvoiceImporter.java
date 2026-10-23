package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import dev.michalraczkowski.fakturomost.core.fa3.InvalidInvoiceException;
import dev.michalraczkowski.fakturomost.core.fa3.InvoiceParseException;
import dev.michalraczkowski.fakturomost.core.fa3.UnsupportedFormatException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Use case: import documents into a {@link DocumentStore}, one {@link ImportResult} per document.
 *
 * <p>Rules:
 * <ul>
 *   <li>Identity is the SHA-256 of the original bytes; a byte-identical document is a
 *       {@code Duplicate} regardless of its name, and is not parsed again.</li>
 *   <li>FA(3) invoices are validated and stored ({@code Imported}); other XML formats are stored
 *       without invoice data ({@code Unsupported}); broken documents are reported, not stored
 *       ({@code Invalid}).</li>
 *   <li>The KSeF number comes only from the source name, and for invoices only if its seller NIP
 *       matches the invoice's seller — a renamed or mismatched file gets no KSeF number rather than
 *       a wrong one.</li>
 *   <li>Never throws for a bad document or a failing store; that becomes {@code Failed}.</li>
 * </ul>
 *
 * <p>Stateless apart from its collaborators; thread-safe if the store is.
 */
public final class InvoiceImporter {

    private final Fa3Parser parser;
    private final DocumentStore store;
    private final SourceFileReader fileReader;

    public InvoiceImporter(Fa3Parser parser, DocumentStore store) {
        this(parser, store, ImportLimits.DEFAULT);
    }

    public InvoiceImporter(Fa3Parser parser, DocumentStore store, ImportLimits limits) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.store = Objects.requireNonNull(store, "store");
        this.fileReader = new SourceFileReader(Objects.requireNonNull(limits, "limits"));
    }

    /**
     * Imports files: an XML file is one document, a ZIP archive (detected by content, not by
     * extension) one document per entry — e.g. a KSeF bulk download. Results come in order; an
     * entry is named {@code <zip path>!/<entry name>}, and its KSeF number is read from the entry
     * name. Problems with a file or archive as a whole are reported under the file's name.
     *
     * <p>Files are only read, never modified; nothing is extracted to disk.
     */
    public List<ImportResult> importPaths(List<Path> paths) {
        List<ImportResult> results = new ArrayList<>();
        SourceFileReader.Sink sink = new SourceFileReader.Sink() {
            @Override
            public void document(SourceDocument document) {
                results.add(importDocument(document));
            }

            @Override
            public void rejected(ImportResult result) {
                results.add(result);
            }
        };
        for (Path path : paths) {
            fileReader.read(path, sink);
        }
        return results;
    }

    /** Imports the documents in order; duplicates within the list are detected too. */
    public List<ImportResult> importDocuments(List<SourceDocument> sources) {
        List<ImportResult> results = new ArrayList<>(sources.size());
        for (SourceDocument source : sources) {
            results.add(importDocument(source));
        }
        return results;
    }

    public ImportResult importDocument(SourceDocument source) {
        Objects.requireNonNull(source, "source");
        String name = source.name();
        DocumentId id = source.content().id();
        try {
            if (store.contains(id)) {
                return new ImportResult.Duplicate(name, id);
            }
            StoredDocument document;
            ImportResult ifStored;
            try {
                Invoice invoice = parser.parse(source.content().bytes());
                KsefNumber ksefNumber = ksefNumberFor(name, invoice);
                document = new StoredDocument(source.content(), name, ksefNumber, invoice);
                ifStored = new ImportResult.Imported(name, id, ksefNumber, invoice);
            } catch (InvoiceParseException e) {
                // Exhaustive over the sealed hierarchy: a new kind of parse failure won't compile here.
                switch (e) {
                    case UnsupportedFormatException unsupported -> {
                        KsefNumber ksefNumber = KsefNumber.fromFileName(name).orElse(null);
                        document = new StoredDocument(source.content(), name, ksefNumber, null);
                        ifStored = new ImportResult.Unsupported(name, id, ksefNumber, unsupported.getMessage());
                    }
                    case InvalidInvoiceException invalid -> {
                        return new ImportResult.Invalid(name, id, invalid.getMessage());
                    }
                }
            }
            return store.addIfAbsent(document) ? ifStored : new ImportResult.Duplicate(name, id);
        } catch (RuntimeException e) {
            return new ImportResult.Failed(name, "Unexpected error: " + e.getClass().getSimpleName(), e);
        }
    }

    private static KsefNumber ksefNumberFor(String sourceName, Invoice invoice) {
        return KsefNumber.fromFileName(sourceName)
                .filter(number -> invoice.seller().taxId() instanceof TaxIdentifier.Nip(String nip)
                        && nip.equals(number.sellerNip()))
                .orElse(null);
    }
}
