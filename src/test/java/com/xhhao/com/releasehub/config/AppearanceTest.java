package com.xhhao.com.releasehub.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AppearanceTest {
    @Test void defaultColorsMatchHaloReleasePalette() {
        var appearance = new HubSettings.Appearance();
        assertEquals("#00c16a", appearance.primary());
        assertEquals("#3b82f6", appearance.secondary());
    }
    @Test void acceptsHexAndNormalizesShortNotation() {
        var appearance = new HubSettings.Appearance();
        appearance.setPrimaryColor(" #A3F "); appearance.setSecondaryColor("#7C3AED");
        assertEquals("#aa33ff", appearance.primary());
        assertEquals("#7c3aed", appearance.secondary());
    }
    @Test void unsafeAndInvalidCssValuesFallBackToDefaults() {
        var appearance = new HubSettings.Appearance();
        appearance.setPrimaryColor("red; background:url(https://example.com)");
        appearance.setSecondaryColor(null);
        assertEquals("#00c16a", appearance.primary());
        assertEquals("#3b82f6", appearance.secondary());
    }
}
