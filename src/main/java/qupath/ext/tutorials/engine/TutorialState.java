package qupath.ext.tutorials.engine;

import java.util.List;

/**
 * A detached snapshot of the QuPath state a tutorial step can check. Built on the FX thread from
 * cheap reads only; every step check is a pure function of this record, so steps are unit-testable
 * without a running QuPath.
 *
 * <p>The <em>tracked annotation</em> is the annotation the user is working on: the selected one if an
 * annotation is selected, otherwise the most recent one.
 *
 * @param imageOpen whether an image is open in the viewer
 * @param imageName the open image's name, or {@code null}
 * @param imageType QuPath's {@code ImageData.ImageType} name, or {@code null}
 * @param ownSlide whether the user chose to follow along on their own slide
 * @param annotationCount number of annotations in the hierarchy
 * @param trackedAreaMicrons2 tracked annotation's area in µm²; {@code NaN} if none or uncalibrated
 * @param trackedChildCount number of detections inside the tracked annotation
 * @param positiveCount children classified Positive (incl. compound e.g. {@code "Tumor: Positive"})
 * @param negativeCount children classified Negative
 * @param windowTitles titles of every showing window other than QuPath's main window
 * @param viewerDownsample the viewer's current downsample (1 = full resolution); {@code NaN} if none
 * @param micronsPerScreenPixel how many µm one screen pixel covers at the current zoom; {@code NaN} if
 *     uncalibrated
 * @param showDetections whether the viewer is drawing detections (toggled with D)
 * @param fillDetections whether detections are drawn filled (toggled with F)
 * @param selectedKind {@code "annotation"}, {@code "detection"}, another object type, or {@code null}
 * @param selectedClass the selected object's classification, or {@code null}
 * @param selectedId the selected object's unique ID, or {@code null} — tells "a different cell was
 *     selected" apart from "the same cell is still selected"
 * @param detectionRunning whether a detection command is still working on the tracked annotation
 *     (QuPath shows its progress as temporary tile objects inside it, which are never counted as cells)
 */
public record TutorialState(
        boolean imageOpen,
        String imageName,
        String imageType,
        boolean ownSlide,
        int annotationCount,
        double trackedAreaMicrons2,
        int trackedChildCount,
        int positiveCount,
        int negativeCount,
        List<String> windowTitles,
        double viewerDownsample,
        double micronsPerScreenPixel,
        boolean showDetections,
        boolean fillDetections,
        String selectedKind,
        String selectedClass,
        String selectedId,
        boolean detectionRunning) {

    public static Builder builder() {
        return new Builder();
    }

    /** Whether a showing window's title contains {@code fragment}, case-insensitively. */
    public boolean hasWindowContaining(String fragment) {
        String needle = fragment.toLowerCase();
        return windowTitles.stream().anyMatch(t -> t != null && t.toLowerCase().contains(needle));
    }

    /** Mutable builder; every field defaults to "no image, nothing selected". */
    public static final class Builder {
        private boolean imageOpen;
        private String imageName;
        private String imageType;
        private boolean ownSlide;
        private int annotationCount;
        private double trackedAreaMicrons2 = Double.NaN;
        private int trackedChildCount;
        private int positiveCount;
        private int negativeCount;
        private List<String> windowTitles = List.of();
        private double viewerDownsample = Double.NaN;
        private double micronsPerScreenPixel = Double.NaN;
        private boolean showDetections = true;
        private boolean fillDetections;
        private String selectedKind;
        private String selectedClass;
        private String selectedId;
        private boolean detectionRunning;

        public Builder detectionRunning(boolean running) {
            this.detectionRunning = running;
            return this;
        }

        public Builder image(String name, String type) {
            this.imageOpen = true;
            this.imageName = name;
            this.imageType = type;
            return this;
        }

        public Builder ownSlide(boolean ownSlide) {
            this.ownSlide = ownSlide;
            return this;
        }

        public Builder annotations(int count, double trackedAreaMicrons2) {
            this.annotationCount = count;
            this.trackedAreaMicrons2 = trackedAreaMicrons2;
            return this;
        }

        public Builder detections(int total, int positive, int negative) {
            this.trackedChildCount = total;
            this.positiveCount = positive;
            this.negativeCount = negative;
            return this;
        }

        public Builder windowTitles(List<String> windowTitles) {
            this.windowTitles = List.copyOf(windowTitles);
            return this;
        }

        public Builder zoom(double viewerDownsample, double micronsPerScreenPixel) {
            this.viewerDownsample = viewerDownsample;
            this.micronsPerScreenPixel = micronsPerScreenPixel;
            return this;
        }

        public Builder overlay(boolean showDetections, boolean fillDetections) {
            this.showDetections = showDetections;
            this.fillDetections = fillDetections;
            return this;
        }

        public Builder selected(String kind, String pathClass, String id) {
            this.selectedKind = kind;
            this.selectedClass = pathClass;
            this.selectedId = id;
            return this;
        }

        public TutorialState build() {
            return new TutorialState(
                    imageOpen,
                    imageName,
                    imageType,
                    ownSlide,
                    annotationCount,
                    trackedAreaMicrons2,
                    trackedChildCount,
                    positiveCount,
                    negativeCount,
                    windowTitles,
                    viewerDownsample,
                    micronsPerScreenPixel,
                    showDetections,
                    fillDetections,
                    selectedKind,
                    selectedClass,
                    selectedId,
                    detectionRunning);
        }
    }
}
