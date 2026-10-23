package dev.michalraczkowski.fakturomost.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.GeneralCodingRules;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for {@code fakturomost-core}, checked on the compiled production classes.
 *
 * <p>The Maven Enforcer bans framework <em>jars</em>; these rules check what the code actually
 * uses — including JDK APIs (JDBC, sockets, console, XML factories) that no dependency ban can see
 * — and pin the package layering inside core.
 *
 * <p>Plain JUnit tests on the {@code archunit} library (no ArchUnit test engine), so they run on
 * whatever JUnit version the Spring Boot BOM manages.
 */
class CoreArchitectureTest {

    private static final String CORE = "dev.michalraczkowski.fakturomost.core";
    private static final String DOMAIN = CORE + ".domain..";
    private static final String FA3 = CORE + ".fa3..";
    private static final String APPLICATION = CORE + ".application..";

    /** Production classes only, imported once for all rules. */
    private static final JavaClasses CORE_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(CORE);

    // --- Structure: application -> fa3 -> domain ---------------------------------------------------

    @Test
    void packagesFormLayersDomainFa3Application() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(DOMAIN)
                .layer("FA(3) parsing").definedBy(FA3)
                .layer("Application").definedBy(APPLICATION)
                .whereLayer("Application").mayNotBeAccessedByAnyLayer()
                .whereLayer("FA(3) parsing").mayOnlyBeAccessedByLayers("Application")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("FA(3) parsing", "Application")
                .because("the domain model must not know how it is parsed or used, and parsing must not "
                        + "know about import, storage or export")
                .check(CORE_CLASSES);
    }

    @Test
    void corePackagesAreFreeOfCycles() {
        slices().matching(CORE + ".(*)..").should().beFreeOfCycles().check(CORE_CLASSES);
    }

    @Test
    void domainDependsOnlyOnTheJdk() {
        classes().that().resideInAPackage(DOMAIN)
                .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, "java..")
                .because("the domain model is plain Java that every front end and store can use")
                .check(CORE_CLASSES);
    }

    @Test
    void fa3DependsOnlyOnTheDomainAndTheJdkXmlApis() {
        classes().that().resideInAPackage(FA3)
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        FA3, DOMAIN, "java..", "javax.xml..", "org.w3c.dom..", "org.xml.sax..")
                .because("the parser uses only the JDK's XML stack, which SecureXml hardens")
                .check(CORE_CLASSES);
    }

    @Test
    void onlyTheApplicationLayerUsesJackson() {
        noClasses().that().resideOutsideOfPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAPackage("tools.jackson..")
                .because("JSON is an export format, not part of the domain or the parser")
                .check(CORE_CLASSES);
    }

    // --- What core must never use ------------------------------------------------------------------

    @Test
    void coreUsesNoFrameworksDatabasesOrUi() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "javafx..", "java.awt..", "javax.swing..",
                        "java.sql..", "javax.sql..", "org.sqlite..", "com.sun.jna..", "picocli..")
                .because("core is a framework-free library; storage, Spring, UI and CLI live in their own modules")
                .check(CORE_CLASSES);
    }

    @Test
    void coreDoesNoNetworking() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.net.http..", "javax.net..")
                .orShould().dependOnClassesThat().belongToAnyOf(
                        URL.class, URLConnection.class, HttpURLConnection.class,
                        Socket.class, ServerSocket.class, DatagramSocket.class)
                .because("parsing and schema validation must never reach the network; KSeF sync, if ever "
                        + "built, is an adapter outside core (java.net.URI stays allowed)")
                .check(CORE_CLASSES);
    }

    // --- XML security --------------------------------------------------------------------------------

    @Test
    void onlyTheHardenedParserCreatesXmlParsers() {
        noClasses().that().haveNameNotMatching(".*\\.fa3\\.SecureXml(\\$.*)?")
                .should().dependOnClassesThat().resideInAPackage("javax.xml.parsers..")
                .because("every XML document must go through SecureXml's hardened configuration (XXE)")
                .check(CORE_CLASSES);
    }

    @Test
    void noOtherXmlProcessingApisAreUsed() {
        noClasses().should().dependOnClassesThat().resideInAPackage("javax.xml.stream..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("javax.xml.transform.TransformerFactory")
                .because("StAX readers and XSLT transformers would need their own hardening; use SecureXml")
                .check(CORE_CLASSES);
    }

    @Test
    void onlyTheBundledSchemaLoadsXmlSchemas() {
        noClasses().that().haveNameNotMatching(".*\\.fa3\\.Fa3Schema(\\$.*)?")
                .should().dependOnClassesThat().resideInAPackage("javax.xml.validation..")
                .because("schemas must come from the bundled, hash-checked files via BundledSchemaResolver")
                .check(CORE_CLASSES);
    }

    @Test
    void productionCodeAlwaysValidatesAgainstTheSchema() {
        noClasses().should().callMethod(Fa3Parser.class, "withoutSchemaValidation")
                .because("the non-validating parser exists for tests only")
                .check(CORE_CLASSES);
    }

    // --- Log hygiene ---------------------------------------------------------------------------------

    @Test
    void coreDoesNotWriteToTheConsoleOrJavaUtilLogging() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                .because("invoice data must not end up in console output or logs by accident")
                .check(CORE_CLASSES);
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(CORE_CLASSES);
    }
}
