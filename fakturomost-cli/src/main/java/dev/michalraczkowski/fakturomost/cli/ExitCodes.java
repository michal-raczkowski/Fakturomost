package dev.michalraczkowski.fakturomost.cli;

/**
 * Documented process exit codes (addendum §4). Scripts rely on these values; never renumber.
 */
public final class ExitCodes {

    /** Everything requested was done. */
    public static final int OK = 0;

    /** Bad command line: unknown option, missing argument, no command given. */
    public static final int USAGE = 1;

    /** Ran to the end, but at least one document was invalid or unsupported. */
    public static final int SOME_DOCUMENTS_REJECTED = 2;

    /** I/O problem or unexpected error; the requested output may be missing. */
    public static final int FATAL = 3;

    private ExitCodes() {
    }
}
