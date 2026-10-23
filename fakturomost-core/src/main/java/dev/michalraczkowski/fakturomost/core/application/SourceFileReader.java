package dev.michalraczkowski.fakturomost.core.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Turns files on disk into {@link SourceDocument}s: an XML file is one document, a ZIP archive is
 * one document per entry. Every problem becomes an {@link ImportResult} instead of an exception.
 *
 * <p>Security properties:
 * <ul>
 *   <li>Nothing is ever written to disk, so entry names like {@code ../../x} cannot escape anywhere
 *       (no Zip Slip); names are only labels, with control characters replaced.</li>
 *   <li>All {@link ImportLimits} are enforced on bytes actually read; declared sizes are only used
 *       to reject an archive early.</li>
 *   <li>Entries are handed over one at a time, so memory use is bounded by one document.</li>
 *   <li>Each entry's CRC-32 is checked against the archive, which {@link ZipFile} does not do.</li>
 *   <li>Nested archives are not opened.</li>
 * </ul>
 */
final class SourceFileReader {

    /** Receives what was read, in order. */
    interface Sink {
        void document(SourceDocument document);

        void rejected(ImportResult result);
    }

    private static final byte[] ZIP_LOCAL_HEADER = {'P', 'K', 3, 4};
    private static final byte[] ZIP_EMPTY_ARCHIVE = {'P', 'K', 5, 6};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};

    private final ImportLimits limits;

    SourceFileReader(ImportLimits limits) {
        this.limits = limits;
    }

    void read(Path path, Sink sink) {
        String name = displayName(path.toString());
        if (!Files.exists(path)) {
            sink.rejected(new ImportResult.Failed(name, "File not found", null));
            return;
        }
        if (!Files.isRegularFile(path)) {
            sink.rejected(new ImportResult.Failed(name, "Not a regular file", null));
            return;
        }
        try {
            byte[] head;
            try (InputStream in = Files.newInputStream(path)) {
                head = in.readNBytes(PDF.length);
            }
            if (startsWith(head, ZIP_LOCAL_HEADER) || startsWith(head, ZIP_EMPTY_ARCHIVE)) {
                readZip(path, name, sink);
            } else {
                readSingleFile(path, name, sink);
            }
        } catch (NoSuchFileException e) {
            sink.rejected(new ImportResult.Failed(name, "File not found", e));
        } catch (IOException e) {
            sink.rejected(new ImportResult.Failed(name, "Could not read file: " + e.getClass().getSimpleName(), e));
        }
    }

    private void readSingleFile(Path path, String name, Sink sink) throws IOException {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(path)) {
            bytes = in.readNBytes(limits.maxDocumentBytes() + 1);
        }
        if (bytes.length > limits.maxDocumentBytes()) {
            sink.rejected(tooLarge(name));
            return;
        }
        offer(name, bytes, sink);
    }

    private void readZip(Path path, String zipName, Sink sink) throws IOException {
        ZipFile zip;
        try {
            zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8);
        } catch (ZipException | IllegalArgumentException e) {
            // IllegalArgumentException: entry names that are not valid UTF-8.
            sink.rejected(new ImportResult.Invalid(zipName, null, "Not a readable ZIP archive"));
            return;
        }
        try (zip) {
            if (zip.size() > limits.maxZipEntries()) {
                sink.rejected(new ImportResult.Invalid(zipName, null,
                        "ZIP archive has more than " + limits.maxZipEntries() + " entries; nothing imported"));
                return;
            }
            if (declaredUncompressedSize(zip) > limits.maxZipUncompressedBytes()) {
                sink.rejected(new ImportResult.Invalid(zipName, null,
                        "ZIP archive is larger than " + limits.maxZipUncompressedBytes()
                                + " bytes uncompressed; nothing imported"));
                return;
            }
            readEntries(zip, zipName, sink);
        }
    }

    private void readEntries(ZipFile zip, String zipName, Sink sink) {
        long budget = limits.maxZipUncompressedBytes();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        int index = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            index++;
            if (entry.isDirectory()) {
                continue; // carries no document
            }
            String name = zipName + "!/" + displayName(entry.getName());
            int perEntryLimit = (int) Math.min(limits.maxDocumentBytes(), budget);
            byte[] bytes;
            try (InputStream in = zip.getInputStream(entry)) {
                bytes = in.readNBytes(perEntryLimit + 1);
            } catch (ZipException e) {
                sink.rejected(new ImportResult.Invalid(name, null, "Corrupt or unsupported ZIP entry"));
                continue;
            } catch (IOException e) {
                sink.rejected(new ImportResult.Failed(name, "Could not read ZIP entry: " + e.getClass().getSimpleName(), e));
                continue;
            }
            if (bytes.length > perEntryLimit) {
                if (perEntryLimit < limits.maxDocumentBytes()) {
                    // The archive lied about its size: stop rather than keep inflating.
                    sink.rejected(new ImportResult.Invalid(zipName, null,
                            "ZIP archive exceeds " + limits.maxZipUncompressedBytes()
                                    + " bytes uncompressed; entries from #" + index + " on not imported"));
                    return;
                }
                sink.rejected(tooLarge(name));
                budget -= bytes.length;
                continue;
            }
            budget -= bytes.length;
            if (!matchesCrc(entry, bytes)) {
                sink.rejected(new ImportResult.Invalid(name, null, "Corrupt ZIP entry (checksum mismatch)"));
                continue;
            }
            offer(name, bytes, sink);
        }
    }

    /**
     * {@link ZipFile} does not verify entry checksums, so a damaged archive could deliver altered
     * bytes — e.g. a changed digit in an amount that still passes schema validation.
     */
    private static boolean matchesCrc(ZipEntry entry, byte[] bytes) {
        if (entry.getCrc() == -1) {
            return true; // not recorded; nothing to compare with
        }
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue() == entry.getCrc();
    }

    /** Hands over a document, unless it is clearly not something to parse as XML. */
    private static void offer(String name, byte[] bytes, Sink sink) {
        SourceDocument document = SourceDocument.of(name, bytes);
        if (startsWith(bytes, ZIP_LOCAL_HEADER) || startsWith(bytes, ZIP_EMPTY_ARCHIVE)) {
            sink.rejected(new ImportResult.Invalid(name, document.content().id(), "Nested ZIP archives are not imported"));
        } else if (startsWith(bytes, PDF)) {
            sink.rejected(new ImportResult.Invalid(name, document.content().id(),
                    "PDF file, not an XML invoice (download invoices from KSeF as XML)"));
        } else {
            sink.document(document);
        }
    }

    private ImportResult tooLarge(String name) {
        return new ImportResult.Invalid(name, null, "Larger than the limit of " + limits.maxDocumentBytes() + " bytes");
    }

    private static long declaredUncompressedSize(ZipFile zip) {
        return zip.stream().mapToLong(entry -> Math.max(entry.getSize(), 0)).sum();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    /** Replaces control characters so a crafted name cannot break log lines or the UI. */
    static String displayName(String raw) {
        StringBuilder result = new StringBuilder(raw.length());
        raw.codePoints().forEach(c -> result.appendCodePoint(Character.isISOControl(c) ? '?' : c));
        return result.toString();
    }
}
