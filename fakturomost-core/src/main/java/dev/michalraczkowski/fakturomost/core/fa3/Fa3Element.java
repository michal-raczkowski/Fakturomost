package dev.michalraczkowski.fakturomost.core.fa3;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Read access to one FA(3) element. Children are matched by namespace URI and local name among
 * <em>direct</em> children only — never by prefix (real files use different prefixes) and never by
 * a deep search (which would pick up e.g. a {@code NIP} from {@code Podmiot3} or
 * {@code Podmiot2K}).
 *
 * <p>Error messages carry the element path but never its value.
 */
final class Fa3Element {

    /** Strict decimal: optional minus, digits, optional fraction. No exponent, sign '+', or commas. */
    private static final Pattern DECIMAL = Pattern.compile("-?\\d{1,24}(\\.\\d{1,12})?");
    private static final Pattern NATURAL = Pattern.compile("\\d{1,18}");
    private static final Pattern XML_WHITESPACE = Pattern.compile("[ \\t\\r\\n]+");

    private final Element element;
    private final String path;

    private Fa3Element(Element element, String path) {
        this.element = element;
        this.path = path;
    }

    static Fa3Element root(Element element) {
        return new Fa3Element(element, element.getLocalName());
    }

    String path() {
        return path;
    }

    String attribute(String name) {
        return element.hasAttributeNS(null, name) ? element.getAttributeNS(null, name) : null;
    }

    /** First direct child with this local name in the FA(3) namespace, or {@code null}. */
    Fa3Element child(String localName) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (isFa3Element(node, localName)) {
                return new Fa3Element((Element) node, path + "/" + localName);
            }
        }
        return null;
    }

    Fa3Element requiredChild(String localName) throws InvalidInvoiceException {
        Fa3Element child = child(localName);
        if (child == null) {
            throw missing(localName);
        }
        return child;
    }

    /** All direct children with this local name, in document order. */
    List<Fa3Element> children(String localName) {
        List<Fa3Element> result = new ArrayList<>();
        int index = 0;
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (isFa3Element(node, localName)) {
                index++;
                result.add(new Fa3Element((Element) node, path + "/" + localName + "[" + index + "]"));
            }
        }
        return result;
    }

    boolean has(String localName) {
        return child(localName) != null;
    }

    /**
     * Text of a child, whitespace-collapsed as for {@code xsd:token} (the type of nearly every FA(3)
     * text field), or {@code null} if the child is absent or empty.
     */
    String text(String localName) {
        Fa3Element child = child(localName);
        return child == null ? null : child.ownText();
    }

    String requiredText(String localName) throws InvalidInvoiceException {
        String text = text(localName);
        if (text == null) {
            throw missing(localName);
        }
        return text;
    }

    BigDecimal decimal(String localName) throws InvalidInvoiceException {
        String text = text(localName);
        if (text == null) {
            return null;
        }
        if (!DECIMAL.matcher(text).matches()) {
            throw new InvalidInvoiceException("Malformed number in " + path + "/" + localName);
        }
        return new BigDecimal(text);
    }

    BigDecimal requiredDecimal(String localName) throws InvalidInvoiceException {
        BigDecimal value = decimal(localName);
        if (value == null) {
            throw missing(localName);
        }
        return value;
    }

    LocalDate date(String localName) throws InvalidInvoiceException {
        String text = text(localName);
        if (text == null) {
            return null;
        }
        try {
            // ISO_LOCAL_DATE is strict yyyy-MM-dd, which is exactly etd:TData's pattern.
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new InvalidInvoiceException("Malformed date in " + path + "/" + localName);
        }
    }

    LocalDate requiredDate(String localName) throws InvalidInvoiceException {
        LocalDate value = date(localName);
        if (value == null) {
            throw missing(localName);
        }
        return value;
    }

    long requiredNaturalNumber(String localName) throws InvalidInvoiceException {
        String text = requiredText(localName);
        if (!NATURAL.matcher(text).matches()) {
            throw new InvalidInvoiceException("Malformed number in " + path + "/" + localName);
        }
        return Long.parseLong(text);
    }

    InvalidInvoiceException invalid(String localName, String problem) {
        return new InvalidInvoiceException(problem + " in " + path + "/" + localName);
    }

    private String ownText() {
        String collapsed = XML_WHITESPACE.matcher(element.getTextContent()).replaceAll(" ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    private InvalidInvoiceException missing(String localName) {
        return new InvalidInvoiceException("Missing required element " + path + "/" + localName);
    }

    private static boolean isFa3Element(Node node, String localName) {
        return node.getNodeType() == Node.ELEMENT_NODE
                && Fa3Format.NAMESPACE.equals(node.getNamespaceURI())
                && localName.equals(node.getLocalName());
    }
}
