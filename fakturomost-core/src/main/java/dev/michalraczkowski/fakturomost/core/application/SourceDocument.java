package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.OriginalDocument;
import java.util.Objects;

/**
 * One document offered for import.
 *
 * @param name    where it came from, for display and for reading a KSeF number from the file
 *                name — e.g. {@code 5265877635-20250826-0100001AF629-AF.xml}, or a path / ZIP entry
 *                name (only the last segment is used for the KSeF number)
 * @param content the bytes exactly as received
 */
public record SourceDocument(String name, OriginalDocument content) {

    public SourceDocument {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
    }

    /** Copies {@code bytes}. */
    public static SourceDocument of(String name, byte[] bytes) {
        return new SourceDocument(name, OriginalDocument.of(bytes));
    }
}
