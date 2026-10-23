package dev.michalraczkowski.fakturomost.core.fa3;

/** Identifiers of the one supported document format: FA(3), schema version 1-0E. */
final class Fa3Format {

    /** Target namespace of the FA(3) schema ({@code schemat.xsd}). */
    static final String NAMESPACE = "http://crd.gov.pl/wzor/2025/06/25/13775/";

    static final String ROOT_ELEMENT = "Faktura";

    /** Fixed value of {@code KodFormularza/@kodSystemowy}; note the space. */
    static final String SYSTEM_CODE = "FA (3)";

    /** Fixed value of {@code KodFormularza/@wersjaSchemy}. */
    static final String SCHEMA_VERSION = "1-0E";

    private Fa3Format() {
    }
}
