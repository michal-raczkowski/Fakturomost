package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;

/**
 * Upper bounds for reading import sources. They are checked against bytes actually read, never
 * against sizes an archive merely declares, so a ZIP with forged headers cannot get past them.
 *
 * @param maxDocumentBytes        largest single document (an XML file or one ZIP entry, uncompressed)
 * @param maxZipEntries           most entries one ZIP may contain (directories included)
 * @param maxZipUncompressedBytes most bytes read from all entries of one ZIP together
 */
public record ImportLimits(int maxDocumentBytes, int maxZipEntries, long maxZipUncompressedBytes) {

    /**
     * 10 MiB per document (same as the parser), 10,000 entries and 512 MiB uncompressed per ZIP.
     * Generous for KSeF packages, small enough that a ZIP bomb is stopped after reading at most
     * 512 MiB, one document (at most 10 MiB) at a time.
     */
    public static final ImportLimits DEFAULT =
            new ImportLimits(Fa3Parser.DEFAULT_MAX_DOCUMENT_BYTES, 10_000, 512L * 1024 * 1024);

    public ImportLimits {
        if (maxDocumentBytes <= 0 || maxZipEntries <= 0 || maxZipUncompressedBytes <= 0) {
            throw new IllegalArgumentException("Limits must be positive");
        }
    }
}
