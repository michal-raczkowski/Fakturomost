package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.desktop.ui.model.ImportSummary;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Every import result, one line per document, so no document is dropped silently: imported and
 * duplicate ones, and — highlighted — unsupported, invalid and failed ones with core's reason.
 * Not modal: the user can keep it open next to the table.
 */
final class ImportResultsWindow {

    private ImportResultsWindow() {
    }

    static void show(Window owner, ImportSummary summary) {
        Label headline = new Label(summary.headline());
        headline.getStyleClass().add("details-title");

        TableView<ImportSummary.Line> table = new TableView<>(FXCollections.observableArrayList(summary.lines()));
        table.getColumns().add(column("Status", 95, line -> line.status().label()));
        table.getColumns().add(column("Document", 430, ImportSummary.Line::source));
        table.getColumns().add(column("Detail", 430, ImportSummary.Line::detail));
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(ImportSummary.Line line, boolean empty) {
                super.updateItem(line, empty);
                getStyleClass().remove("problem");
                if (!empty && line != null && line.status().isProblem()) {
                    getStyleClass().add("problem");
                }
            }
        });
        table.setPlaceholder(new Label("No documents were read."));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox notes = new VBox(4);
        summary.notes().forEach(note -> {
            Label label = new Label("Note: " + note);
            label.setWrapText(true);
            notes.getChildren().add(label);
        });

        Stage stage = new Stage();
        Button copy = new Button("Copy to clipboard");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(summary.asText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        Button close = new Button("Close");
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, copy, spacer, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, headline, table, notes, buttons);
        root.setPadding(new Insets(16, 20, 16, 20));
        Scene scene = new Scene(root, 1000, 480);
        scene.getStylesheets().add(Theme.STYLESHEET);
        stage.setScene(scene);
        stage.setTitle("Import results");
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.show();
    }

    private static TableColumn<ImportSummary.Line, String> column(String title, double width,
                                                                 Function<ImportSummary.Line, String> value) {
        TableColumn<ImportSummary.Line, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        return column;
    }
}
