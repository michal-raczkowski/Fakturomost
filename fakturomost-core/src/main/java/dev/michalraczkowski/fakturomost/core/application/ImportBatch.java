package dev.michalraczkowski.fakturomost.core.application;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Everything one {@link InvoiceImporter#importInputs} call did.
 *
 * @param results      one per document, in order (see {@link InvoiceImporter#importInputs})
 * @param filesRead    real paths of the files that were read (named ones and those found in folders;
 *                     existing ones only), so a front end can refuse to write over its own inputs
 * @param skippedFiles files inside folders that were not read because of their extension
 * @param skippedLinks symbolic links (and folders leading back to a parent) inside folders that were
 *                     not followed
 * @param cancelled    {@code true} if {@link ImportProgress#isCancelled()} stopped the import before
 *                     every input was read; the results so far are still complete and stored
 */
public record ImportBatch(
        List<ImportResult> results, Set<Path> filesRead, int skippedFiles, int skippedLinks, boolean cancelled) {

    public ImportBatch {
        results = List.copyOf(results);
        filesRead = Set.copyOf(filesRead);
        if (skippedFiles < 0 || skippedLinks < 0) {
            throw new IllegalArgumentException("Counts must not be negative");
        }
    }
}
