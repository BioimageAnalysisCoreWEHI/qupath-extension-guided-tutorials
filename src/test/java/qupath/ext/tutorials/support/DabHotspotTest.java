package qupath.ext.tutorials.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DabHotspotTest {

    @Test
    void findsTheBrownestWindowAndIgnoresNegativeNoise() {
        int w = 20;
        int h = 10;
        float[] dab = new float[w * h];
        java.util.Arrays.fill(dab, -0.5f); // negative OD must not drag other windows below zero
        for (int y = 5; y < 9; y++) {
            for (int x = 12; x < 16; x++) {
                dab[y * w + x] = 0.4f;
            }
        }
        var hot = DabHotspot.find(dab, w, h, 4, 4, 0.1).orElseThrow();
        assertEquals(12, hot.x());
        assertEquals(5, hot.y());
        assertEquals(0.4, hot.meanDab(), 1e-6);
    }

    @Test
    void emptyWhenNothingIsBrownEnoughOrTheBoxDoesNotFit() {
        float[] faint = new float[100];
        java.util.Arrays.fill(faint, 0.02f);
        assertTrue(DabHotspot.find(faint, 10, 10, 4, 4, 0.1).isEmpty());
        assertTrue(DabHotspot.find(faint, 10, 10, 11, 4, 0.0).isEmpty());
    }
}
