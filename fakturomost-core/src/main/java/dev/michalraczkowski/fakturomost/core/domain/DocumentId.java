package dev.michalraczkowski.fakturomost.core.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identity of an imported document: the SHA-256 of its original bytes, as 64 lower-case hex digits.
 *
 * <p>Content identity, not business identity: the same seller and invoice number can appear on
 * different documents, while the same document downloaded twice (single file or inside a ZIP) is
 * byte-identical and therefore gets the same ID. Any change to the bytes — even whitespace — makes a
 * different document.
 */
public record DocumentId(String sha256Hex) {

    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{64}");

    public DocumentId {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (!FORMAT.matcher(sha256Hex).matches()) {
            throw new IllegalArgumentException("Document ID must be 64 lower-case hex digits");
        }
    }

    /** The ID of these bytes. */
    public static DocumentId of(byte[] content) {
        Objects.requireNonNull(content, "content");
        try {
            return new DocumentId(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    /** First 12 hex digits, for compact display. Not unique enough to look documents up by. */
    public String shortForm() {
        return sha256Hex.substring(0, 12);
    }

    @Override
    public String toString() {
        return sha256Hex;
    }
}
