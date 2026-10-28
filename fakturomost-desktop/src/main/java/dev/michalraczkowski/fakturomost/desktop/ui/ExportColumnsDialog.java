package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.core.application.ExportColumn;
import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceExports;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ColumnLayout;
import dev.michalraczkowski.fakturomost.desktop.ui.model.CsvSettings;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ExportFields;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ExportSetup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Step 2 of a CSV or chosen-fields JSON export: which invoice fields become columns, in which order,
 * under which names, plus (CSV) the file settings. A live preview shows the start of the file
 * exactly as core writes it.
 *
 * <p>The preview formats the first few invoices of the export in memory on the JavaFX thread. That
 * is the one export call not run on {@link Background}: it touches neither the store nor the disk
 * and handles at most {@value #PREVIEW_ROWS} already-loaded invoices.
 */
final class ExportColumnsDialog {

    static final int PREVIEW_ROWS = 3;

    /** How the dialog was left. {@code setup} is the state at that moment, also for {@code BACK}. */
    record Result(Outcome outcome, ExportSetup setup) {
    }

    enum Outcome { CONTINUE, BACK }

    /** One exported column while the dialog is open; the name is edited in place in the table. */
    private static final class Row {
        final ExportColumn field;
        final StringProperty name;

        Row(ColumnLayout.Column column) {
            this.field = column.field();
            this.name = new SimpleStringProperty(column.name());
        }
    }

    private final ExportSetup start;
    private final InvoiceExports exports;
    private final List<InvoiceEntry> sample;

    private ColumnLayout.Names names;
    private CsvSettings csv;
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final ObservableList<ExportColumn> available = FXCollections.observableArrayList();

    private final ListView<ExportColumn> availableList = new ListView<>(available);
    private final TableView<Row> table = new TableView<>(rows);
    private final Label hint = new Label(" ");
    private final Label problems = new Label();
    private final TextArea preview = new TextArea();
    private final Label countLabel = new Label();
    private Button continueButton;

    private ExportColumnsDialog(ExportSetup start, InvoiceExports exports, List<InvoiceEntry> invoices) {
        this.start = start;
        this.exports = exports;
        this.sample = List.copyOf(invoices.subList(0, Math.min(PREVIEW_ROWS, invoices.size())));
        this.names = start.layout().names();
        this.csv = start.csv();
    }

    /**
     * @param setup    the remembered (or default) setup of a format {@linkplain
     *                 dev.michalraczkowski.fakturomost.desktop.ui.model.ExportFormat#hasColumns() with columns}
     * @param invoices the invoices about to be exported, in order; the first few are previewed
     * @return empty if cancelled
     */
    static Optional<Result> show(Window owner, ExportSetup setup, InvoiceExports exports, List<InvoiceEntry> invoices) {
        return new ExportColumnsDialog(setup, exports, invoices).show(owner, invoices.size());
    }

    private Optional<Result> show(Window owner, int invoiceCount) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Export invoices");
        dialog.setHeaderText("Columns — " + start.format().label());
        dialog.setResizable(true);
        Theme.style(dialog);

        VBox content = new VBox(14);
        content.setPadding(new Insets(6, 0, 0, 0));
        if (csv != null) {
            content.getChildren().add(csvSettings());
        }
        Node lists = lists();
        content.getChildren().addAll(lists, hint, problems, previewBox(invoiceCount));
        VBox.setVgrow(lists, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        // Fits a 1366×768 laptop screen; the lists take whatever the window gives them.
        dialog.getDialogPane().setPrefSize(900, 680);
        dialog.getDialogPane().setMinSize(760, 560);

        ButtonType back = new ButtonType("Back", ButtonBar.ButtonData.BACK_PREVIOUS);
        ButtonType next = new ButtonType("Choose location…", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(back, next, ButtonType.CANCEL);
        continueButton = (Button) dialog.getDialogPane().lookupButton(next);
        dialog.setResultConverter(button -> button == next ? new Result(Outcome.CONTINUE, current())
                : button == back ? new Result(Outcome.BACK, current())
                : null);

        load(start.layout());
        return dialog.showAndWait();
    }

    // --- Parts -----------------------------------------------------------------------------------

    private Node csvSettings() {
        ComboBox<CsvSettings.Choice> separator = choiceBox(CsvSettings.SEPARATORS, csv.separator());
        ComboBox<CsvSettings.Choice> decimal = choiceBox(CsvSettings.DECIMAL_SEPARATORS, csv.decimalSeparator());
        CheckBox header = new CheckBox("Column names in the first row");
        header.setSelected(csv.headerRow());
        CheckBox bom = new CheckBox("UTF-8 BOM (Excel needs it for Polish letters)");
        bom.setSelected(csv.byteOrderMark());
        CheckBox crlf = new CheckBox("Windows line endings (CRLF)");
        crlf.setSelected(csv.crlf());

        separator.valueProperty().addListener((o, a, b) -> changeCsv(csv.withSeparator(b.value())));
        decimal.valueProperty().addListener((o, a, b) -> changeCsv(csv.withDecimalSeparator(b.value())));
        header.selectedProperty().addListener((o, a, b) -> changeCsv(csv.withHeaderRow(b)));
        bom.selectedProperty().addListener((o, a, b) -> changeCsv(csv.withByteOrderMark(b)));
        crlf.selectedProperty().addListener((o, a, b) -> changeCsv(csv.withCrlf(b)));

        HBox choices = new HBox(10, new Label("Separator"), separator, gap(14), new Label("Decimal sign"), decimal);
        choices.setAlignment(Pos.CENTER_LEFT);
        HBox checks = new HBox(22, header, bom, crlf);
        checks.setAlignment(Pos.CENTER_LEFT);
        return new VBox(8, section("CSV file"), choices, checks);
    }

    private Node namesChoice() {
        ComboBox<ColumnLayout.Names> defaultNames = new ComboBox<>(FXCollections.observableArrayList(ColumnLayout.Names.values()));
        defaultNames.setValue(names);
        defaultNames.setButtonCell(namesCell());
        defaultNames.setCellFactory(list -> namesCell());
        defaultNames.valueProperty().addListener((o, a, b) -> {
            names = b;
            load(current().layout().withNames(b));
        });
        defaultNames.setTooltip(new Tooltip("Names for new columns and for \"Default names\". "
                + "Columns you renamed keep your name."));
        HBox row = new HBox(8, new Label("Default names"), defaultNames);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node lists() {
        availableList.getStyleClass().add("field-list");
        availableList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        availableList.setCellFactory(list -> new FieldCell());
        availableList.setPlaceholder(new Label("Every field is exported."));
        availableList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                addSelected();
            }
        });
        availableList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                addSelected();
                e.consume();
            }
        });
        availableList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showHint(b));
        availableList.focusedProperty().addListener((o, a, b) -> {
            if (b) {
                showHint(availableList.getSelectionModel().getSelectedItem());
            }
        });

        table.getStyleClass().add("column-editor");
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label("No columns — add fields from the left."));
        TableColumn<Row, Row> number = new TableColumn<>("#");
        number.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        number.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                setText(empty || row == null ? null : String.valueOf(getIndex() + 1));
            }
        });
        number.setPrefWidth(40);
        number.setMinWidth(36);
        number.setSortable(false);
        number.getStyleClass().add("numeric");
        TableColumn<Row, Row> field = new TableColumn<>("Field");
        field.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        field.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                setText(empty || row == null ? null : ExportFields.label(row.field));
                setTooltip(empty || row == null ? null : new Tooltip(ExportFields.hint(row.field)));
            }
        });
        field.setPrefWidth(180);
        field.setMinWidth(120);
        field.setSortable(false);
        TableColumn<Row, Row> name = new TableColumn<>("Name in the file");
        name.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        name.setCellFactory(c -> new NameCell());
        name.setPrefWidth(260);
        name.setMinWidth(140);
        name.setSortable(false);
        table.getColumns().add(number);
        table.getColumns().add(field);
        table.getColumns().add(name);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showHint(b == null ? null : b.field));
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                removeSelected();
                e.consume();
            }
        });

        Button add = new Button("Add →");
        add.setOnAction(e -> addSelected());
        add.disableProperty().bind(availableList.getSelectionModel().selectedItemProperty().isNull());
        Button remove = new Button("← Remove");
        remove.setOnAction(e -> removeSelected());
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        Button addAll = new Button("Add all");
        addAll.setOnAction(e -> load(current().layout().addAll()));
        Button removeAll = new Button("Remove all");
        removeAll.setOnAction(e -> load(current().layout().removeAll()));
        VBox middle = new VBox(8, add, remove, new Region(), addAll, removeAll);
        middle.setAlignment(Pos.CENTER);
        middle.getChildren().forEach(n -> {
            if (n instanceof Button b) {
                b.setMaxWidth(Double.MAX_VALUE);
                b.setMinWidth(Region.USE_PREF_SIZE);
            }
        });
        middle.setMinWidth(Region.USE_PREF_SIZE);
        ((Region) middle.getChildren().get(2)).setMinHeight(12);

        Button up = new Button("Move up");
        up.setOnAction(e -> move(-1));
        Button down = new Button("Move down");
        down.setOnAction(e -> move(1));
        up.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        down.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        Button resetNames = new Button("Default names");
        resetNames.setTooltip(new Tooltip("Every column back to its default name; order and choice stay."));
        resetNames.setOnAction(e -> load(current().layout().resetNames()));
        Button resetAll = new Button("Default columns");
        resetAll.setTooltip(new Tooltip("The columns the preset writes, in its order, with default names."));
        resetAll.setOnAction(e -> load(ColumnLayout.defaults(names)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox tableButtons = new HBox(8, up, down, spacer, resetNames, resetAll);

        Label leftHead = section("Available fields");
        leftHead.setMinHeight(30); // same height as the right header with its combo box
        VBox left = new VBox(6, leftHead, availableList);
        VBox.setVgrow(availableList, Priority.ALWAYS);
        left.setPrefWidth(230);
        left.setMinWidth(190);
        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        Label rightHead = section("Exported columns");
        rightHead.setMinWidth(Region.USE_PREF_SIZE);
        countLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox head = new HBox(8, rightHead, countLabel, headSpacer, namesChoice());
        head.setAlignment(Pos.CENTER_LEFT);
        VBox right = new VBox(6, head, table, tableButtons);
        countLabel.getStyleClass().add("muted");
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        right.setMinWidth(420);

        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        problems.getStyleClass().add("export-problems");
        problems.setWrapText(true);
        problems.managedProperty().bind(problems.visibleProperty());

        HBox box = new HBox(12, left, middle, right);
        box.setMinHeight(170);
        box.setPrefHeight(320);
        return box;
    }

    private Node previewBox(int invoiceCount) {
        preview.setEditable(false);
        preview.setWrapText(false);
        preview.setPrefRowCount(4);
        preview.getStyleClass().add("export-preview");
        preview.setFocusTraversable(false);
        preview.setPromptText("The preview appears when the columns can be exported.");
        Label note = new Label("First " + Math.min(PREVIEW_ROWS, invoiceCount) + " of " + invoiceCount
                + (invoiceCount == 1 ? " invoice" : " invoices") + ", exactly as written to the file.");
        note.getStyleClass().add("muted");
        preview.setMinHeight(92);
        HBox head = new HBox(8, section("Preview"), note);
        head.setAlignment(Pos.BASELINE_LEFT);
        return new VBox(6, head, preview);
    }

    // --- Changes ---------------------------------------------------------------------------------

    private void addSelected() {
        List<ExportColumn> chosen = new ArrayList<>(availableList.getSelectionModel().getSelectedItems());
        if (chosen.isEmpty()) {
            return;
        }
        int after = table.getSelectionModel().getSelectedIndex();
        int firstNew = after >= 0 ? after + 1 : rows.size();
        load(current().layout().add(chosen, after));
        table.getSelectionModel().clearSelection();
        table.getSelectionModel().selectRange(firstNew, firstNew + chosen.size());
        table.scrollTo(firstNew);
    }

    private void removeSelected() {
        List<ExportColumn> chosen = table.getSelectionModel().getSelectedItems().stream().map(r -> r.field).toList();
        if (chosen.isEmpty()) {
            return;
        }
        int index = table.getSelectionModel().getSelectedIndex();
        load(current().layout().remove(chosen));
        if (!rows.isEmpty()) {
            table.getSelectionModel().select(Math.min(index, rows.size() - 1));
        }
    }

    private void move(int delta) {
        int index = table.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        ColumnLayout moved = current().layout().move(index, delta);
        load(moved);
        int target = Math.max(0, Math.min(rows.size() - 1, index + delta));
        table.getSelectionModel().clearAndSelect(target);
        table.scrollTo(Math.max(0, target - 2));
        table.requestFocus();
    }

    private void changeCsv(CsvSettings value) {
        csv = value;
        refresh();
    }

    /** Shows {@code layout}: rebuilds both lists (only on add/remove/move/reset, never while typing). */
    private void load(ColumnLayout layout) {
        names = layout.names();
        rows.setAll(layout.columns().stream().map(Row::new).toList());
        rows.forEach(row -> row.name.addListener((o, a, b) -> refresh()));
        available.setAll(layout.available());
        table.refresh(); // row numbers
        refresh();
    }

    /** Problems, preview and counts for the current state. Cheap: runs on every keystroke. */
    private void refresh() {
        ExportSetup setup = current();
        List<String> list = setup.problems();
        problems.setText(String.join("\n", list));
        problems.setVisible(!list.isEmpty());
        if (continueButton != null) {
            continueButton.setDisable(!list.isEmpty());
        }
        preview.setText(list.isEmpty() ? setup.preview(exports, sample) : "");
        countLabel.setText(rows.size() == 1 ? "1 column" : rows.size() + " columns");
    }

    private ExportSetup current() {
        ColumnLayout layout = new ColumnLayout(names, rows.stream()
                .map(r -> new ColumnLayout.Column(r.field, r.name.get() == null ? "" : r.name.get()))
                .toList());
        return start.withLayout(layout).withCsv(csv);
    }

    private void showHint(ExportColumn field) {
        hint.setText(field == null ? " " : ExportFields.label(field) + ": " + ExportFields.hint(field));
    }

    // --- Cells -----------------------------------------------------------------------------------

    private static final class FieldCell extends ListCell<ExportColumn> {
        private final Label label = new Label();
        private final Label kind = new Label();
        private final HBox box = new HBox(6, label, kind);

        FieldCell() {
            kind.getStyleClass().add("field-kind");
            box.setAlignment(Pos.BASELINE_LEFT);
        }

        @Override
        protected void updateItem(ExportColumn item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            label.setText(ExportFields.label(item));
            kind.setText(ExportFields.valueKind(item));
            setGraphic(box);
            setTooltip(new Tooltip(ExportFields.hint(item)));
        }
    }

    /** An always-visible text field bound to the row's name: typing changes the name at once. */
    private final class NameCell extends TableCell<Row, Row> {
        private final TextField field = new TextField();
        private Row bound;

        NameCell() {
            field.focusedProperty().addListener((o, a, b) -> {
                if (b && getIndex() >= 0 && getIndex() < rows.size()) {
                    table.getSelectionModel().clearAndSelect(getIndex());
                }
            });
            field.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    table.requestFocus();
                    e.consume();
                }
            });
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (bound != null) {
                field.textProperty().unbindBidirectional(bound.name);
                bound = null;
            }
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            field.textProperty().bindBidirectional(row.name);
            field.setPromptText(names.of(row.field));
            bound = row;
            setGraphic(field);
        }
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private static ComboBox<CsvSettings.Choice> choiceBox(List<CsvSettings.Choice> choices, char selected) {
        ComboBox<CsvSettings.Choice> box = new ComboBox<>(FXCollections.observableArrayList(choices));
        box.setValue(choices.stream().filter(c -> c.value() == selected).findFirst().orElse(choices.getFirst()));
        box.setPrefWidth(170);
        return box;
    }

    private static ListCell<ColumnLayout.Names> namesCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ColumnLayout.Names item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        };
    }

    private static Region gap(double width) {
        Region gap = new Region();
        gap.setMinWidth(width);
        return gap;
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("details-section");
        return label;
    }
}
