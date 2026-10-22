package dev.michalraczkowski.fakturomost.desktop;

import javafx.application.Application;

/** Plain main class: lets JavaFX run from the classpath (non-modular). */
public final class Launcher {
    public static void main(String[] args) {
        Application.launch(FakturomostApp.class, args);
    }
}