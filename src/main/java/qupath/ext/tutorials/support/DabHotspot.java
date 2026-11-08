package qupath.ext.tutorials.support;

import java.util.Optional;

/**
 * Finds the box-sized window with the most brown (DAB) staining in a grid of per-pixel DAB optical
 * densities — used to place the drawing demo over tissue worth drawing around, rather than wherever
 * the middle of the screen happens to be. Pure; summed-area table, so every position is checked in
 * one pass.
 */
public final class DabHotspot {

    /**
     * @param x left of the best window, in grid cells
     * @param y top of the best window, in grid cells
     * @param meanDab mean DAB optical density inside it
     */
    public record Hotspot(int x, int y, double meanDab) {}

    private DabHotspot() {}

    /**
     * @param dab row-major DAB optical density per grid cell; negative values (noise from imperfect
     *     stain separation) count as zero
     * @param width grid width
     * @param height grid height
     * @param boxW window width, in grid cells
     * @param boxH window height, in grid cells
     * @param minMeanDab below this the brownest window is not brown enough to point at
     * @return the brownest window, or empty if none reaches {@code minMeanDab} or the box doesn't fit
     */
    public static Optional<Hotspot> find(float[] dab, int width, int height, int boxW, int boxH, double minMeanDab) {
        if (boxW <= 0 || boxH <= 0 || boxW > width || boxH > height || dab.length < width * height) {
            return Optional.empty();
        }
        double[] sum = new double[(width + 1) * (height + 1)];
        int stride = width + 1;
        for (int y = 0; y < height; y++) {
            double row = 0;
            for (int x = 0; x < width; x++) {
                float v = dab[y * width + x];
                row += (Float.isNaN(v) || v < 0) ? 0 : v;
                sum[(y + 1) * stride + x + 1] = sum[y * stride + x + 1] + row;
            }
        }
        double area = (double) boxW * boxH;
        Hotspot best = null;
        for (int y = 0; y + boxH <= height; y++) {
            for (int x = 0; x + boxW <= width; x++) {
                double total = sum[(y + boxH) * stride + x + boxW]
                        - sum[y * stride + x + boxW]
                        - sum[(y + boxH) * stride + x]
                        + sum[y * stride + x];
                double mean = total / area;
                if (best == null || mean > best.meanDab()) {
                    best = new Hotspot(x, y, mean);
                }
            }
        }
        return best != null && best.meanDab() >= minMeanDab ? Optional.of(best) : Optional.empty();
    }
}
