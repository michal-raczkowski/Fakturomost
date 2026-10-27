package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.desktop.ui.model.Formats;
import dev.michalraczkowski.fakturomost.desktop.ui.model.InvoiceRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * The invoice list (master prompt §12): number, type, KSeF number, seller, buyer, issue date, gross
 * and currency. Multi-selection; click a header to sort (dates and amounts sort by value, not text);
 * without a sort column the rows keep the catalog's order (newest first).
 */
final class InvoiceTable {

    private static final String EMPTY_TEXT = "No invoices yet. Use \"Import folder…\" or \"Import files…\", "
            + "or drop files here.\nTo try it out, import the demo-data folder of the repository.";

    private final Label emptyPlaceholder = new Label(EMPTY_TEXT);
    private final Label noMatchPlaceholder = new Label("No invoices match the filters. Press Esc in a filter field or \"Clear\" to see all.");
    private final ObservableList<InvoiceRow> rows = FXCollections.observableArrayList();
    private final TableView<InvoiceRow> view = new TableView<>();

    InvoiceTable() {
        SortedList<InvoiceRow> sorted = new SortedList<>(rows);
        sorted.comparatorProperty().bind(view.comparatorProperty());
        view.setItems(sorted);
        view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        view.setPlaceholder(emptyPlaceholder);

        view.getColumns().add(text("Number", 150, InvoiceRow::number));
        view.getColumns().add(text("Type", 55, InvoiceRow::type));
        view.getColumns().add(text("KSeF number", 300, InvoiceRow::ksefNumber));
        view.getColumns().add(text("Seller", 220, InvoiceRow::seller));
        view.getColumns().add(text("Buyer", 220, InvoiceRow::buyer));
        view.getColumns().add(dateColumn());
        view.getColumns().add(grossColumn());
        view.getColumns().add(text("Currency", 80, InvoiceRow::currency));
    }

    TableView<InvoiceRow> view() {
        return view;
    }

    /** @param filtered whether the rows come from a filter, so "nothing matches" can be told apart from "nothing imported" */
    void setRows(List<InvoiceRow> newRows, boolean filtered) {
        rows.setAll(newRows);
        view.setPlaceholder(filtered ? noMatchPlaceholder : emptyPlaceholder);
    }

    int size() {
        return rows.size();
    }

    /** The selected rows in the order shown (not the order they were clicked). */
    List<InvoiceRow> selectedInDisplayOrder() {
        return view.getSelectionModel().getSelectedIndices().stream()
                .sorted()
                .map(view.getItems()::get)
                .toList();
    }

    private static TableColumn<InvoiceRow, String> text(String title, double width, Function<InvoiceRow, String> value) {
        TableColumn<InvoiceRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        return column;
    }

    private static TableColumn<InvoiceRow, LocalDate> dateColumn() {
        TableColumn<InvoiceRow, LocalDate> column = new TableColumn<>("Issue date");
        column.setPrefWidth(95);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().issueDate()));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Formats.date(item));
            }
        });
        return column;
    }

    private static TableColumn<InvoiceRow, BigDecimal> grossColumn() {
        TableColumn<InvoiceRow, BigDecimal> column = new TableColumn<>("Gross");
        column.setPrefWidth(110);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().gross()));
        column.getStyleClass().add("numeric"); // right-aligned header and cells (fakturomost.css)
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Formats.amount(item));
            }
        });
        return column;
    }
}
