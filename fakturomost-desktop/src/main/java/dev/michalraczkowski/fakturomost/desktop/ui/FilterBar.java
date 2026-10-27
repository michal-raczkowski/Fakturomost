package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.desktop.ui.model.FilterInput;
import dev.michalraczkowski.fakturomost.desktop.ui.model.Formats;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The filters above the invoice table (master prompt §12: number, NIP, company name, date range,
 * amount — plus type). Only collects input; parsing is {@link FilterInput}, searching is done by
 * {@code MainWindow} in the background. Typing waits {@value #DELAY_MILLIS} ms before searching, so
 * a search does not run for every keystroke.
 */
final class FilterBar {

    static final int DELAY_MILLIS = 250;

    private final TextField text = field("Search number, name or KSeF number", 180, 320);
    private final TextField taxId = field("NIP / VAT ID", 110, 140);
    private final TextField issuedFrom = field("Issued from", 90, 108);
    private final TextField issuedTo = field("Issued to", 90, 108);
    private final TextField grossFrom = field("Gross from", 80, 100);
    private final TextField grossTo = field("Gross to", 80, 100);
    private final ComboBox<InvoiceType> type = new ComboBox<>();
    private final Button clear = new Button("Clear");
    private final Label count = new Label();
    private final Label problems = new Label();
    private final Map<FilterInput.Field, Control> controls = new EnumMap<>(FilterInput.Field.class);
    private final PauseTransition delay = new PauseTransition(Duration.millis(DELAY_MILLIS));
    private final VBox view;

    FilterBar(Runnable onChange) {
        controls.put(FilterInput.Field.TEXT, text);
        controls.put(FilterInput.Field.TAX_ID, taxId);
        controls.put(FilterInput.Field.ISSUED_FROM, issuedFrom);
        controls.put(FilterInput.Field.ISSUED_TO, issuedTo);
        controls.put(FilterInput.Field.GROSS_FROM, grossFrom);
        controls.put(FilterInput.Field.GROSS_TO, grossTo);
        controls.put(FilterInput.Field.TYPE, type);

        taxId.setTooltip(new Tooltip("The full NIP or EU VAT number of the seller or buyer, in any notation."));
        issuedFrom.setTooltip(new Tooltip("2026-10-02 or 02.10.2026"));
        issuedTo.setTooltip(new Tooltip("2026-10-02 or 02.10.2026"));
        grossFrom.setTooltip(new Tooltip("Gross amount in the invoice's currency, e.g. 1 234,50"));
        grossTo.setTooltip(new Tooltip("Gross amount in the invoice's currency, e.g. 1 234,50"));

        List<InvoiceType> types = new ArrayList<>();
        types.add(null); // "All types"
        types.addAll(List.of(InvoiceType.values()));
        type.setItems(FXCollections.observableArrayList(types));
        type.setCellFactory(list -> new TypeCell());
        type.setButtonCell(new TypeCell());
        type.getSelectionModel().selectFirst();
        type.setPrefWidth(190);
        type.setMinWidth(130);

        clear.setOnAction(e -> clear());
        clear.setMinWidth(Region.USE_PREF_SIZE);
        count.getStyleClass().add("muted");
        count.setMinWidth(150); // fixed, so the fields don't move when the text changes
        count.setAlignment(Pos.CENTER_RIGHT);
        problems.getStyleClass().add("filter-problems");
        problems.setWrapText(true);
        problems.managedProperty().bind(problems.visibleProperty());
        problems.setVisible(false);

        delay.setOnFinished(e -> onChange.run());
        for (TextField field : List.of(text, taxId, issuedFrom, issuedTo, grossFrom, grossTo)) {
            field.textProperty().addListener((obs, old, value) -> {
                updateClear();
                delay.playFromStart();
            });
            field.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    clear();
                    e.consume();
                }
            });
        }
        type.valueProperty().addListener((obs, old, value) -> {
            updateClear();
            delay.stop();
            onChange.run(); // a click, not typing: no need to wait
        });

        HBox.setHgrow(text, Priority.ALWAYS);
        Region spacer = new Region();
        spacer.setMinWidth(4);
        HBox row = new HBox(8, text, taxId, issuedFrom, dash(), issuedTo, grossFrom, dash(), grossTo, type, clear, spacer, count);
        row.getStyleClass().add("filter-row");
        view = new VBox(4, row, problems);
        view.getStyleClass().add("filter-bar");
        updateClear();
    }

    Node view() {
        return view;
    }

    FilterInput input() {
        return new FilterInput(text.getText(), taxId.getText(), issuedFrom.getText(), issuedTo.getText(),
                grossFrom.getText(), grossTo.getText(), type.getValue());
    }

    void focusSearch() {
        text.requestFocus();
        text.selectAll();
    }

    /** Empties every field; the resulting change triggers one search. */
    void clear() {
        delay.stop();
        for (TextField field : List.of(text, taxId, issuedFrom, issuedTo, grossFrom, grossTo)) {
            field.clear();
        }
        type.getSelectionModel().selectFirst();
        delay.playFromStart();
    }

    void setCount(String value) {
        count.setText(value);
    }

    void showProblems(List<FilterInput.Problem> list) {
        clearProblems();
        list.forEach(problem -> controls.get(problem.field()).getStyleClass().add("invalid"));
        problems.setText(String.join(" ", list.stream().map(FilterInput.Problem::message).distinct().toList()));
        problems.setVisible(true);
    }

    void clearProblems() {
        controls.values().forEach(control -> control.getStyleClass().remove("invalid"));
        problems.setVisible(false);
    }

    private void updateClear() {
        clear.setDisable(input().isEmpty());
    }

    private static TextField field(String prompt, double min, double pref) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMinWidth(min);
        field.setPrefWidth(pref);
        return field;
    }

    private static Label dash() {
        Label dash = new Label("–");
        dash.getStyleClass().add("muted");
        dash.setMinWidth(Region.USE_PREF_SIZE);
        return dash;
    }

    private static final class TypeCell extends ListCell<InvoiceType> {
        @Override
        protected void updateItem(InvoiceType item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item == null ? "All types" : Formats.type(item));
        }
    }
}
