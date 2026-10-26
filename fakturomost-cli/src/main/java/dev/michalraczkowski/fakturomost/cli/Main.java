package dev.michalraczkowski.fakturomost.cli;

import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * Entry point and the CLI's only wiring code: plain Java, no Spring (ADR 0006).
 *
 * <p>Output rules (addendum §4): help and version go to stdout because the user asked for them;
 * every diagnostic goes to stderr; invoice data is only ever written to the {@code --out} file.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, consoleWriter(System.out), consoleWriter(System.err));
        System.exit(exitCode);
    }

    /** Runs the CLI without exiting the JVM; used by {@link #main} and by tests. */
    static int run(String[] args, PrintWriter out, PrintWriter err) {
        try {
            return newCommandLine(out, err).execute(args);
        } finally {
            out.flush();
            err.flush();
        }
    }

    static CommandLine newCommandLine(PrintWriter out, PrintWriter err) {
        CommandLine commandLine = new CommandLine(new FakturomostCli());
        commandLine.setOut(out);
        commandLine.setErr(err);
        // The handlers write to the given err directly, so subcommands added later cannot fall back to System.err.
        commandLine.setParameterExceptionHandler(usageErrorHandler(err));
        commandLine.setExecutionExceptionHandler(unexpectedErrorHandler(err));
        return commandLine;
    }

    /** Short usage-error report instead of the full help text; the user can ask for --help. */
    private static IParameterExceptionHandler usageErrorHandler(PrintWriter err) {
        return (exception, args) -> {
            CommandLine commandLine = exception.getCommandLine();
            err.println("Error: " + exception.getMessage());
            UnmatchedArgumentException.printSuggestions(exception, err);
            err.println("Try '" + commandLine.getCommandSpec().qualifiedName() + " --help' for more information.");
            return ExitCodes.USAGE;
        };
    }

    /**
     * Last-resort handler. Prints only the exception type, never its message or stack trace: messages
     * from lower layers may contain file names or invoice values, which must not reach logs (addendum §4).
     * Expected problems (unreadable input, existing output) are reported by the commands themselves.
     */
    private static IExecutionExceptionHandler unexpectedErrorHandler(PrintWriter err) {
        return (exception, commandLine, parseResult) -> {
            err.println("Error: unexpected " + exception.getClass().getSimpleName()
                    + ". No details are shown because they may contain invoice data.");
            return ExitCodes.FATAL;
        };
    }

    /**
     * Wraps a standard stream with the console's own charset ({@link PrintStream#charset()}, JDK 18+).
     * A plain {@code new PrintWriter(System.out)} would encode with the default charset (UTF-8 since
     * JDK 18), which garbles Polish characters on a Windows console using a legacy code page.
     */
    private static PrintWriter consoleWriter(PrintStream stream) {
        return new PrintWriter(new OutputStreamWriter(stream, stream.charset()), true);
    }
}
