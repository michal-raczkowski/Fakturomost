package dev.michalraczkowski.fakturomost.core.domain;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

/**
 * The bytes of a document exactly as received, kept unchanged so they can be exported or re-read
 * later. Immutable: the bytes are copied in and out. Equality is by content ({@link #id()}).
 *
 * <p>{@link #toString()} never includes the content.
 */
public final class OriginalDocument {

    private final byte[] bytes;
    private final DocumentId id;

    private OriginalDocument(byte[] bytes) {
        this.bytes = bytes;
        this.id = DocumentId.of(bytes);
    }

    /** Copies {@code bytes}; later changes to the array do not affect this document. */
    public static OriginalDocument of(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new OriginalDocument(bytes.clone());
    }

    public DocumentId id() {
        return id;
    }

    public int size() {
        return bytes.length;
    }

    /** A copy of the bytes. For large documents prefer {@link #openStream()}. */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** Reads the bytes without copying them. */
    public InputStream openStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OriginalDocument that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "OriginalDocument[id=" + id.shortForm() + ", size=" + bytes.length + "]";
    }
}
