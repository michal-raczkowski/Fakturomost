package dev.michalraczkowski.fakturomost.cli;

import dev.michalraczkowski.fakturomost.core.application.ImportResult;
import dev.michalraczkowski.fakturomost.core.application.InvoiceImporter;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Imports the paths given on the command line. Core's {@code importPaths} reads files only, so folders
 * are expanded here:
 * <ul>
 *   <li>A path named on the command line is always offered to core, whatever its extension (core
 *       detects ZIP archives by content and reports anything else).</li>
 *   <li>A folder is searched recursively, in name order, for files ending in {@code .xml},
 *       {@code .zip} or {@code .pdf} (any case). PDFs are included because a PDF downloaded from KSeF
 *       by mistake should get core's "download as XML" message, not be ignored. Other files
 *       ({@code README.md}, {@code desktop.ini}, …) are skipped and only counted.</li>
 *   <li>Symbolic links inside a folder are skipped, not followed: no loops, and nothing outside the
 *       named folder is read. A folder that leads back to one of its own parents (e.g. a Windows
 *       junction, which is not a symbolic link to the JDK) is skipped the same way.</li>
 *   <li>A folder that cannot be listed becomes a {@code Failed} result, like an unreadable file.</li>
 * </ul>
 * Results come in order, one per document, so nothing is dropped silently.
 */
final class InputFiles {

    private static final List<String> FOLDER_EXTENSIONS = List.of(".xml", ".zip", ".pdf");

    /**
     * @param filesRead    real paths of the files offered to core (explicit and from folders, existing
     *                     ones only), so a command can refuse to write over its own inputs
     * @param skippedFiles files inside folders that were not read because of their extension
     * @param skippedLinks symbolic links (and parent loops) inside folders that were not followed
     */
    record Outcome(List<ImportResult> results, Set<Path> filesRead, int skippedFiles, int skippedLinks) {
        Outcome {
            results = List.copyOf(results);
            filesRead = Set.copyOf(filesRead);
        }
    }

    private final InvoiceImporter importer;
    private final List<ImportResult> results = new ArrayList<>();
    private final Set<Path> filesRead = new HashSet<>();
    private int skippedFiles;
    private int skippedLinks;

    private InputFiles(InvoiceImporter importer) {
        this.importer = importer;
    }

    static Outcome importAll(InvoiceImporter importer, List<Path> inputs) {
        Objects.requireNonNull(importer, "importer");
        InputFiles run = new InputFiles(importer);
        for (Path input : inputs) {
            if (Files.isDirectory(input)) {
                run.walk(input, new HashSet<>());
            } else {
                run.importFile(input);
            }
        }
        return new Outcome(run.results, run.filesRead, run.skippedFiles, run.skippedLinks);
    }

    private void importFile(Path file) {
        results.addAll(importer.importPaths(List.of(file)));
        try {
            filesRead.add(file.toRealPath());
        } catch (IOException e) {
            // Missing or unreadable: core has reported it; nothing that could be overwritten.
        }
    }

    /** @param ancestors real paths of the folders above this one, to stop parent loops */
    private void walk(Path folder, Set<Path> ancestors) {
        List<Path> children = new ArrayList<>();
        Path realFolder;
        try {
            realFolder = folder.toRealPath();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
                stream.forEach(children::add);
            }
        } catch (IOException | DirectoryIteratorException e) {
            results.add(new ImportResult.Failed(displayName(folder),
                    "Could not read folder: " + e.getClass().getSimpleName(), e));
            return;
        }
        if (!ancestors.add(realFolder)) {
            skippedLinks++;
            return;
        }
        children.sort(Comparator.comparing(child -> child.getFileName().toString()));
        for (Path child : children) {
            if (Files.isSymbolicLink(child)) {
                skippedLinks++;
            } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                walk(child, ancestors);
            } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS) && hasFolderExtension(child)) {
                importFile(child);
            } else {
                skippedFiles++;
            }
        }
        ancestors.remove(realFolder);
    }

    private static boolean hasFolderExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return FOLDER_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /** Same rule as core: control characters in names must not break report lines. */
    static String displayName(Path path) {
        StringBuilder result = new StringBuilder();
        path.toString().codePoints().forEach(c -> result.appendCodePoint(Character.isISOControl(c) ? '?' : c));
        return result.toString();
    }
}
