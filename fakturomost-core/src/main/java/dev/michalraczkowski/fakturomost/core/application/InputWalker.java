package dev.michalraczkowski.fakturomost.core.application;

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
import java.util.Set;
import java.util.function.Consumer;

/**
 * Expands the inputs of {@link InvoiceImporter#importInputs} into files. Moved here from the CLI
 * (M4) so the desktop app and the CLI select exactly the same files; the rules are documented on
 * {@code importInputs}.
 *
 * <p>Single use: one instance per call.
 */
final class InputWalker {

    private static final List<String> FOLDER_EXTENSIONS = List.of(".xml", ".zip", ".pdf");

    private final Consumer<Path> readFile;
    private final Consumer<ImportResult> reject;
    private final Set<Path> filesRead = new HashSet<>();
    private int skippedFiles;
    private int skippedLinks;

    /**
     * @param readFile reads one file and reports its documents
     * @param reject   receives a {@code Failed} result for a folder that cannot be listed
     */
    InputWalker(Consumer<Path> readFile, Consumer<ImportResult> reject) {
        this.readFile = readFile;
        this.reject = reject;
    }

    void walkAll(List<Path> inputs) {
        for (Path input : inputs) {
            if (Files.isDirectory(input)) {
                walk(input, new HashSet<>());
            } else {
                file(input);
            }
        }
    }

    Set<Path> filesRead() {
        return filesRead;
    }

    int skippedFiles() {
        return skippedFiles;
    }

    int skippedLinks() {
        return skippedLinks;
    }

    private void file(Path file) {
        readFile.accept(file);
        try {
            filesRead.add(file.toRealPath());
        } catch (IOException e) {
            // Missing or unreadable: the reader has reported it; nothing that could be overwritten.
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
            reject.accept(new ImportResult.Failed(SourceFileReader.displayName(folder.toString()),
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
                file(child);
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
}
