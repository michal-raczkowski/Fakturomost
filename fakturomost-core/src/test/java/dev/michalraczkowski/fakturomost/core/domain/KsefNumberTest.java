package dev.michalraczkowski.fakturomost.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KsefNumberTest {

    /** The example from the official spec (ksef-docs, faktury/numer-ksef.md). */
    private static final String OFFICIAL_EXAMPLE = "5265877635-20250826-0100001AF629-AF";
    /** Synthetic, fictitious NIP; checksum computed with the spec's CRC-8. */
    private static final String SYNTHETIC = "9999999999-20261020-0123456789AB-F2";

    @Test
    void acceptsTheOfficialExampleAndExposesItsParts() {
        KsefNumber number = new KsefNumber(OFFICIAL_EXAMPLE);

        assertEquals("5265877635", number.sellerNip());
        assertEquals(LocalDate.of(2025, 8, 26), number.acceptanceDate());
        assertEquals(OFFICIAL_EXAMPLE, number.toString());
    }

    @Test
    void computesTheChecksumAsSpecified() {
        assertEquals("AF", KsefNumber.checksum(OFFICIAL_EXAMPLE.substring(0, 32)));
        assertEquals("F2", KsefNumber.checksum(SYNTHETIC.substring(0, 32)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "9999999999-20230908-8BEF280C8D35-4D",  // from MF sample 2: pattern-valid, checksum wrong
        "5265877635-20250826-0100001AF629-AE",  // official example, last digit changed
        "5265877635-20250826-0100001AF629-af",  // lower-case checksum
        "5265877635-20250826-0100001af629-AF",  // lower-case technical part
        "5265877635-20250826-0100001-AF629-AF", // 6-6 hyphenated variant the XSD allows; never issued
        "0265877635-20250826-0100001AF629-AF",  // NIP cannot start with 0
        "5265877635-20251332-0100001AF629-AF",  // no such date
        "5265877635-20250826-0100001AF629",     // checksum missing
        " 5265877635-20250826-0100001AF629-AF", // surrounding whitespace
        ""})
    void rejectsInvalidNumbers(String value) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new KsefNumber(value));

        assertFalse(!value.isEmpty() && e.getMessage().contains(value), "message must not repeat the value");
        assertEquals(Optional.empty(), KsefNumber.parse(value));
    }

    @Test
    void readsTheNumberFromAKsefDownloadFileName() {
        KsefNumber expected = new KsefNumber(SYNTHETIC);

        assertEquals(Optional.of(expected), KsefNumber.fromFileName(SYNTHETIC + ".xml"));
        assertEquals(Optional.of(expected), KsefNumber.fromFileName(SYNTHETIC + ".XML"));
        assertEquals(Optional.of(expected), KsefNumber.fromFileName("downloads/2026/" + SYNTHETIC + ".xml"));
        assertEquals(Optional.of(expected), KsefNumber.fromFileName("C:\\Users\\x\\" + SYNTHETIC + ".xml"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "invoice.xml",
        "9999999999-20261020-0123456789AB-F2 (1).xml", // browser's copy suffix
        "9999999999-20261020-0123456789AB-F2.pdf",
        "9999999999-20261020-0123456789AB-F2.xml.bak",
        "9999999999-20261020-0123456789AB-F2",
        "9999999999-20261020-0123456789AB-F3.xml",     // checksum mismatch
        ".xml"})
    void ignoresFileNamesThatAreNotAValidKsefNumber(String fileName) {
        assertEquals(Optional.empty(), KsefNumber.fromFileName(fileName));
    }

    @Test
    void handlesNull() {
        assertTrue(KsefNumber.parse(null).isEmpty());
        assertTrue(KsefNumber.fromFileName(null).isEmpty());
    }
}
