package dev.michalraczkowski.fakturomost.core.application;

/**
 * Optional observer of {@link InvoiceImporter#importInputs(java.util.List, ImportProgress)}: hears
 * about each result as it is produced and can stop the import early.
 *
 * <p>Both methods are called on the importing thread, so a UI must hand results over to its own
 * thread itself (e.g. JavaFX {@code Task.updateMessage}). Implementations must be quick and must not
 * throw.
 */
public interface ImportProgress {

    /** Observes nothing and never cancels. */
    ImportProgress NONE = new ImportProgress() {
    };

    /**
     * One more result, in the order of the final {@link ImportBatch#results()}.
     *
     * @param result     the new result
     * @param resultsSoFar how many results there are now, including this one
     */
    default void resultAdded(ImportResult result, int resultsSoFar) {
    }

    /**
     * Asked before each file and before each document of a ZIP. Once it returns {@code true} the
     * import stops: documents already imported stay in the store, nothing further is read, and the
     * batch is marked {@link ImportBatch#cancelled()}.
     */
    default boolean isCancelled() {
        return false;
    }
}
