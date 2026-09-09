package com.bizard.homesmokemqtt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeSmokeThemeTest {
    @Test
    public void parsesStoredModeSafely() {
        assertEquals(HomeSmokeTheme.Mode.SYSTEM, HomeSmokeTheme.Mode.from(null));
        assertEquals(HomeSmokeTheme.Mode.SYSTEM, HomeSmokeTheme.Mode.from("unknown"));
        assertEquals(HomeSmokeTheme.Mode.SYSTEM, HomeSmokeTheme.Mode.from("system"));
        assertEquals(HomeSmokeTheme.Mode.LIGHT, HomeSmokeTheme.Mode.from("light"));
        assertEquals(HomeSmokeTheme.Mode.DARK, HomeSmokeTheme.Mode.from("dark"));
        assertEquals(HomeSmokeTheme.Mode.DARK, HomeSmokeTheme.Mode.from("DARK"));
    }

    @Test
    public void lightAndDarkPalettesRemainDistinct() {
        HomeSmokeTheme.Palette light = HomeSmokeTheme.palette(false);
        HomeSmokeTheme.Palette dark = HomeSmokeTheme.palette(true);

        assertFalse(light.dark);
        assertTrue(dark.dark);
        assertNotEquals(light.background, dark.background);
        assertNotEquals(light.surface, dark.surface);
        assertNotEquals(light.text, dark.text);
        assertNotEquals(light.border, dark.border);
    }
}
