package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.desktop.ui.model.OtherDocumentRow;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The documents kept in formats Fakturomost cannot read (Unsupported on import), so they are not
 * lost from sight: listed here, and exportable as their original XML.
 */
final class OtherDocumentsWindow {

    private OtherDocumentsWindow() {
    }

    /** @param onExport starts the export of every listed document (owned by {@code MainWindow}) */
    static void show(Window owner, List<OtherDocumentRow> rows, Runnable onExport) {
        Label title = new Label(OtherDocumentRow.linkText(rows.size()));
        title.getStyleClass().add("details-title");
        Label explanation = new Label(OtherDocumentRow.EXPLANATION);
        explanation.setWrapText(true);
        explanation.setMinHeight(Region.USE_PREF_SIZE); // wrap instead of cutting the text off
        explanation.getStyleClass().add("muted");

        TableView<OtherDocumentRow> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.getColumns().add(column("Document", 470, false, OtherDocumentRow::source));
        table.getColumns().add(column("KSeF number", 290, false, OtherDocumentRow::ksefNumber));
        table.getColumns().add(column("Size", 90, true, OtherDocumentRow::size));
        table.setPlaceholder(new Label("No documents in other formats."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Stage stage = new Stage();
        Button export = new Button("Export original XML…");
        export.getStyleClass().add("primary");
        export.setDisable(rows.isEmpty());
        export.setOnAction(e -> {
            stage.close();
            onExport.run();
        });
        Button close = new Button("Close");
        close.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, export, spacer, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, title, explanation, table, buttons);
        root.setPadding(new Insets(16, 20, 16, 20));
        Scene scene = new Scene(root, 920, 380);
        scene.getStylesheets().add(Theme.STYLESHEET);
        stage.setScene(scene);
        stage.setTitle("Documents in other formats");
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.show();
    }

    private static TableColumn<OtherDocumentRow, String> column(String title, double width, boolean numeric,
                                                               Function<OtherDocumentRow, String> value) {
        TableColumn<OtherDocumentRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        if (numeric) {
            column.getStyleClass().add("numeric");
        }
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        return column;
    }
}
