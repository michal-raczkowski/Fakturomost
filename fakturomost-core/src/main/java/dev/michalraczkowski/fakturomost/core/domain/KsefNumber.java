package dev.michalraczkowski.fakturomost.core.domain;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A KSeF number: the identifier KSeF assigns to an accepted invoice, e.g.
 * {@code 5265877635-20250826-0100001AF629-AF}.
 *
 * <p>Structure (official spec, ksef-docs {@code faktury/numer-ksef.md}): always 35 characters —
 * seller NIP (10 digits), date the invoice was accepted ({@code yyyyMMdd}), 12 upper-case hex
 * digits, and a CRC-8 checksum (polynomial 0x07, initial value 0x00) of the first 32 characters as
 * 2 upper-case hex digits. All of it, including the checksum, is verified here.
 *
 * <p>The number is not inside the invoice XML; KSeF downloads carry it only as the file name.
 */
public record KsefNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile(
            "([1-9](?:\\d[1-9]|[1-9]\\d)\\d{7})-(\\d{8})-([0-9A-F]{12})-([0-9A-F]{2})");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final String XML_EXTENSION = ".xml";

    /**
     * @throws IllegalArgumentException if the value is not a valid KSeF number (format, date or
     *                                  checksum); the message does not repeat the value
     */
    public KsefNumber {
        Objects.requireNonNull(value, "value");
        Matcher matcher = FORMAT.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not in KSeF number format");
        }
        try {
            LocalDate.parse(matcher.group(2), DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("KSeF number contains an invalid date");
        }
        if (!matcher.group(4).equals(checksum(value.substring(0, 32)))) {
            throw new IllegalArgumentException("KSeF number checksum does not match");
        }
    }

    /** The KSeF number if {@code value} is a valid one, otherwise empty. */
    public static Optional<KsefNumber> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new KsefNumber(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * The KSeF number encoded in a file name such as {@code 5265877635-20250826-0100001AF629-AF.xml}
     * (the naming KSeF uses for downloads). Directory parts ({@code /} or {@code \}) are ignored and
     * the {@code .xml} extension is matched case-insensitively. Anything else — another name, a
     * renamed copy like {@code ...-AF (1).xml}, a wrong checksum — gives empty.
     */
    public static Optional<KsefNumber> fromFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
        if (!name.toLowerCase(Locale.ROOT).endsWith(XML_EXTENSION)) {
            return Optional.empty();
        }
        return parse(name.substring(0, name.length() - XML_EXTENSION.length()));
    }

    /** NIP of the seller who issued the invoice (first part of the number). */
    public String sellerNip() {
        return value.substring(0, 10);
    }

    /** Date KSeF accepted the invoice for processing. */
    public LocalDate acceptanceDate() {
        return LocalDate.parse(value.substring(11, 19), DATE);
    }

    @Override
    public String toString() {
        return value;
    }

    /** CRC-8, polynomial 0x07, initial value 0x00, no reflection, no final XOR; as 2 hex digits. */
    static String checksum(String data) {
        int crc = 0;
        for (byte b : data.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= b & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return String.format(Locale.ROOT, "%02X", crc);
    }
}
