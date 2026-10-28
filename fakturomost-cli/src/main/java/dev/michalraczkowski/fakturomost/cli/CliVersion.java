package dev.michalraczkowski.fakturomost.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

/**
 * Version text for {@code --version}. The version comes from a Maven-filtered resource, so it is the
 * same in the IDE, in tests and in the packaged jar (a manifest entry would exist only in the jar).
 */
final class CliVersion implements IVersionProvider {

    static final String RESOURCE = "fakturomost-cli.properties";

    @Override
    public String[] getVersion() {
        return new String[] {
                "Fakturomost CLI " + version(),
                "Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"
        };
    }

    static String version() {
        Properties properties = new Properties();
        try (InputStream in = CliVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String version = properties.getProperty("version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("No version in " + RESOURCE);
        }
        return version.strip();
    }
}
