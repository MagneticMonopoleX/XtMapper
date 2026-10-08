package xtr.keymapper.keymap;

import org.junit.Test;

import xtr.keymapper.keymap.element.Key;

import static org.junit.Assert.assertEquals;

public class KeymapProfileScaleTest {

    @Test
    public void testScaleGuardsAgainstSystemInsetMismatch() {
        KeymapProfile profile = new KeymapProfile();
        profile.xRes = 2560;
        profile.yRes = 1488; // Tablet with navigation bar / taskbar insets

        Key key = new Key();
        key.code = "KEY_SPACE";
        key.x = 1671f;
        key.y = 1450f;
        profile.keys.add(key);

        // Scaling to 2560 x 1600 (fullscreen vs insets on the same tablet)
        profile.scale(2560f, 1600f);

        // Coordinates should NOT be scaled/drifted downward!
        assertEquals(1671f, key.x, 0.001f);
        assertEquals(1450f, key.y, 0.001f);
        // Resolution updated
        assertEquals(2560, profile.xRes);
        assertEquals(1600, profile.yRes);
    }

    @Test
    public void testScaleDoesNotDriftOnIdenticalDimensions() {
        KeymapProfile profile = new KeymapProfile();
        profile.xRes = 2560;
        profile.yRes = 1600;

        Key key = new Key();
        key.code = "KEY_RIGHT";
        key.x = 640f;
        key.y = 1400f;
        profile.keys.add(key);

        profile.scale(2560f, 1600f);

        assertEquals(640f, key.x, 0.001f);
        assertEquals(1400f, key.y, 0.001f);
    }

    @Test
    public void testScaleAppliesToGenuineResolutionDifferences() {
        KeymapProfile profile = new KeymapProfile();
        profile.xRes = 1920;
        profile.yRes = 1080;

        Key key = new Key();
        key.code = "KEY_LEFT";
        key.x = 100f;
        key.y = 500f;
        profile.keys.add(key);

        // Scaling to a different device resolution, e.g. 2560 x 1440
        profile.scale(2560f, 1440f);

        float expectedX = 100f * (2560f / 1920f);
        float expectedY = 500f * (1440f / 1080f);
        assertEquals(expectedX, key.x, 0.01f);
        assertEquals(expectedY, key.y, 0.01f);
    }
}
