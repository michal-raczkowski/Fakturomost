package dev.michalraczkowski.fakturomost.desktop.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The look depends on resources a JavaFX-free test can still check: the bundled font files are
 * present and unmodified (the OFL reserves the name "Plex", so no subsetting), their family name is
 * the one the stylesheet asks for — otherwise JavaFX silently falls back to the system font and the
 * UI looks different per OS again — and their digits are tabular, which the amount columns rely on.
 */
class ThemeResourcesTest {

    /** Same values as {@code fonts/SOURCE.md} (IBM/plex tag {@code @ibm/plex-sans@1.1.0}). */
    private static final Map<String, String> SHA_256 = Map.of(
            "IBMPlexSans-Regular.ttf", "975dcda37d80f038dcd143c22e33ca2d97a0cc5a929aace1c749153b0fe1afa5",
            "IBMPlexSans-SemiBold.ttf", "a20caf8286023a6a7a85e40b1d2a4ae9fc3e3b1f9eda8f4c542dd4986af67bb1",
            "IBMPlexSans-Bold.ttf", "9e6c74a889a700d707613d24548fe4ffa6bc59559a0689d2cf9e133bdcdafb2f");

    @Test
    void bundledFontsAreTheUnmodifiedReleaseFiles() throws IOException {
        assertThat(Theme.FONT_FILES).containsExactlyInAnyOrderElementsOf(SHA_256.keySet());
        for (String file : Theme.FONT_FILES) {
            assertThat(sha256(font(file))).as(file).isEqualTo(SHA_256.get(file));
        }
        assertThat(resource("fonts/OFL-IBM-Plex.txt")).as("licence must ship with the fonts").isNotNull();
    }

    @Test
    void stylesheetAsksForTheFamiliesTheFontsDeclare() throws IOException {
        String css = new String(resource("fakturomost.css"), StandardCharsets.UTF_8);

        assertThat(familyName(font("IBMPlexSans-Regular.ttf"))).isEqualTo(Theme.FONT_FAMILY);
        assertThat(familyName(font("IBMPlexSans-Bold.ttf"))).isEqualTo(Theme.FONT_FAMILY);
        // SemiBold is its own legacy family; the stylesheet names it directly for headings.
        String semiBold = familyName(font("IBMPlexSans-SemiBold.ttf"));
        assertThat(semiBold).isEqualTo("IBM Plex Sans SmBld");
        assertThat(css).contains("-fx-font-family: \"" + Theme.FONT_FAMILY + "\";")
                .contains("-fx-font-family: \"" + semiBold + "\";");
    }

    @Test
    void digitsAreTabularSoAmountsLineUp() throws IOException {
        for (String file : Theme.FONT_FILES) {
            assertThat(digitAdvanceWidths(font(file))).as(file).hasSize(1);
        }
    }

    // --- minimal TrueType reading (tables: name, cmap format 4, hhea, hmtx) -----------------------

    private static byte[] font(String file) throws IOException {
        byte[] bytes = resource("fonts/" + file);
        assertThat(bytes).as(file).isNotNull();
        return bytes;
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = Theme.class.getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static int table(ByteBuffer font, String tag) {
        int count = font.getShort(4) & 0xFFFF;
        for (int i = 0; i < count; i++) {
            int record = 12 + 16 * i;
            byte[] name = new byte[4];
            font.get(record, name);
            if (new String(name, StandardCharsets.US_ASCII).equals(tag)) {
                return font.getInt(record + 8);
            }
        }
        throw new AssertionError("No '" + tag + "' table");
    }

    /** Name ID 1 (family), Windows platform, UTF-16BE. */
    private static String familyName(byte[] bytes) {
        ByteBuffer font = ByteBuffer.wrap(bytes);
        int name = table(font, "name");
        int count = font.getShort(name + 2) & 0xFFFF;
        int strings = name + (font.getShort(name + 4) & 0xFFFF);
        for (int i = 0; i < count; i++) {
            int record = name + 6 + 12 * i;
            int platform = font.getShort(record) & 0xFFFF;
            int nameId = font.getShort(record + 6) & 0xFFFF;
            if (platform == 3 && nameId == 1) {
                int length = font.getShort(record + 8) & 0xFFFF;
                int offset = font.getShort(record + 10) & 0xFFFF;
                byte[] text = new byte[length];
                font.get(strings + offset, text);
                return new String(text, StandardCharsets.UTF_16BE);
            }
        }
        throw new AssertionError("No family name");
    }

    /** Advance widths of the glyphs for 0-9; one distinct value means tabular figures. */
    private static Set<Integer> digitAdvanceWidths(byte[] bytes) {
        ByteBuffer font = ByteBuffer.wrap(bytes);
        int longMetrics = font.getShort(table(font, "hhea") + 34) & 0xFFFF;
        int hmtx = table(font, "hmtx");
        Set<Integer> widths = new HashSet<>();
        for (char digit = '0'; digit <= '9'; digit++) {
            int glyph = glyphId(font, digit);
            int metric = Math.min(glyph, longMetrics - 1);
            widths.add(font.getShort(hmtx + 4 * metric) & 0xFFFF);
        }
        return widths;
    }

    /** Glyph ID from the Windows Unicode BMP cmap subtable (format 4). */
    private static int glyphId(ByteBuffer font, char c) {
        int cmap = table(font, "cmap");
        int count = font.getShort(cmap + 2) & 0xFFFF;
        for (int i = 0; i < count; i++) {
            int record = cmap + 4 + 8 * i;
            int platform = font.getShort(record) & 0xFFFF;
            int encoding = font.getShort(record + 2) & 0xFFFF;
            int sub = cmap + font.getInt(record + 4);
            if (platform == 3 && encoding == 1 && (font.getShort(sub) & 0xFFFF) == 4) {
                int segments = (font.getShort(sub + 6) & 0xFFFF) / 2;
                int ends = sub + 14;
                int starts = ends + 2 * segments + 2;
                int deltas = starts + 2 * segments;
                int ranges = deltas + 2 * segments;
                for (int s = 0; s < segments; s++) {
                    int end = font.getShort(ends + 2 * s) & 0xFFFF;
                    int start = font.getShort(starts + 2 * s) & 0xFFFF;
                    if (c >= start && c <= end) {
                        int delta = font.getShort(deltas + 2 * s);
                        int rangeOffset = font.getShort(ranges + 2 * s) & 0xFFFF;
                        if (rangeOffset == 0) {
                            return (c + delta) & 0xFFFF;
                        }
                        int glyph = font.getShort(ranges + 2 * s + rangeOffset + 2 * (c - start)) & 0xFFFF;
                        return glyph == 0 ? 0 : (glyph + delta) & 0xFFFF;
                    }
                }
            }
        }
        throw new AssertionError("No glyph for " + c);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
