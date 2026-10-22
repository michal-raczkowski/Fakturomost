package dev.michalraczkowski.fakturomost.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductInfoTest {
    @Test
    void displayNameCombinesNameAndVersion() {
        assertEquals("Fakturomost 0.1.0", new ProductInfo("Fakturomost", "0.1.0").displayName());
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new ProductInfo(" ", "1"));
    }
}