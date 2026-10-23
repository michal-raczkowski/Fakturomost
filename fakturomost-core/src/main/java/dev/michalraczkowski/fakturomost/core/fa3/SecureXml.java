package dev.michalraczkowski.fakturomost.core.fa3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parses untrusted bytes into a DOM with every external-access and entity feature switched off.
 *
 * <p>FA(3) documents never need a DOCTYPE, so any DOCTYPE is rejected outright. That single rule
 * removes XXE, external DTDs and entity-expansion ("billion laughs") attacks; the remaining
 * settings are defence in depth in case a future change re-enables DOCTYPEs.
 *
 * <p>Uses {@link DocumentBuilderFactory#newDefaultInstance()} so the JDK's built-in parser is used
 * even if some other XML parser appears on the classpath; the feature names below are the ones it
 * understands.
 */
final class SecureXml {

    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private SecureXml() {
    }

    static Document parse(byte[] xml) throws InvalidInvoiceException {
        try {
            DocumentBuilder builder = newFactory().newDocumentBuilder();
            builder.setErrorHandler(FailFast.INSTANCE);
            // Never resolve anything external, even if a feature above were ignored.
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are not allowed");
            });
            return builder.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (SAXParseException e) {
            throw new InvalidInvoiceException(
                    "Not well-formed XML (line " + e.getLineNumber() + ", column " + e.getColumnNumber()
                            + "): " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new InvalidInvoiceException("Not well-formed XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new InvalidInvoiceException("Could not read XML: " + e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            // A JDK without these features would be unsafe to use, so fail loudly.
            throw new IllegalStateException("XML parser does not support required security features", e);
        }
    }

    private static DocumentBuilderFactory newFactory() throws ParserConfigurationException {
        // A new factory per call: factories are not thread-safe, and this keeps the parser shareable.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(DISALLOW_DOCTYPE, true);
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        factory.setFeature(LOAD_EXTERNAL_DTD, false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    /** Turns every parser diagnostic into an exception instead of printing it to stderr. */
    private enum FailFast implements ErrorHandler {
        INSTANCE;

        @Override
        public void warning(SAXParseException exception) {
            // Warnings (e.g. about unused features) are not errors; stay silent.
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
}
