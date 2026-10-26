package dev.michalraczkowski.fakturomost.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

class MainTest {

    /** Captured result of one CLI run. */
    private record Run(int exitCode, String out, String err) {
    }

    private static Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int exitCode = Main.run(args, new PrintWriter(out), new PrintWriter(err));
        return new Run(exitCode, out.toString(), err.toString());
    }

    @Test
    void versionGoesToStdoutAndExitsZero() {
        for (String option : new String[] {"--version", "-V"}) {
            Run run = run(option);

            assertEquals(ExitCodes.OK, run.exitCode(), option);
            assertTrue(run.out().startsWith("Fakturomost CLI " + CliVersion.version()), run.out());
            assertTrue(run.out().contains("Java " + System.getProperty("java.version")), run.out());
            assertEquals("", run.err(), option);
        }
    }

    @Test
    void versionResourceIsFilteredByMaven() {
        // Fails if resource filtering is switched off and the raw ${project.version} placeholder ships.
        assertTrue(CliVersion.version().matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"), CliVersion.version());
    }

    @Test
    void helpGoesToStdoutAndExitsZero() {
        for (String option : new String[] {"--help", "-h"}) {
            Run run = run(option);

            assertEquals(ExitCodes.OK, run.exitCode(), option);
            assertTrue(run.out().startsWith("Usage: fakturomost-cli"), run.out());
            // Help text wraps at the terminal width; compare with whitespace collapsed.
            assertTrue(run.out().replaceAll("\\s+", " ")
                    .contains("not affiliated with or endorsed by the Polish Ministry of Finance"), run.out());
            assertEquals("", run.err(), option);
        }
    }

    @Test
    void helpDocumentsEveryExitCode() {
        String help = run("--help").out();

        assertTrue(help.contains("Exit codes:"), help);
        assertTrue(help.matches("(?s).*\\R\\s+" + ExitCodes.OK + "\\s+Success\\..*"), help);
        assertTrue(help.matches("(?s).*\\R\\s+" + ExitCodes.USAGE + "\\s+Usage error.*"), help);
        assertTrue(help.matches("(?s).*\\R\\s+" + ExitCodes.SOME_DOCUMENTS_REJECTED + "\\s+Some documents.*"), help);
        assertTrue(help.matches("(?s).*\\R\\s+" + ExitCodes.FATAL + "\\s+I/O or unexpected error.*"), help);
    }

    @Test
    void exitCodesHaveTheDocumentedValues() {
        // Scripts depend on these numbers (addendum §4).
        assertEquals(0, ExitCodes.OK);
        assertEquals(1, ExitCodes.USAGE);
        assertEquals(2, ExitCodes.SOME_DOCUMENTS_REJECTED);
        assertEquals(3, ExitCodes.FATAL);
    }

    @Test
    void noArgumentsIsAUsageErrorReportedOnStderr() {
        Run run = run();

        assertEquals(ExitCodes.USAGE, run.exitCode());
        assertEquals("", run.out());
        assertTrue(run.err().startsWith("Missing command."), run.err());
        assertTrue(run.err().contains("Usage: fakturomost-cli"), run.err());
    }

    @Test
    void unknownOptionIsAUsageErrorWithSuggestion() {
        Run run = run("--versoin");

        assertEquals(ExitCodes.USAGE, run.exitCode());
        assertEquals("", run.out());
        assertTrue(run.err().startsWith("Error: Unknown option: '--versoin'"), run.err());
        assertTrue(run.err().contains("--version"), run.err());
        assertTrue(run.err().contains("Try 'fakturomost-cli --help'"), run.err());
        // Short report, not the whole help text.
        assertFalse(run.err().contains("Exit codes:"), run.err());
    }

    @Test
    void unexpectedParameterIsAUsageError() {
        Run run = run("invoice.xml");

        assertEquals(ExitCodes.USAGE, run.exitCode());
        assertEquals("", run.out());
        assertTrue(run.err().startsWith("Error: Unmatched argument"), run.err());
    }

    @Command(name = "boom")
    static final class FailingCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            throw new IllegalStateException("FV/2026/10/0001 gross 5460.00 seller 9999999999");
        }
    }

    @Test
    void unexpectedExceptionIsFatalAndPrintsNoDetails() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = Main.newCommandLine(new PrintWriter(out), new PrintWriter(err));
        commandLine.addSubcommand(new FailingCommand());

        int exitCode = commandLine.execute("boom");
        err.flush();

        assertEquals(ExitCodes.FATAL, exitCode);
        assertEquals("", out.toString());
        assertTrue(err.toString().startsWith("Error: unexpected IllegalStateException."), err.toString());
        assertFalse(err.toString().contains("FV/2026"), err.toString());
        assertFalse(err.toString().contains("9999999999"), err.toString());
        assertFalse(err.toString().contains("\tat "), "no stack trace: " + err);
    }
}
