package dev.michalraczkowski.fakturomost.core.fa3;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * The official FA(3) XML schema, loaded once from the bundled files (see {@code schema/SOURCE.md}).
 *
 * <p>Network and file access are impossible by construction: the schema factory and every
 * validator have {@code ACCESS_EXTERNAL_SCHEMA} and {@code ACCESS_EXTERNAL_DTD} set to "none", and
 * the only resolver is {@link BundledSchemaResolver}, which refuses anything but the three base
 * schemas.
 *
 * <p>The compiled {@link Schema} is immutable and thread-safe, so a single shared instance is not
 * the kind of global mutable state core avoids.
 */
final class Fa3Schema {

    static final String MAIN_SCHEMA_FILE = "schemat_FA(3)_v1-0E.xsd";

    /** Canonical location of the main schema; used as the base URI for its references. */
    private static final String MAIN_SCHEMA_URI = Fa3Format.NAMESPACE + "schemat.xsd";

    /** Xerces property that exposes the element being validated during DOM validation. */
    private static final String CURRENT_ELEMENT_NODE =
            "http://apache.org/xml/properties/dom/current-element-node";

    /** Message prefix such as {@code cvc-maxInclusive-valid}; the rest may contain document values. */
    private static final Pattern ERROR_CODE = Pattern.compile("^([A-Za-z][A-Za-z0-9.\\-]*):");

    private static final int MAX_REPORTED_ERRORS = 100;

    /** {@code maxOccurs} bounds at or above this are validated as {@code unbounded}. */
    static final int RELAXED_FROM = 100;

    private static final Pattern MAX_OCCURS = Pattern.compile("maxOccurs=\"(\\d{1,9})\"");

    private final Schema schema;

    private Fa3Schema(Schema schema) {
        this.schema = schema;
    }

    /** The bundled schema, compiled on first use. */
    static Fa3Schema bundled() {
        return Holder.INSTANCE;
    }

    /**
     * @throws InvalidInvoiceException if the document violates the schema; the message names the
     *                                 first rule and element path, never a value from the document
     */
    void validate(Document document) throws InvalidInvoiceException {
        Validator validator = schema.newValidator(); // not thread-safe, so one per call
        Collector collector = new Collector(validator);
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setResourceResolver(new BundledSchemaResolver());
            validator.setErrorHandler(collector);
            validator.validate(new DOMSource(document));
        } catch (TooManyViolations e) {
            // Stop early; the collected violations are reported below.
        } catch (SAXException e) {
            throw new InvalidInvoiceException("Schema validation failed: " + errorCode(e));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!collector.violations.isEmpty()) {
            throw collector.toInvalid();
        }
    }

    static InputStream openBundled(String fileName) {
        InputStream in = Fa3Schema.class.getResourceAsStream("schema/" + fileName);
        if (in == null) {
            throw new IllegalStateException("Bundled schema file missing: " + fileName);
        }
        return in;
    }

    private static Fa3Schema load() {
        try (InputStream main = openBundled(MAIN_SCHEMA_FILE)) {
            String relaxed = relaxLargeOccurrenceBounds(new String(main.readAllBytes(), StandardCharsets.UTF_8));
            SchemaFactory factory = SchemaFactory.newDefaultInstance();
            // Secure processing also keeps the JDK's 5,000-node content-model limit, which proves
            // that relaxLargeOccurrenceBounds left nothing large to expand.
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setResourceResolver(new BundledSchemaResolver());
            factory.setErrorHandler(StrictSchemaErrors.INSTANCE);
            return new Fa3Schema(factory.newSchema(new StreamSource(new StringReader(relaxed), MAIN_SCHEMA_URI)));
        } catch (SAXException e) {
            throw new IllegalStateException("Bundled FA(3) schema could not be loaded", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Replaces every {@code maxOccurs} of {@value #RELAXED_FROM} or more with {@code unbounded}.
     *
     * <p>The JDK's schema validator turns a bounded repetition into a state machine whose size grows
     * with the bound; the official schema's {@code maxOccurs="10000"} / {@code "50000"} (line items,
     * corrected invoices, ...) exhaust the heap. These bounds only limit how many rows a document may
     * have — {@link Fa3Parser#DEFAULT_MAX_DOCUMENT_BYTES} already caps that — so they are not
     * enforced. All structure, types, patterns, enumerations and smaller bounds are. The bundled
     * file itself stays byte-identical; this runs in memory.
     */
    static String relaxLargeOccurrenceBounds(String schemaText) {
        Matcher matcher = MAX_OCCURS.matcher(schemaText);
        StringBuilder result = new StringBuilder(schemaText.length());
        while (matcher.find()) {
            boolean large = Long.parseLong(matcher.group(1)) >= RELAXED_FROM;
            matcher.appendReplacement(result, large ? "maxOccurs=\"unbounded\"" : "$0");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String errorCode(SAXException e) {
        Matcher matcher = ERROR_CODE.matcher(String.valueOf(e.getMessage()));
        return matcher.find() ? matcher.group(1) : "schema-error";
    }

    /** {@code Faktura/Fa/FaWiersz[2]/P_11}: local names; index only where a name repeats. */
    private static String pathOf(Node node) {
        if (!(node instanceof Element element)) {
            return "?";
        }
        StringBuilder path = new StringBuilder();
        for (Element current = element; current != null; current = parentElement(current)) {
            String step = current.getLocalName() + indexAmongNamesakes(current);
            path.insert(0, path.isEmpty() ? step : step + "/");
        }
        return path.toString();
    }

    private static Element parentElement(Element element) {
        return element.getParentNode() instanceof Element parent ? parent : null;
    }

    private static String indexAmongNamesakes(Element element) {
        if (!(element.getParentNode() instanceof Element parent)) {
            return "";
        }
        int index = 0;
        int count = 0;
        for (Node sibling = parent.getFirstChild(); sibling != null; sibling = sibling.getNextSibling()) {
            if (sibling instanceof Element candidate
                    && candidate.getLocalName().equals(element.getLocalName())
                    && java.util.Objects.equals(candidate.getNamespaceURI(), element.getNamespaceURI())) {
                count++;
                if (candidate == element) {
                    index = count;
                }
            }
        }
        return count > 1 ? "[" + index + "]" : "";
    }

    /** Collects violations as "code at path"; never keeps the parser's message text. */
    private static final class Collector implements ErrorHandler {

        private final Validator validator;
        private final List<String> violations = new ArrayList<>();

        Collector(Validator validator) {
            this.validator = validator;
        }

        @Override
        public void warning(SAXParseException exception) {
            // Schema warnings do not make an invoice invalid.
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            String violation = errorCode(exception) + " at " + currentPath();
            if (!violations.contains(violation)) {
                violations.add(violation);
            }
            if (violations.size() >= MAX_REPORTED_ERRORS) {
                throw new TooManyViolations();
            }
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }

        private String currentPath() {
            try {
                return pathOf((Node) validator.getProperty(CURRENT_ELEMENT_NODE));
            } catch (SAXException | ClassCastException e) {
                return "?";
            }
        }

        InvalidInvoiceException toInvalid() {
            String more = violations.size() > 1 ? " (and " + (violations.size() - 1) + " more)" : "";
            return new InvalidInvoiceException("Schema validation failed: " + violations.get(0) + more);
        }
    }

    /** Stops validation of a hopelessly broken document; carries no document data. */
    private static final class TooManyViolations extends SAXException {

        @java.io.Serial
        private static final long serialVersionUID = 1L;
    }

    /** Any problem in the bundled schema itself is a build defect, never ignored. */
    private enum StrictSchemaErrors implements ErrorHandler {
        INSTANCE;

        @Override
        public void warning(SAXParseException exception) {
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }

    private static final class Holder {
        static final Fa3Schema INSTANCE = load();
    }
}
