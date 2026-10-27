package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.desktop.ui.model.ExportFormat;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Step 1 of "Export selected…": choose the format, with the "not encrypted" notice in plain sight.
 * Step 2, choosing the file or folder, is the platform's own dialog (see {@code MainWindow}).
 */
final class ExportDialog {

    private ExportDialog() {
    }

    /** The chosen format, or empty if the user cancelled. */
    static Optional<ExportFormat> chooseFormat(Window owner, int invoiceCount, ExportFormat preselected) {
        Dialog<ExportFormat> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Export selected invoices");
        dialog.setHeaderText("Export " + (invoiceCount == 1 ? "1 invoice" : invoiceCount + " invoices") + " as:");
        Theme.style(dialog);

        ToggleGroup group = new ToggleGroup();
        VBox choices = new VBox(6);
        for (ExportFormat format : ExportFormat.values()) {
            RadioButton button = new RadioButton(format.label());
            button.setUserData(format);
            button.setToggleGroup(group);
            button.setSelected(format == preselected);
            choices.getChildren().add(button);
        }

        Label notice = new Label(ExportFormat.NOT_ENCRYPTED_NOTICE);
        notice.setWrapText(true);
        notice.setMaxWidth(460);
        notice.getStyleClass().add("warning");

        VBox content = new VBox(14, choices, notice);
        content.setPadding(new Insets(6, 0, 0, 0));
        dialog.getDialogPane().setContent(content);

        ButtonType next = new ButtonType("Choose location…", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(next, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == next && group.getSelectedToggle() != null
                ? (ExportFormat) group.getSelectedToggle().getUserData()
                : null);
        return dialog.showAndWait();
    }
}
