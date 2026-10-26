package dev.michalraczkowski.fakturomost.cli;

import dev.michalraczkowski.fakturomost.core.application.ImportResult;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code inspect <path...>}: imports in memory and prints one status line per document plus a summary.
 *
 * <p>The report is the command's result, so it goes to stdout (scripts can pipe or grep it); notes go
 * to stderr. Line format: {@link ImportReport}.
 */
@Command(
        name = "inspect",
        mixinStandardHelpOptions = true,
        versionProvider = CliVersion.class,
        description = {
                "Check FA(3) invoice files without writing anything.",
                "Prints one line per document (Imported, Duplicate, Unsupported, Invalid or Failed) and a summary.",
                "Lines show file names, KSeF numbers and reasons only, never invoice contents."
        },
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
                "0:Every document is a valid FA(3) invoice (duplicates included).",
                "1:Usage error.",
                "2:Some documents were invalid or unsupported.",
                "3:Some files or folders could not be read, or an unexpected error occurred."
        })
final class InspectCommand implements Callable<Integer> {

    @Parameters(
            arity = "1..*",
            paramLabel = "<path>",
            description = "XML or ZIP files, or folders (searched recursively for .xml, .zip and .pdf files; "
                    + "symbolic links are not followed).")
    private List<Path> paths;

    @Spec
    private CommandSpec spec;

    private final Supplier<CoreServices> services;

    InspectCommand(Supplier<CoreServices> services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        InputFiles.Outcome outcome = InputFiles.importAll(services.get().importer(), paths);
        List<ImportResult> results = outcome.results();

        ImportReport.printLines(results, result -> true, out);
        ImportReport.Summary summary = ImportReport.Summary.of(results);
        out.println(summary.text());
        ImportReport.printNotes(outcome, err);
        return summary.exitCode();
    }
}
