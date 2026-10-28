package dev.michalraczkowski.fakturomost.desktop.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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

    /**
     * Sizes of the logo renders in {@code icons/fakturomost-<size>.png} (design spec §5.8). The 16 and
     * 24 px files use the small-size variant (no inner wave); {@code fakturomost-24@2x.png} is the same
     * variant for HiDPI screens, picked up by JavaFX automatically.
     */
    static final List<Integer> ICON_SIZES = List.of(16, 24, 32, 48, 64, 128, 256);

    private Theme() {
    }

    /** Window and taskbar icons, every size, so the OS can pick the closest one. */
    static List<Image> windowIcons() {
        return ICON_SIZES.stream().map(size -> new Image(icon(size))).toList();
    }

    /**
     * The logo mark at {@code size} px: the small-size variant at 24 px and below (header), the full
     * mark above (About dialog), drawn from the next larger render so it stays sharp on HiDPI.
     */
    static ImageView mark(int size) {
        int source = size <= 24 ? 24 : ICON_SIZES.stream().filter(s -> s >= 2 * size).findFirst().orElse(256);
        ImageView view = new ImageView(new Image(icon(source)));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setSmooth(true);
        view.getStyleClass().add("mark");
        return view;
    }

    private static String icon(int size) {
        String name = "icons/fakturomost-" + size + ".png";
        return Objects.requireNonNull(Theme.class.getResource(name), name).toExternalForm();
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
