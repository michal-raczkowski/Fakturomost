package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.core.application.ImportBatch;
import dev.michalraczkowski.fakturomost.core.application.ImportProgress;
import dev.michalraczkowski.fakturomost.core.application.ImportResult;
import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import dev.michalraczkowski.fakturomost.core.application.OtherDocument;
import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ExportFormat;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ExportScope;
import dev.michalraczkowski.fakturomost.desktop.ui.model.FilterInput;
import dev.michalraczkowski.fakturomost.desktop.ui.model.Formats;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ImportSummary;
import dev.michalraczkowski.fakturomost.desktop.ui.model.InvoiceDetails;
import dev.michalraczkowski.fakturomost.desktop.ui.model.InvoiceRow;
import dev.michalraczkowski.fakturomost.desktop.ui.model.Notices;
import dev.michalraczkowski.fakturomost.desktop.ui.model.OtherDocumentRow;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * The main window and the whole M4 chain: import (files, folder or drag &amp; drop) → results →
 * table → details → export selected.
 *
 * <p>Threading rule (master prompt §6): every call into the use cases runs in a {@link Task} on
 * {@link Background}; this class only touches controls, on the JavaFX thread. Only one operation
 * runs at a time — the buttons are disabled meanwhile.
 *
 * <p>Nothing here logs: messages show statuses, counts, file names and exception types, never
 * invoice contents.
 */
public final class MainWindow {

    private final Stage stage;
    private final UiServices services;
    private final Background background;

    private final InvoiceTable table = new InvoiceTable();
    private final DetailsPane details = new DetailsPane();
    private final FilterBar filters = new FilterBar(this::refreshTable);
    private final Label status = new Label("Ready.");
    private final Label selection = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button cancel = new Button("Cancel import");
    private final Hyperlink lastResults = new Hyperlink("Show import results");
    private final Button importFiles = new Button("Import files…");
    private final Button importFolder = new Button("Import folder…");
    private final Button exportButton = new Button("Export…");
    private final Hyperlink otherDocumentsLink = new Hyperlink();

    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private boolean busy;
    private ImportSummary lastSummary;
    private File lastDirectory;
    private ExportFormat lastFormat = ExportFormat.CSV_EXCEL_PL;
    /** Increases with every selection change, so a slow detail load cannot overwrite a newer one. */
    private long detailsGeneration;
    /** Same for searches: only the newest filter's result reaches the table. */
    private long searchGeneration;
    /** Whether the table currently shows a filtered subset (for the export dialog's wording). */
    private boolean filteredView;
    /** Stored documents in formats Fakturomost cannot read, as of the last refresh. */
    private List<OtherDocument> otherDocuments = List.of();

    private MainWindow(Stage stage, UiServices services, Background background) {
        this.stage = stage;
        this.services = services;
        this.background = background;
    }

    public static void show(Stage stage, UiServices services, Background background) {
        new MainWindow(stage, services, background).build();
    }

    // --- Layout ----------------------------------------------------------------------------------

    private void build() {
        importFiles.setOnAction(e -> chooseFilesToImport());
        importFolder.setOnAction(e -> chooseFolderToImport());
        exportButton.setOnAction(e -> export());
        Button about = new Button("About");
        about.setOnAction(e -> showAbout());
        importFolder.getStyleClass().add("primary"); // the one filled button: where most sessions start
        Label wordmark = new Label("Fakturomost");
        wordmark.getStyleClass().add("wordmark");
        Label version = new Label(services.product().version());
        version.getStyleClass().add("version");
        Region gap = new Region();
        gap.setMinWidth(16);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(Theme.mark(24), wordmark, version, gap, importFolder, importFiles, exportButton, spacer,
                about);
        header.getStyleClass().add("app-header");

        Label banner = new Label(Notices.BANNER);
        banner.getStyleClass().add("banner");
        Region info = new Region(); // shape and colour come from .info-icon in fakturomost.css
        info.getStyleClass().add("info-icon");
        banner.setGraphic(info);
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setWrapText(true);

        VBox list = new VBox(filters.view(), table.view());
        VBox.setVgrow(table.view(), Priority.ALWAYS);
        SplitPane split = new SplitPane(list, details.view());
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.45);

        progress.setPrefSize(18, 18);
        progress.setVisible(false);
        cancel.setVisible(false);
        cancel.setOnAction(e -> {
            cancelRequested.set(true);
            cancel.setDisable(true);
        });
        lastResults.setVisible(false);
        lastResults.setOnAction(e -> {
            if (lastSummary != null) {
                ImportResultsWindow.show(stage, lastSummary);
            }
        });
        // Hidden controls take no space; the status text is what shrinks when the bar is crowded.
        progress.managedProperty().bind(progress.visibleProperty());
        cancel.managedProperty().bind(cancel.visibleProperty());
        lastResults.managedProperty().bind(lastResults.visibleProperty());
        otherDocumentsLink.setVisible(false);
        otherDocumentsLink.managedProperty().bind(otherDocumentsLink.visibleProperty());
        otherDocumentsLink.setMinWidth(Region.USE_PREF_SIZE);
        otherDocumentsLink.setOnAction(e -> OtherDocumentsWindow.show(stage,
                otherDocuments.stream().map(OtherDocumentRow::of).toList(), this::exportOtherDocuments));
        selection.setMinWidth(Region.USE_PREF_SIZE);
        lastResults.setMinWidth(Region.USE_PREF_SIZE);
        cancel.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(status, Priority.ALWAYS);
        status.setMaxWidth(Double.MAX_VALUE);
        HBox statusBar = new HBox(8, progress, status, selection, otherDocumentsLink, lastResults, cancel);
        statusBar.getStyleClass().add("status-bar");

        BorderPane root = new BorderPane(split, new VBox(header, banner), null, statusBar, null);
        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets().add(Theme.STYLESHEET);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::chooseFilesToImport);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN), this::export);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), filters::focusSearch);
        acceptDroppedFiles(scene);

        table.view().getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> showDetails(row));
        table.view().getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> updateControls());
        updateControls();
        refreshTable();

        stage.setTitle(services.product().displayName());
        stage.getIcons().setAll(Theme.windowIcons());
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.show();
    }

    private void acceptDroppedFiles(Scene scene) {
        scene.setOnDragOver(event -> {
            if (!busy && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        scene.setOnDragDropped(event -> {
            boolean accepted = !busy && event.getDragboard().hasFiles();
            if (accepted) {
                startImport(event.getDragboard().getFiles().stream().map(File::toPath).toList());
            }
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    private void updateControls() {
        int selected = table.view().getSelectionModel().getSelectedIndices().size();
        importFiles.setDisable(busy);
        importFolder.setDisable(busy);
        exportButton.setDisable(busy || table.size() == 0);
        selection.setText(selected == 0 ? "" : selected + (selected == 1 ? " invoice" : " invoices") + " selected");
    }

    private void setBusy(boolean value, boolean cancellable) {
        busy = value;
        progress.setVisible(value);
        cancel.setVisible(value && cancellable);
        cancel.setDisable(false);
        updateControls();
    }

    // --- Import ----------------------------------------------------------------------------------

    private void chooseFilesToImport() {
        if (busy) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import FA(3) invoice files");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("KSeF invoices (*.xml, *.zip)", "*.xml", "*.XML", "*.zip", "*.ZIP"),
                new FileChooser.ExtensionFilter("All files", "*"));
        initialDirectory(chooser::setInitialDirectory);
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            lastDirectory = files.getFirst().getParentFile();
            startImport(files.stream().map(File::toPath).toList());
        }
    }

    private void chooseFolderToImport() {
        if (busy) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Import a folder (searched recursively for .xml, .zip and .pdf files)");
        initialDirectory(chooser::setInitialDirectory);
        File folder = chooser.showDialog(stage);
        if (folder != null) {
            lastDirectory = folder;
            startImport(List.of(folder.toPath()));
        }
    }

    private void startImport(List<Path> inputs) {
        if (busy || inputs.isEmpty()) {
            return;
        }
        cancelRequested.set(false);
        Task<ImportSummary> task = new Task<>() {
            @Override
            protected ImportSummary call() {
                updateMessage("Importing…");
                ImportBatch batch = services.importer().importInputs(inputs, new ImportProgress() {
                    @Override
                    public void resultAdded(ImportResult result, int resultsSoFar) {
                        updateMessage("Importing… " + resultsSoFar + (resultsSoFar == 1 ? " document" : " documents") + " read");
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelRequested.get();
                    }
                });
                return ImportSummary.of(batch);
            }
        };
        setBusy(true, true);
        status.textProperty().bind(task.messageProperty());
        background.run(task, summary -> {
            status.textProperty().unbind();
            setBusy(false, false);
            refreshTable(); // through the current filters
            lastSummary = summary;
            lastResults.setVisible(true);
            status.setText(summary.headline());
            ImportResultsWindow.show(stage, summary);
        }, error -> {
            status.textProperty().unbind();
            setBusy(false, false);
            status.setText("Import failed.");
            showError("Import failed", "The import stopped because of an unexpected error ("
                    + error.getClass().getSimpleName() + "). Invoices imported before it are kept.");
        });
    }

    // --- Table and filters -----------------------------------------------------------------------

    /** Runs the current filters in the background; invalid input is shown and the table kept as is. */
    private void refreshTable() {
        FilterInput input = filters.input();
        switch (input.toQuery()) {
            case FilterInput.Result.Invalid invalid -> filters.showProblems(invalid.problems());
            case FilterInput.Result.Valid valid -> {
                filters.clearProblems();
                search(valid.query(), !input.isEmpty());
            }
        }
    }

    private void search(InvoiceQuery query, boolean filtered) {
        long generation = ++searchGeneration;
        Task<SearchOutcome> task = new Task<>() {
            @Override
            protected SearchOutcome call() {
                List<InvoiceRow> rows = services.catalog().search(query).stream().map(InvoiceRow::of).toList();
                int total = filtered ? services.catalog().search(InvoiceQuery.all()).size() : rows.size();
                return new SearchOutcome(rows, total, services.catalog().otherDocuments());
            }
        };
        background.run(task, outcome -> {
            if (generation == searchGeneration) {
                table.setRows(outcome.rows(), filtered && outcome.total() > 0);
                filteredView = filtered;
                otherDocuments = outcome.otherDocuments();
                otherDocumentsLink.setText(OtherDocumentRow.linkText(otherDocuments.size()));
                otherDocumentsLink.setVisible(!otherDocuments.isEmpty());
                filters.setCount(outcome.total() == 0 ? "" : FilterInput.countText(outcome.rows().size(), outcome.total(), filtered));
                updateControls();
            }
        }, error -> {
            if (generation == searchGeneration) {
                filters.setCount("Search failed (" + error.getClass().getSimpleName() + ")");
            }
        });
    }

    private record SearchOutcome(List<InvoiceRow> rows, int total, List<OtherDocument> otherDocuments) {
    }

    // --- Details ---------------------------------------------------------------------------------

    private void showDetails(InvoiceRow row) {
        long generation = ++detailsGeneration;
        if (row == null) {
            details.showMessage("Select an invoice to see its details.");
            return;
        }
        Task<InvoiceDetails> task = new Task<>() {
            @Override
            protected InvoiceDetails call() {
                return InvoiceDetails.of(services.catalog().find(row.entry().id()).orElseThrow());
            }
        };
        background.run(task, loaded -> {
            if (generation == detailsGeneration) {
                details.show(loaded);
            }
        }, error -> {
            if (generation == detailsGeneration) {
                details.showMessage("Could not load the details (" + error.getClass().getSimpleName() + ").");
            }
        });
    }

    // --- Export ----------------------------------------------------------------------------------

    /** "Export…": the selected rows or all rows the filters show, in any format. */
    private void export() {
        if (busy || table.size() == 0) {
            return;
        }
        List<InvoiceRow> selected = table.selectedInDisplayOrder();
        List<InvoiceRow> shown = table.shownInDisplayOrder();
        Optional<ExportDialog.Choice> choice =
                ExportDialog.chooseForInvoices(stage, selected.size(), shown.size(), filteredView, lastFormat);
        if (choice.isEmpty()) {
            return;
        }
        ExportFormat format = choice.get().format();
        lastFormat = format;
        List<InvoiceEntry> invoices = (choice.get().scope() == ExportScope.SELECTED ? selected : shown).stream()
                .map(InvoiceRow::entry).toList();
        Path location = chooseExportLocation(format);
        if (location == null) {
            return;
        }
        Path target = format.normalizeTarget(location);
        runExport(target, format, format.doneMessage(invoices.size(), target), false,
                overwrite -> format.export(services.exports(), invoices, target, overwrite));
    }

    /** The originals of every document in another format (FA(2), ...), as ZIP or into a folder. */
    private void exportOtherDocuments() {
        List<OtherDocument> documents = otherDocuments;
        if (busy || documents.isEmpty()) {
            return;
        }
        Optional<ExportFormat> chosen = ExportDialog.chooseForOriginals(stage, documents.size());
        if (chosen.isEmpty()) {
            return;
        }
        ExportFormat format = chosen.get();
        Path location = chooseExportLocation(format);
        if (location == null) {
            return;
        }
        Path target = format.normalizeTarget(location);
        List<DocumentId> ids = documents.stream().map(OtherDocument::id).toList();
        runExport(target, format, format.doneMessage(documents.size(), "document", target), false,
                overwrite -> services.exports().exportOriginals(ids, target, overwrite));
    }

    /** One export attempt; {@code overwrite} is only ever {@code true} after the user said "Replace". */
    @FunctionalInterface
    private interface ExportJob {
        List<Path> write(boolean overwrite) throws IOException;
    }

    private Path chooseExportLocation(ExportFormat format) {
        File chosen;
        if (format.writesFolder()) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Export original XML into a folder");
            initialDirectory(chooser::setInitialDirectory);
            chosen = chooser.showDialog(stage);
            if (chosen != null) {
                lastDirectory = chosen;
            }
        } else {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export as " + format.label());
            chooser.setInitialFileName(format.suggestedFileName(LocalDate.now()));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(format.fileDescription() + " (*" + format.extension() + ")",
                            "*" + format.extension()));
            initialDirectory(chooser::setInitialDirectory);
            chosen = chooser.showSaveDialog(stage);
            if (chosen != null) {
                lastDirectory = chosen.getParentFile();
            }
        }
        return chosen == null ? null : chosen.toPath();
    }

    /**
     * Never replaces anything on the first try; if core reports an existing target, the user is asked
     * and only a "Replace" answer repeats the export with {@code overwrite = true}.
     */
    private void runExport(Path target, ExportFormat format, String done, boolean overwrite, ExportJob job) {
        Task<List<Path>> task = new Task<>() {
            @Override
            protected List<Path> call() throws Exception {
                return job.write(overwrite);
            }
        };
        setBusy(true, false);
        status.setText("Exporting…");
        background.run(task, written -> {
            setBusy(false, false);
            status.setText(done);
            Alert alert = alert(Alert.AlertType.INFORMATION, "Export finished", done);
            alert.setHeaderText("Export finished");
            Label notice = wrapped(ExportFormat.NOT_ENCRYPTED_NOTICE);
            notice.getStyleClass().add("warning");
            alert.getDialogPane().setContent(new VBox(12, wrapped(done), notice));
            alert.showAndWait();
        }, error -> {
            setBusy(false, false);
            if (error instanceof FileAlreadyExistsException && !overwrite) {
                status.setText("The target already exists — waiting for your answer.");
                if (confirmReplace(format.overwriteQuestion(target))) {
                    runExport(target, format, done, true, job);
                } else {
                    status.setText("Export cancelled; nothing was replaced.");
                }
                return;
            }
            status.setText("Export failed.");
            String where = error instanceof FileSystemException fs && fs.getFile() != null
                    ? " — " + Formats.singleLine(fs.getFile()) : "";
            showError("Export failed", "The export could not be completed (" + error.getClass().getSimpleName() + where
                    + ")." + (format.writesFolder() ? " Some files may already have been written to the folder." : ""));
        });
    }

    private boolean confirmReplace(String question) {
        // The keyboard must never overwrite. Three layers, because DialogPane.updateButtons() re-applies
        // the default button from each ButtonData whenever it lays the buttons out (JavaFX 21 source):
        //  1. Replace is OTHER, not OK_DONE, so DialogPane never makes it the default.
        //  2. Replace is not focus-traversable, so Enter/Space on a focused button (Windows, Linux)
        //     can't reach it — it takes a mouse click.
        //  3. Cancel gets the keyboard focus once the dialog is showing.
        // Checked in the sandbox driver: Enter closes the question and the file is unchanged.
        ButtonType replace = new ButtonType("Replace", ButtonBar.ButtonData.OTHER);
        Alert alert = alert(Alert.AlertType.CONFIRMATION, "Replace existing files?", question);
        alert.setHeaderText("Replace existing files?");
        alert.getButtonTypes().setAll(ButtonType.CANCEL, replace);
        alert.getDialogPane().lookupButton(replace).setFocusTraversable(false);
        alert.setOnShown(event -> Platform.runLater(() -> alert.getDialogPane().lookupButton(ButtonType.CANCEL).requestFocus()));
        return alert.showAndWait().filter(replace::equals).isPresent();
    }

    // --- About and messages ----------------------------------------------------------------------

    private void showAbout() {
        Alert alert = alert(Alert.AlertType.INFORMATION, "About Fakturomost", "");
        alert.setHeaderText(services.product().displayName());
        TextArea text = new TextArea(Notices.ABOUT + "\n\nLicense: Apache License 2.0.");
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefRowCount(18);
        text.setPrefColumnCount(60);
        VBox content = new VBox(12, Theme.mark(64), text);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = alert(Alert.AlertType.ERROR, title, message);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private Alert alert(Alert.AlertType type, String title, String text) {
        Alert alert = new Alert(type);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        // A wrapped label instead of setContentText: the default content label may cut long text
        // (paths!) with "..." — and the "not encrypted" notice must never be cut.
        alert.getDialogPane().setContent(wrapped(text));
        Theme.style(alert);
        alert.setResizable(true);
        return alert;
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setPrefWidth(520);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private void initialDirectory(Consumer<File> setter) {
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            setter.accept(lastDirectory);
        }
    }
}
