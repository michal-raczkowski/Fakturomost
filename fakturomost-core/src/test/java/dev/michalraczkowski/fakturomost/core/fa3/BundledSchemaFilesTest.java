package dev.michalraczkowski.fakturomost.core.fa3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The bundled schemas must stay byte-identical to the official files (see
 * {@code schema/SOURCE.md}). Also catches Git converting their line endings — the base schemas use
 * CR-only line breaks, which an editor or {@code core.autocrlf} could silently rewrite.
 */
class BundledSchemaFilesTest {

    private static final Map<String, String> EXPECTED_SHA256 = Map.of(
            "schemat_FA(3)_v1-0E.xsd", "b646b6b525f51adf1bb2545f111fc8ca6e7aa6dd2f98948f1667d3695c06d958",
            "StrukturyDanych_v10-0E.xsd", "cb08348374598e1e716e086c40d740390fb9e1bfa3aba1f4ec4cba0e1ef6d60f",
            "ElementarneTypyDanych_v10-0E.xsd", "8daf4d3771de200b26b697294cc906a2add3de9acfbbd97f4b1bd4fc0e5ecb2f",
            "KodyKrajow_v10-0E.xsd", "48be2a9f181d7ff80f185c62491ba12604c5cacbbe21af8e2aaaf2c585bbd214");

    @Test
    void bundledSchemasAreTheOfficialFiles() throws IOException {
        Map<String, String> actual = new TreeMap<>();
        for (String file : EXPECTED_SHA256.keySet()) {
            try (InputStream in = Fa3Schema.openBundled(file)) {
                actual.put(file, sha256(in.readAllBytes()));
            }
        }
        assertEquals(new TreeMap<>(EXPECTED_SHA256), actual);
    }

    @Test
    void licenseNoticeIsBundledWithTheSchemas() {
        assertNotNull(Fa3Schema.class.getResource("schema/LICENSE-ksef-docs.txt"));
    }

    @Test
    void everyAllowedReferenceHasABundledFile() {
        for (String file : BundledSchemaResolver.ALLOWED.values()) {
            assertNotNull(Fa3Schema.class.getResource("schema/" + file), file);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
