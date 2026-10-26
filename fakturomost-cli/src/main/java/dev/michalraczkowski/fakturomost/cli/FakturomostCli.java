package dev.michalraczkowski.fakturomost.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Top-level command. Subcommands are registered in {@link Main#newCommandLine}: {@code inspect} now,
 * {@code convert} in a later M3 task.
 *
 * <p>Exit codes for usage errors and unexpected exceptions are set by the handlers in {@link Main}, not by
 * {@code exitCodeOnInvalidInput}/{@code exitCodeOnExecutionException}: those annotation attributes are
 * per command (picocli defaults 2 and 1), so every future subcommand would have to repeat them.
 */
@Command(
        name = "fakturomost-cli",
        mixinStandardHelpOptions = true,
        versionProvider = CliVersion.class,
        sortOptions = false,
        usageHelpAutoWidth = true,
        description = {
                "Inspect and convert KSeF FA(3) invoice files (XML or ZIP) offline.",
                "Independent, experimental open-source tool; not affiliated with or endorsed by the Polish Ministry of Finance."
        },
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
                "0:Success.",
                "1:Usage error (unknown option, missing argument, no command).",
                "2:Some documents were invalid or unsupported.",
                "3:I/O or unexpected error."
        })
final class FakturomostCli implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    /** No command given: show usage on stderr and report a usage error. */
    @Override
    public Integer call() {
        spec.commandLine().getErr().println("Missing command.");
        spec.commandLine().usage(spec.commandLine().getErr());
        return ExitCodes.USAGE;
    }
}
