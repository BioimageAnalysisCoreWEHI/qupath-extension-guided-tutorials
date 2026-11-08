package qupath.ext.tutorials.live;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.tutorials.support.DabHotspot;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

/**
 * Finds one good area of the slide to practise on: a few hundred µm of tissue with plenty of brown
 * (DAB) staining, in full-resolution image coordinates. Used to send the user there ("Take me to a good
 * area") and to pin the drawing demo to that spot on the tissue.
 *
 * <p>A known slide can have a hand-picked {@link #PRESETS preset}; otherwise the whole slide is scanned
 * once at low resolution with the image's own stain vectors, on a background thread. Results are
 * cached per image and re-scanned if the stain vectors change (e.g. once the image type is set to
 * H-DAB). Read-only: it never changes the image.
 */
final class SlideTargetFinder {

    /** A region in full-resolution image pixels. */
    record Target(double x, double y, double width, double height, boolean preset) {
        double centreX() {
            return x + width / 2;
        }

        double centreY() {
            return y + height / 2;
        }
    }

    /**
     * Hand-picked practice areas for known tutorial slides, keyed by image name, as {@code {x, y, width,
     * height}} in full-resolution pixels. Read them off QuPath's status bar (cursor position) or a drawn
     * annotation. Slides not listed here are scanned.
     */
    static final Map<String, double[]> PRESETS = Map.of();

    private static final Logger logger = LoggerFactory.getLogger(SlideTargetFinder.class);

    /** Longest side of the low-resolution overview scanned for brown tissue. */
    private static final int SCAN_SIZE = 2048;

    /** Side of the practice area: a few hundred cells. */
    private static final double TARGET_MICRONS = 400;

    /** Fallback when uncalibrated: this fraction of the slide's width. */
    private static final double TARGET_FRACTION = 0.03;

    private static final double MIN_MEAN_DAB = 0.05;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tutorial-slide-scan");
        t.setDaemon(true);
        return t;
    });

    private ImageData<BufferedImage> scannedImage;
    private ColorDeconvolutionStains scannedStains;
    private volatile Target target;

    /** Starts a scan if {@code imageData} (or its stain vectors) changed since the last one. FX thread. */
    void update(ImageData<BufferedImage> imageData) {
        if (imageData == null) {
            scannedImage = null;
            target = null;
            return;
        }
        ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
        if (imageData == scannedImage && stains == scannedStains) {
            return;
        }
        scannedImage = imageData;
        scannedStains = stains;
        target = null;

        String name = imageData.getServer().getMetadata().getName();
        double[] preset = PRESETS.get(name);
        if (preset != null) {
            target = new Target(preset[0], preset[1], preset[2], preset[3], true);
            return;
        }
        executor.submit(() -> {
            Target found = scan(imageData, stains);
            Platform.runLater(() -> {
                if (scannedImage == imageData && scannedStains == stains) {
                    target = found;
                }
            });
        });
    }

    /** The practice area for the current image, or {@code null} if none is known (yet). */
    Target current() {
        return target;
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private static Target scan(ImageData<BufferedImage> imageData, ColorDeconvolutionStains stains) {
        try {
            ImageServer<BufferedImage> server = imageData.getServer();
            int width = server.getWidth();
            int height = server.getHeight();
            double downsample = Math.max(1, Math.max(width, height) / (double) SCAN_SIZE);
            BufferedImage overview = server.readRegion(downsample, 0, 0, width, height);
            int gw = overview.getWidth();
            int gh = overview.getHeight();
            int[] rgb = overview.getRGB(0, 0, gw, gh, null, 0, gw);

            float[] dab = new float[rgb.length];
            if (stains != null) {
                ColorTransformer.getTransformedPixels(rgb, ColorTransformer.ColorTransformMethod.Stain_2, dab, stains);
            } else {
                ColorTransformer.getSimpleTransformedPixels(rgb, ColorTransformer.ColorTransformMethod.DAB_H_DAB, dab);
            }

            var cal = server.getPixelCalibration();
            double sidePixels = cal.hasPixelSizeMicrons()
                    ? TARGET_MICRONS / cal.getAveragedPixelSizeMicrons()
                    : width * TARGET_FRACTION;
            int box = Math.max(2, (int) Math.round(sidePixels / downsample));

            return DabHotspot.find(dab, gw, gh, box, box, MIN_MEAN_DAB)
                    .map(h -> new Target(
                            h.x() * downsample, h.y() * downsample, box * downsample, box * downsample, false))
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Could not scan the slide for a practice area", e);
            return null;
        }
    }
}
