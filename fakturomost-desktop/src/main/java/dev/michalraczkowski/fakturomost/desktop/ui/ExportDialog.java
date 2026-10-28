package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.desktop.ui.model.ExportFormat;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ExportScope;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Step 1 of an export: what to export and in which format, with the "not encrypted" notice in plain
 * sight. For CSV and chosen-fields JSON, {@link ExportColumnsDialog} follows (columns, names, CSV
 * settings); the last step, choosing the file or folder, is the platform's own dialog (see
 * {@code MainWindow}).
 */
final class ExportDialog {

    /** The user's answer. */
    record Choice(ExportFormat format, ExportScope scope) {
    }

    private static final String CHOOSE_LOCATION = "Choose location…";
    private static final String NEXT = "Next: columns…";

    private ExportDialog() {
    }

    /**
     * For invoices: selected rows or all shown rows, in any format.
     *
     * @param selected number of selected rows (0 disables that choice)
     * @param shown    number of rows the table shows
     * @param filtered whether the filters hide some invoices
     * @param preselectedScope the scope to show selected (after "Back"), or {@code null} for the default
     */
    static Optional<Choice> chooseForInvoices(Window owner, int selected, int shown, boolean filtered,
                                              ExportFormat preselected, ExportScope preselectedScope) {
        ToggleGroup scopes = new ToggleGroup();
        RadioButton selectedButton = radio(ExportScope.SELECTED.label(selected, filtered), ExportScope.SELECTED, scopes);
        RadioButton shownButton = radio(ExportScope.ALL_SHOWN.label(shown, filtered), ExportScope.ALL_SHOWN, scopes);
        selectedButton.setDisable(selected == 0);
        ExportScope scope = preselectedScope == null || (preselectedScope == ExportScope.SELECTED && selected == 0)
                ? ExportScope.defaultFor(selected) : preselectedScope;
        (scope == ExportScope.SELECTED ? selectedButton : shownButton).setSelected(true);

        return show(owner, "Export invoices", "Export", List.of(ExportFormat.values()), preselected,
                new VBox(6, sectionLabel("Invoices"), selectedButton, shownButton), scopes);
    }

    /** For documents in other formats: only original XML (ZIP or folder) makes sense. */
    static Optional<ExportFormat> chooseForOriginals(Window owner, int count) {
        return show(owner, "Export original files", "Export " + (count == 1 ? "1 document" : count + " documents"),
                ExportFormat.originalFormats(), ExportFormat.XML_ZIP, null, null)
                .map(Choice::format);
    }

    private static Optional<Choice> show(Window owner, String title, String header, List<ExportFormat> formats,
                                         ExportFormat preselected, VBox scopeBox, ToggleGroup scopes) {
        Dialog<Choice> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        Theme.style(dialog);

        ToggleGroup formatGroup = new ToggleGroup();
        VBox choices = new VBox(6, sectionLabel("Format"));
        ExportFormat initial = formats.contains(preselected) ? preselected : formats.getFirst();
        for (ExportFormat format : formats) {
            RadioButton button = radio(format.label(), format, formatGroup);
            button.setSelected(format == initial);
            choices.getChildren().add(button);
        }

        Label notice = new Label(ExportFormat.NOT_ENCRYPTED_NOTICE);
        notice.setWrapText(true);
        notice.setMaxWidth(460);
        notice.getStyleClass().add("warning");

        VBox content = new VBox(14);
        if (scopeBox != null) {
            content.getChildren().add(scopeBox);
        }
        content.getChildren().addAll(choices, notice);
        content.setPadding(new Insets(6, 0, 0, 0));
        dialog.getDialogPane().setContent(content);

        ButtonType next = new ButtonType(CHOOSE_LOCATION, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(next, ButtonType.CANCEL);
        // Formats with columns go on to the columns step first; the button says so.
        Button nextButton = (Button) dialog.getDialogPane().lookupButton(next);
        Runnable label = () -> nextButton.setText(formatGroup.getSelectedToggle() != null
                && ((ExportFormat) formatGroup.getSelectedToggle().getUserData()).hasColumns() ? NEXT : CHOOSE_LOCATION);
        formatGroup.selectedToggleProperty().addListener((o, a, b) -> label.run());
        label.run();
        dialog.setResultConverter(button -> button == next && formatGroup.getSelectedToggle() != null
                ? new Choice((ExportFormat) formatGroup.getSelectedToggle().getUserData(),
                        scopes == null ? null : (ExportScope) scopes.getSelectedToggle().getUserData())
                : null);
        return dialog.showAndWait();
    }

    private static RadioButton radio(String text, Object value, ToggleGroup group) {
        RadioButton button = new RadioButton(text);
        button.setUserData(value);
        button.setToggleGroup(group);
        return button;
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("details-section");
        return label;
    }
}
