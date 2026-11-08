package qupath.ext.tutorials.support;

import java.util.Optional;

/**
 * Percentage of positive cells, carrying its own derivation so the number shown to the user can be
 * checked by hand. The denominator is positive + negative <em>cells</em> — never area, and never
 * objects of unrelated classes.
 *
 * @param value percentage, 0–100
 * @param numerator positive cells
 * @param denominator positive + negative cells
 */
public record PositivePercent(double value, int numerator, int denominator) {

    public static final String FORMULA = "positive / (positive + negative) × 100";

    /** @return empty when there are no positive or negative cells to divide by */
    public static Optional<PositivePercent> of(int positive, int negative) {
        int denominator = positive + negative;
        if (positive < 0 || negative < 0 || denominator == 0) {
            return Optional.empty();
        }
        return Optional.of(new PositivePercent(100.0 * positive / denominator, positive, denominator));
    }

    /**
     * Whether a classification name means Positive, including compound names such as {@code "Tumor:
     * Positive"}. Intensity sub-classes ({@code "1+"}, {@code "2+"}, {@code "3+"}) are not counted —
     * they come from three-threshold scoring, which this tutorial deliberately avoids.
     */
    public static boolean isPositive(String className) {
        return lastPart(className).equalsIgnoreCase("Positive");
    }

    public static boolean isNegative(String className) {
        return lastPart(className).equalsIgnoreCase("Negative");
    }

    private static String lastPart(String className) {
        if (className == null) {
            return "";
        }
        int colon = className.lastIndexOf(':');
        return (colon >= 0 ? className.substring(colon + 1) : className).trim();
    }
}
