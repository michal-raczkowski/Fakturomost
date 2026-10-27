package dev.michalraczkowski.fakturomost.desktop;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.GeneralCodingRules;
import dev.michalraczkowski.fakturomost.core.ProductInfo;
import dev.michalraczkowski.fakturomost.core.application.DocumentStore;
import dev.michalraczkowski.fakturomost.core.application.InMemoryDocumentStore;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import org.junit.jupiter.api.Test;

/**
 * Addendum §3.4 for the desktop module (deferred from M2/M3 until UI packages existed): the UI uses
 * only the application layer and domain types — never the store or the parser — and does not depend
 * on the CLI. Same style as {@code CoreArchitectureTest}: plain ArchUnit, production classes only.
 */
class DesktopArchitectureTest {

    private static final String DESKTOP = "dev.michalraczkowski.fakturomost.desktop";
    private static final String UI = DESKTOP + ".ui..";
    private static final String UI_MODEL = DESKTOP + ".ui.model..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(DESKTOP);

    @Test
    void uiUsesOnlyTheApplicationLayerDomainJavaFxAndTheJdk() {
        classes().that().resideInAPackage(UI)
                .should().onlyDependOnClassesThat(resideInAnyPackage(
                                UI,
                                "dev.michalraczkowski.fakturomost.core.application..",
                                "dev.michalraczkowski.fakturomost.core.domain..",
                                "javafx..",
                                "java..")
                        .or(describe("is ProductInfo", (JavaClass c) -> c.isEquivalentTo(ProductInfo.class))))
                .because("the UI is a front end over the use cases (addendum §3.2); Spring, the parser and "
                        + "storage stay in the wiring classes")
                .check(CLASSES);
    }

    @Test
    void uiNeverTouchesTheStoreDirectly() {
        noClasses().that().resideInAPackage(UI)
                .should().dependOnClassesThat().belongToAnyOf(DocumentStore.class, InMemoryDocumentStore.class)
                .because("the store is a repository port; the UI goes through InvoiceCatalog / InvoiceExports, "
                        + "so the encrypted store of M5 can replace it without UI changes")
                .check(CLASSES);
    }

    @Test
    void viewModelsAreToolkitFree() {
        noClasses().that().resideInAPackage(UI_MODEL)
                .should().dependOnClassesThat().resideInAPackage("javafx..")
                .because("formatting and export mapping are unit-tested without a JavaFX toolkit")
                .check(CLASSES);
    }

    @Test
    void desktopDoesNotDependOnTheCli() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("dev.michalraczkowski.fakturomost.cli..", "picocli..")
                .because("neither front end depends on the other (addendum §3.4)")
                .check(CLASSES);
    }

    @Test
    void desktopOpensNoNetworkConnections() {
        // java.net.URL itself is allowed: JavaFX takes stylesheets as classpath resource URLs.
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.net.http..", "javax.net..")
                .orShould().dependOnClassesThat().belongToAnyOf(
                        URLConnection.class, Socket.class, ServerSocket.class, DatagramSocket.class)
                .orShould().callMethod(URL.class, "openConnection")
                .orShould().callMethod(URL.class, "openStream")
                .because("no KSeF connectivity before M8 and no listening sockets ever (ADR 0003); "
                        + "NoListeningSocketsTest checks the running context")
                .check(CLASSES);
    }

    @Test
    void desktopDoesNotWriteToTheConsoleOrJavaUtilLogging() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                .because("invoice data must not end up in console output or logs by accident")
                .check(CLASSES);
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(CLASSES);
    }
}
