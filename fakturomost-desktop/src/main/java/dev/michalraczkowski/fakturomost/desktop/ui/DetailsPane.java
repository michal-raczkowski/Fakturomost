package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.desktop.ui.model.InvoiceDetails;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Details of the selected invoice (master prompt §12): parties, dates, line items, net/VAT per
 * bucket, gross, and the original XML. Values are read-only text fields so they can be selected
 * and copied (KSeF numbers, NIPs), but look like labels.
 */
final class DetailsPane {

    /** Row and header heights from fakturomost.css ({@code -fx-cell-size}, column header {@code -fx-size}). */
    private static final double ROW_HEIGHT = 30;
    private static final double HEADER_HEIGHT = 32;

    private final VBox content = new VBox(12);
    private final ScrollPane view = new ScrollPane(content);

    DetailsPane() {
        content.setPadding(new Insets(16, 20, 20, 20));
        content.getStyleClass().add("details");
        view.getStyleClass().add("details-scroll");
        view.setFitToWidth(true);
        showMessage("Select an invoice to see its details.");
    }

    Node view() {
        return view;
    }

    void showMessage(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("details-message");
        content.getChildren().setAll(label);
    }

    void show(InvoiceDetails details) {
        Label title = new Label(details.title());
        title.getStyleClass().add("details-title");

        HBox top = new HBox(24, fieldGrid(details.fields()), party(details.seller()), party(details.buyer()));

        content.getChildren().setAll(title, top,
                section("Line items"), details.items().isEmpty()
                        ? new Label("No line items on this invoice (allowed by FA(3), e.g. advance invoices).")
                        : itemsTable(details.items()),
                section("VAT (as stated on the invoice)"), details.vat().isEmpty()
                        ? new Label("No tax buckets stated.")
                        : vatTable(details.vat()),
                section("Original document"), fieldGrid(details.original()));
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("details-section");
        return label;
    }

    private static GridPane fieldGrid(List<InvoiceDetails.Field> fields) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(110);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        int row = 0;
        for (InvoiceDetails.Field field : fields) {
            Label label = new Label(field.label());
            label.getStyleClass().add("details-label");
            label.setMinWidth(Region.USE_PREF_SIZE);
            grid.addRow(row++, label, copyable(field.value(), 64));
        }
        return grid;
    }

    private static VBox party(InvoiceDetails.PartyView party) {
        Label role = new Label(party.role());
        role.getStyleClass().add("details-label");
        VBox box = new VBox(2, role);
        box.getStyleClass().add("party-block");
        party.lines().forEach(line -> box.getChildren().add(copyable(line, 45)));
        box.setMinWidth(240);
        HBox.setHgrow(box, Priority.SOMETIMES);
        return box;
    }

    /** Looks like a label, but the text can be selected and copied. */
    private static TextField copyable(String text, int maxColumns) {
        TextField field = new TextField(text);
        field.setEditable(false);
        field.setFocusTraversable(false);
        field.getStyleClass().add("copyable");
        field.setPrefColumnCount(Math.max(4, Math.min(maxColumns, text.length() + 1)));
        return field;
    }

    private static TableView<InvoiceDetails.ItemRow> itemsTable(List<InvoiceDetails.ItemRow> items) {
        TableView<InvoiceDetails.ItemRow> table = fixedHeightTable(items.size());
        table.getColumns().add(column("#", 90, false, InvoiceDetails.ItemRow::position));
        table.getColumns().add(column("Description", 340, false, InvoiceDetails.ItemRow::description));
        table.getColumns().add(column("Quantity", 80, true, InvoiceDetails.ItemRow::quantity));
        table.getColumns().add(column("Unit", 60, false, InvoiceDetails.ItemRow::unit));
        table.getColumns().add(column("Unit price", 120, true, InvoiceDetails.ItemRow::unitPrice));
        table.getColumns().add(column("Amount", 120, true, InvoiceDetails.ItemRow::amount));
        table.getColumns().add(column("VAT rate", 80, false, InvoiceDetails.ItemRow::vatRate));
        table.setItems(FXCollections.observableArrayList(items));
        return table;
    }

    private static TableView<InvoiceDetails.VatRow> vatTable(List<InvoiceDetails.VatRow> rows) {
        TableView<InvoiceDetails.VatRow> table = fixedHeightTable(rows.size());
        table.getColumns().add(column("Tax bucket", 320, false, InvoiceDetails.VatRow::bucket));
        table.getColumns().add(column("Net", 120, true, InvoiceDetails.VatRow::net));
        table.getColumns().add(column("VAT", 120, true, InvoiceDetails.VatRow::vat));
        table.getColumns().add(column("VAT in PLN", 120, true, InvoiceDetails.VatRow::vatInPln));
        table.setItems(FXCollections.observableArrayList(rows));
        return table;
    }

    /** Tall enough for every row, so the outer scroll pane is the only one. */
    private static <T> TableView<T> fixedHeightTable(int rows) {
        TableView<T> table = new TableView<>();
        table.getStyleClass().add("details-table");
        table.setFixedCellSize(ROW_HEIGHT);
        double height = HEADER_HEIGHT + rows * ROW_HEIGHT + 4;
        table.setPrefHeight(height);
        table.setMinHeight(height);
        table.setMaxHeight(height);
        return table;
    }

    private static <T> TableColumn<T, String> column(String title, double width, boolean numeric, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false); // document order matters (e.g. "before" rows of a correction)
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        if (numeric) {
            column.getStyleClass().add("numeric"); // right-aligned header and cells (fakturomost.css)
        }
        return column;
    }
}
