package dev.michalraczkowski.fakturomost.core.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OriginalDocumentTest {

    @Test
    void idIsTheSha256OfTheBytes() {
        // Standard test vector: SHA-256("abc").
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                OriginalDocument.of("abc".getBytes(StandardCharsets.US_ASCII)).id().sha256Hex());
        assertEquals("ba7816bf8f01", DocumentId.of("abc".getBytes(StandardCharsets.US_ASCII)).shortForm());
    }

    @Test
    void isImmutable() throws IOException {
        byte[] input = {1, 2, 3};
        OriginalDocument document = OriginalDocument.of(input);

        input[0] = 9;
        byte[] output = document.bytes();
        output[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, document.bytes());
        try (InputStream in = document.openStream()) {
            assertArrayEquals(new byte[] {1, 2, 3}, in.readAllBytes());
        }
        assertEquals(3, document.size());
    }

    @Test
    void equalityIsByContent() {
        assertEquals(OriginalDocument.of(new byte[] {1}), OriginalDocument.of(new byte[] {1}));
        assertEquals(OriginalDocument.of(new byte[] {1}).hashCode(), OriginalDocument.of(new byte[] {1}).hashCode());
        assertNotEquals(OriginalDocument.of(new byte[] {1}), OriginalDocument.of(new byte[] {1, 0}));
    }

    @Test
    void toStringDoesNotIncludeTheContent() {
        String text = OriginalDocument.of("SECRET".getBytes(StandardCharsets.US_ASCII)).toString();

        assertFalse(text.contains("SECRET"));
    }

    @Test
    void documentIdRejectsMalformedHex() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentId("ABC"));
        assertThrows(IllegalArgumentException.class, () -> new DocumentId("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"));
    }
}
