package qupath.ext.tutorials.live;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.tutorials.engine.TutorialState;
import qupath.ext.tutorials.support.PositivePercent;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.ParallelTileObject;
import qupath.lib.roi.interfaces.ROI;

/**
 * Builds a {@link TutorialState} from live QuPath state. The only class here that reads the image
 * hierarchy. Call from the FX thread only.
 *
 * <p>Hierarchy-derived numbers (annotation count, the tracked annotation's area and child counts) are
 * cached and recomputed only after a hierarchy event or a selection change, so polling stays cheap even
 * once thousands of detections exist.
 */
final class StateReader {

    private static final Logger logger = LoggerFactory.getLogger(StateReader.class);

    private final QuPathGUI qupath;
    private final PathObjectHierarchyListener listener = event -> markDirty();

    private ImageData<BufferedImage> watchedImage;
    private PathObjectHierarchy watchedHierarchy;
    private volatile boolean dirty = true;

    private PathObject tracked;
    private int annotationCount;
    private double trackedArea = Double.NaN;
    private int childCount;
    private int positive;
    private int negative;
    private boolean running;

    StateReader(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    TutorialState read(boolean ownSlide) {
        List<String> titles = Window.getWindows().stream()
                .filter(w -> w instanceof Stage)
                .map(w -> (Stage) w)
                .filter(Stage::isShowing)
                .filter(s -> s != qupath.getStage())
                .map(Stage::getTitle)
                .filter(Objects::nonNull)
                .toList();
        var builder = TutorialState.builder().ownSlide(ownSlide).windowTitles(titles);

        ImageData<BufferedImage> imageData = qupath.getImageData();
        if (imageData != watchedImage) {
            watchedImage = imageData;
            tracked = null;
            markDirty();
        }
        if (imageData == null) {
            watch(null);
            return builder.build();
        }
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        watch(hierarchy);

        PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
        if (selected != null && selected.isAnnotation() && selected != tracked) {
            tracked = selected;
            markDirty();
        }
        if (dirty) {
            recompute(imageData, hierarchy);
        }

        QuPathViewer viewer = qupath.getViewer();
        if (viewer != null) {
            double downsample = viewer.getDownsampleFactor();
            var cal = imageData.getServer().getPixelCalibration();
            builder.zoom(
                    downsample,
                    cal.hasPixelSizeMicrons() ? downsample * cal.getAveragedPixelSizeMicrons() : Double.NaN);
            var overlay = viewer.getOverlayOptions();
            builder.overlay(overlay.getShowDetections(), overlay.getFillDetections());
        }
        if (selected != null) {
            String kind = selected.isAnnotation() ? "annotation" : selected.isDetection() ? "detection" : "object";
            builder.selected(
                    kind,
                    selected.getPathClass() == null
                            ? null
                            : selected.getPathClass().toString(),
                    selected.getID().toString());
        }

        return builder.image(
                        imageData.getServer().getMetadata().getName(),
                        imageData.getImageType().name())
                .annotations(annotationCount, trackedArea)
                .detections(childCount, positive, negative)
                .detectionRunning(running)
                .build();
    }

    /** The tracked annotation's ROI, for zooming to it; {@code null} if there is none. */
    ROI trackedRoi() {
        return tracked == null ? null : tracked.getROI();
    }

    /** Detaches the hierarchy listener. */
    void close() {
        watch(null);
    }

    private void markDirty() {
        dirty = true;
    }

    private void watch(PathObjectHierarchy hierarchy) {
        if (hierarchy == watchedHierarchy) {
            return;
        }
        if (watchedHierarchy != null) {
            watchedHierarchy.removeListener(listener);
        }
        watchedHierarchy = hierarchy;
        if (hierarchy != null) {
            hierarchy.addListener(listener);
        }
        markDirty();
    }

    private void recompute(ImageData<BufferedImage> imageData, PathObjectHierarchy hierarchy) {
        try {
            Collection<PathObject> annotations = hierarchy.getAnnotationObjects();
            annotationCount = annotations.size();
            if (tracked != null && !annotations.contains(tracked)) {
                tracked = null;
            }
            if (tracked == null) {
                for (PathObject annotation : annotations) {
                    tracked = annotation; // keep the last — the most recently added
                }
            }

            trackedArea = Double.NaN;
            childCount = 0;
            positive = 0;
            negative = 0;
            running = false;
            if (tracked != null) {
                var cal = imageData.getServer().getPixelCalibration();
                if (tracked.getROI() != null && cal.hasPixelSizeMicrons()) {
                    trackedArea =
                            tracked.getROI().getScaledArea(cal.getPixelWidthMicrons(), cal.getPixelHeightMicrons());
                }
                for (PathObject child : List.copyOf(tracked.getChildObjects())) {
                    // While a command runs, QuPath adds temporary progress tiles (ParallelTileObject, a
                    // detection subclass) inside the region. They are not cells: counting them is what
                    // made the tutorial report "Detected 1 cells" mid-run.
                    if (child instanceof ParallelTileObject) {
                        running = true;
                        continue;
                    }
                    if (!child.isDetection() || child.isTile()) {
                        continue;
                    }
                    childCount++;
                    String name = child.getPathClass() == null
                            ? null
                            : child.getPathClass().toString();
                    if (PositivePercent.isPositive(name)) {
                        positive++;
                    } else if (PositivePercent.isNegative(name)) {
                        negative++;
                    }
                }
            }
            dirty = false;
        } catch (RuntimeException e) {
            // A background command (e.g. detection) can be mid-update; keep dirty and retry next poll.
            logger.debug("Hierarchy changed while reading; retrying", e);
        }
    }
}
