package dev.michalraczkowski.fakturomost.cli;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import org.junit.jupiter.api.Test;

/**
 * Addendum §3.4: the CLI uses only {@code core.application} and domain types. The single exception is
 * the wiring class {@link CoreServices}, which has to construct the {@link Fa3Parser} the importer needs.
 *
 * <p>Same style as {@code CoreArchitectureTest}: plain ArchUnit, production classes only, imported once.
 */
class CliArchitectureTest {

    private static final String CLI = "dev.michalraczkowski.fakturomost.cli..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages("dev.michalraczkowski.fakturomost.cli");

    @Test
    void cliUsesOnlyApplicationLayerDomainPicocliAndJdk() {
        classes().that().resideInAPackage(CLI)
                .should().onlyDependOnClassesThat(resideInAnyPackage(
                                CLI,
                                "dev.michalraczkowski.fakturomost.core.application..",
                                "dev.michalraczkowski.fakturomost.core.domain..",
                                "picocli..",
                                "java..")
                        .or(describe("is Fa3Parser (wiring only, see next rule)",
                                (JavaClass c) -> c.isEquivalentTo(Fa3Parser.class))))
                .check(CLASSES);
    }

    @Test
    void onlyTheWiringClassConstructsTheParser() {
        noClasses().that().resideInAPackage(CLI).and().doNotHaveSimpleName("CoreServices")
                .should().dependOnClassesThat().resideInAPackage("dev.michalraczkowski.fakturomost.core.fa3..")
                .check(CLASSES);
    }

    @Test
    void noNetworkingSqlOrDesktopToolkits() {
        noClasses().that().resideInAPackage(CLI)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.net..", "javax.net..", "java.net.http..", "java.sql..", "javax.sql..",
                        "java.awt..", "javax.swing..", "java.util.logging..")
                .check(CLASSES);
    }

    @Test
    void onlyMainTouchesTheStandardStreams() {
        // Everything else writes to the CommandLine's out/err, which tests capture and which use the
        // console charset.
        noClasses().that().resideInAPackage(CLI).and().doNotHaveSimpleName("Main")
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .orShould().accessField(System.class, "in")
                .check(CLASSES);
    }
}
