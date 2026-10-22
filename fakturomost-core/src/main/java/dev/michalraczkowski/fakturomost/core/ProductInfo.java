package dev.michalraczkowski.fakturomost.core;

import java.util.Objects;

public record ProductInfo(String name, String version) {
    public ProductInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
    }

    public String displayName() {
        return name + " " + version;
    }
}