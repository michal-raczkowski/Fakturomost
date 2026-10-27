package dev.michalraczkowski.fakturomost.desktop.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import javafx.scene.control.Dialog;
import javafx.scene.text.Font;

/**
 * The look of the app, the same on Windows, macOS and Linux: JavaFX's own Modena skin underneath,
 * our stylesheet ({@code fakturomost.css}) on top, and a bundled font instead of whatever system
 * font the OS offers. Every scene and dialog gets the stylesheet through this class.
 *
 * <p>Native windows (file choosers, window title bars) stay native on purpose.
 */
public final class Theme {

    /** The family name inside the bundled TTFs; {@code fakturomost.css} uses exactly this. */
    public static final String FONT_FAMILY = "IBM Plex Sans";

    static final List<String> FONT_FILES =
            List.of("IBMPlexSans-Regular.ttf", "IBMPlexSans-SemiBold.ttf", "IBMPlexSans-Bold.ttf");

    static final String STYLESHEET =
            Objects.requireNonNull(Theme.class.getResource("fakturomost.css"), "fakturomost.css").toExternalForm();

    private Theme() {
    }

    /**
     * Registers the bundled fonts with JavaFX. Call once on the JavaFX thread before the first scene
     * is built. A font that fails to load is not fatal — the stylesheet then falls back to the
     * system font — so this never throws for a bad font file, only for a missing resource.
     */
    public static void loadFonts() {
        for (String file : FONT_FILES) {
            try (InputStream in = Theme.class.getResourceAsStream("fonts/" + file)) {
                if (in == null) {
                    throw new IllegalStateException("Missing bundled font " + file);
                }
                Font.loadFont(in, 13);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Styles a dialog like the main window: our stylesheet and no Modena icon blob. */
    static void style(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(STYLESHEET);
        dialog.setGraphic(null);
    }
}
