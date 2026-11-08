package qupath.ext.tutorials.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PositivePercentTest {

    @Test
    void dividesByCellCountAndCarriesItsDerivation() {
        var p = PositivePercent.of(135, 953).orElseThrow();
        assertEquals(135, p.numerator());
        assertEquals(1088, p.denominator());
        assertEquals(12.408, p.value(), 0.001);
        assertTrue(PositivePercent.of(0, 0).isEmpty());
    }

    @Test
    void recognisesPlainAndCompoundClassNamesButNotIntensityBins() {
        assertTrue(PositivePercent.isPositive("Positive"));
        assertTrue(PositivePercent.isPositive("Tumor: Positive"));
        assertTrue(PositivePercent.isNegative("Tumor: Negative"));
        assertFalse(PositivePercent.isPositive("3+"));
        assertFalse(PositivePercent.isPositive(null));
    }
}
