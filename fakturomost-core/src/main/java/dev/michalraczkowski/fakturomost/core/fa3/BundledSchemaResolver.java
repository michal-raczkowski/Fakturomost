package dev.michalraczkowski.fakturomost.core.fa3;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Serves the three FA(3) base schemas from the classpath and refuses every other reference.
 *
 * <p>This is the only way a schema can be loaded: there is no fallback to the network or the file
 * system. References are expanded against their base URI first, so both the absolute
 * {@code xsd:include}s of the crd.gov.pl originals and the relative ones of the ksef-docs copies
 * map to the same allowed URIs.
 */
final class BundledSchemaResolver implements LSResourceResolver {

    static final String DEFINITIONS_BASE =
            "http://crd.gov.pl/xml/schematy/dziedzinowe/mf/2022/01/05/eD/DefinicjeTypy/";

    /** Allowed absolute URI → bundled file name. Nothing else can ever be resolved. */
    static final Map<String, String> ALLOWED = Map.of(
            DEFINITIONS_BASE + "StrukturyDanych_v10-0E.xsd", "StrukturyDanych_v10-0E.xsd",
            DEFINITIONS_BASE + "ElementarneTypyDanych_v10-0E.xsd", "ElementarneTypyDanych_v10-0E.xsd",
            DEFINITIONS_BASE + "KodyKrajow_v10-0E.xsd", "KodyKrajow_v10-0E.xsd");

    @Override
    public LSInput resolveResource(
            String type, String namespaceUri, String publicId, String systemId, String baseUri) {
        String absolute = absolute(systemId, baseUri);
        String fileName = absolute == null ? null : ALLOWED.get(absolute);
        if (fileName == null) {
            // Throwing (rather than returning null) prevents the parser's default resolution.
            throw new SchemaReferenceRefusedException("Refusing to load schema reference: " + systemId);
        }
        InputStream bytes = Fa3Schema.openBundled(fileName);
        return new BundledInput(publicId, absolute, baseUri, bytes);
    }

    private static String absolute(String systemId, String baseUri) {
        if (systemId == null) {
            return null;
        }
        try {
            URI reference = new URI(systemId);
            if (reference.isAbsolute() || baseUri == null) {
                return reference.normalize().toString();
            }
            return new URI(baseUri).resolve(reference).normalize().toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Thrown for any schema reference outside {@link #ALLOWED}. */
    static final class SchemaReferenceRefusedException extends RuntimeException {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        SchemaReferenceRefusedException(String message) {
            super(message);
        }
    }

    /** Minimal {@link LSInput} carrying a byte stream; the system ID stays the canonical URI. */
    private record BundledInput(String publicId, String systemId, String baseUri, InputStream bytes)
            implements LSInput {

        @Override
        public InputStream getByteStream() {
            return bytes;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public String getBaseURI() {
            return baseUri;
        }

        @Override
        public java.io.Reader getCharacterStream() {
            return null;
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        // The setters are part of the DOM API but the parser never needs them here.

        @Override
        public void setCharacterStream(java.io.Reader characterStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setStringData(String stringData) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSystemId(String systemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPublicId(String publicId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setBaseURI(String baseUri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setEncoding(String encoding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            throw new UnsupportedOperationException();
        }
    }
}
