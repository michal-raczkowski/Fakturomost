package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import tools.jackson.core.exc.JacksonIOException;

/**
 * Use case: export invoices as CSV or JSON, and documents as their original, unchanged XML.
 *
 * <p>Exported files are <strong>not encrypted</strong>; front ends must say so.
 *
 * <p>File rules: an existing file is never replaced unless {@code overwrite} is {@code true}. With
 * {@code overwrite}, the new content is written to a temporary file next to the target and moved
 * into place, so a failed export does not destroy the previous file. Without it, the target is
 * created exclusively and removed again if writing fails.
 */
public final class InvoiceExports {

    private final DocumentStore store;

    public InvoiceExports(DocumentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    // --- CSV and JSON ----------------------------------------------------------------------------

    /** One row per invoice, in the given order. The stream is flushed, not closed. */
    public void writeCsv(List<InvoiceEntry> invoices, CsvOptions options, OutputStream out) throws IOException {
        CsvWriter.write(List.copyOf(invoices), Objects.requireNonNull(options, "options"), out);
    }

    public void exportCsv(List<InvoiceEntry> invoices, CsvOptions options, Path file, boolean overwrite)
            throws IOException {
        writeFile(file, overwrite, out -> writeCsv(invoices, options, out));
    }

    /** The invoices in the given order, in the JSON format of {@link JsonInvoiceWriter}. Not closed. */
    public void writeJson(List<InvoiceEntry> invoices, OutputStream out) throws IOException {
        try {
            JsonInvoiceWriter.write(List.copyOf(invoices), out);
        } catch (JacksonIOException e) {
            throw e.getCause(); // keep the I/O contract of the other export methods
        }
    }

    public void exportJson(List<InvoiceEntry> invoices, Path file, boolean overwrite) throws IOException {
        writeFile(file, overwrite, out -> writeJson(invoices, out));
    }

    // --- Original XML ----------------------------------------------------------------------------

    /**
     * Writes the original bytes of the documents, unchanged — FA(3) invoices and stored documents of
     * other formats alike. If {@code target} ends with {@code .zip} (any case) one ZIP archive is
     * written; otherwise {@code target} is a directory (created if missing) with one file per
     * document.
     *
     * <p>File names: {@code <KSeF number>.xml} when known, otherwise {@code <document ID>.xml};
     * should two documents claim the same KSeF number, the later gets {@code -<short ID>} appended.
     * Without {@code overwrite}, nothing is written if any target file already exists.
     *
     * @return the files written (one path for a ZIP)
     * @throws IllegalArgumentException if an ID is not in the store; nothing is written then
     */
    public List<Path> exportOriginals(List<DocumentId> ids, Path target, boolean overwrite) throws IOException {
        Map<String, StoredDocument> byFileName = fileNames(lookUp(ids));
        if (target.getFileName() != null
                && target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            writeFile(target, overwrite, out -> writeZip(byFileName, out));
            return List.of(target);
        }
        Files.createDirectories(target);
        if (!overwrite) {
            for (String name : byFileName.keySet()) {
                if (Files.exists(target.resolve(name))) {
                    throw new FileAlreadyExistsException(target.resolve(name).toString());
                }
            }
        }
        List<Path> written = new ArrayList<>(byFileName.size());
        for (Map.Entry<String, StoredDocument> entry : byFileName.entrySet()) {
            Path file = target.resolve(entry.getKey());
            StoredDocument document = entry.getValue();
            writeFile(file, overwrite, out -> copy(document, out));
            written.add(file);
        }
        return List.copyOf(written);
    }

    private List<StoredDocument> lookUp(List<DocumentId> ids) {
        List<StoredDocument> documents = new ArrayList<>(ids.size());
        Set<DocumentId> seen = new HashSet<>();
        for (DocumentId id : ids) {
            if (seen.add(id)) {
                documents.add(store.find(id).orElseThrow(
                        () -> new IllegalArgumentException("Unknown document " + id.shortForm())));
            }
        }
        return documents;
    }

    /** Deterministic, safe names: only KSeF numbers (validated) and hex IDs ever become file names. */
    private static Map<String, StoredDocument> fileNames(List<StoredDocument> documents) {
        Map<String, StoredDocument> result = new LinkedHashMap<>();
        for (StoredDocument document : documents) {
            String base = document.ksefNumber() != null ? document.ksefNumber().value() : document.id().sha256Hex();
            String name = base + ".xml";
            if (result.containsKey(name)) {
                name = base + "-" + document.id().shortForm() + ".xml";
            }
            result.put(name, document);
        }
        return result;
    }

    private static void writeZip(Map<String, StoredDocument> byFileName, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        for (Map.Entry<String, StoredDocument> entry : byFileName.entrySet()) {
            zip.putNextEntry(new ZipEntry(entry.getKey()));
            copy(entry.getValue(), zip);
            zip.closeEntry();
        }
        zip.finish(); // completes the archive without closing the caller's stream
    }

    private static void copy(StoredDocument document, OutputStream out) throws IOException {
        try (InputStream in = document.original().openStream()) {
            in.transferTo(out);
        }
    }

    // --- Safe file writing -----------------------------------------------------------------------

    @FunctionalInterface
    private interface Content {
        void writeTo(OutputStream out) throws IOException;
    }

    private static void writeFile(Path file, boolean overwrite, Content content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!overwrite) {
            // CREATE_NEW fails atomically if the file exists — no check-then-write race.
            try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                content.writeTo(out);
            } catch (FileAlreadyExistsException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(file); // we created it, so a partial file must not remain
                throw e;
            }
            return;
        }
        Path temp = Files.createTempFile(parent, ".fakturomost-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                content.writeTo(out);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
