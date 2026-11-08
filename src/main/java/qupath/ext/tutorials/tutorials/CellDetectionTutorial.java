package qupath.ext.tutorials.tutorials;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import qupath.ext.tutorials.engine.HighlightTarget;
import qupath.ext.tutorials.engine.Step;
import qupath.ext.tutorials.engine.StepAction;
import qupath.ext.tutorials.engine.StepStatus;
import qupath.ext.tutorials.engine.Tip;
import qupath.ext.tutorials.engine.Tutorial;
import qupath.ext.tutorials.engine.TutorialState;
import qupath.ext.tutorials.support.PositivePercent;

/**
 * QuPath's own <em>Cell detection</em> tutorial (Ki67 labelling index on {@code OS-2.ndpi}), as a
 * guided walkthrough for someone who has never used QuPath: get the slide, set the image type, zoom
 * in, draw a region, run Positive cell detection, then learn to look at the result — zoom, fill/hide
 * detections, inspect one cell, open the measurement table — and where the numbers live.
 *
 * <p>Every check is a static, pure function of {@link TutorialState}, so the whole script is
 * unit-tested without QuPath.
 */
public final class CellDetectionTutorial {

    public static final String SOURCE_URL =
            "https://qupath.readthedocs.io/en/stable/docs/tutorials/cell_detection.html";

    /** OpenSlide test data; verified live 2026-11-08, image/tiff, 976,661,960 bytes. */
    public static final String SAMPLE_URL =
            "https://openslide.cs.cmu.edu/download/openslide-testdata/Hamamatsu/OS-2.ndpi";

    static final String SAMPLE_NAME = "OS-2";
    static final String DIALOG = "Positive cell detection";
    static final String TABLE_COMMAND = "Show detection measurements";
    static final String H_DAB = "BRIGHTFIELD_H_DAB";

    /** Zoomed in enough to see nuclei (~8 µm across → ≥ 4 screen pixels). */
    static final double MAX_MICRONS_PER_SCREEN_PIXEL = 2.0;

    /** Fallback when the image has no pixel size. */
    static final double MAX_DOWNSAMPLE_UNCALIBRATED = 4.0;

    /** Above this the region is "large" for a first run; detection still works, just slowly. */
    static final double LARGE_REGION_MICRONS2 = 2_000_000; // 2 mm²

    /** The tutorial page's own example result, for comparison only — never a pass/fail target. */
    static final int TUTORIAL_CELLS = 1088;

    static final double TUTORIAL_PERCENT = 12.4;

    private CellDetectionTutorial() {}

    public static Tutorial create() {
        return new Tutorial(
                "Cell detection (Ki67)",
                SOURCE_URL,
                List.of(
                        new Step(
                                "Get the sample slide",
                                """
                                This follows QuPath's Cell detection tutorial, using a Ki67-stained \
                                breast tumour slide from the OpenSlide test data (OS-2.ndpi, about 1 GB).

                                Download it, then drag it onto the QuPath window, or use "I already have \
                                it".

                                → Open OS-2.ndpi in QuPath. I'll move on as soon as it opens.""",
                                List.of(
                                        StepAction.openUrl("Open download page", SAMPLE_URL),
                                        StepAction.openFile("I already have it…"),
                                        StepAction.ownSlide("Use my own slide")),
                                List.of(),
                                (s, entry) -> checkImage(s),
                                false,
                                List.of(Tip.drag("This panel covering the image? Drag it by its title bar to move "
                                        + "it aside, even outside QuPath's window."))),
                        new Step(
                                "Set the image type",
                                """
                                Cell detection's brown (DAB) measurements only make sense if QuPath \
                                knows this is a brightfield H-DAB image.

                                → In the Image tab on the left, double-click "Image type" and choose \
                                "Brightfield (H-DAB)".""",
                                List.of(StepAction.selectTab("Show the Image tab", "Image")),
                                List.of(
                                        HighlightTarget.analysisTab("Image", "1. Open the Image tab"),
                                        HighlightTarget.analysisRow("Image type", "2. Double-click this row"),
                                        HighlightTarget.choice("Brightfield (H-DAB)")),
                                (s, entry) -> checkImageType(s),
                                false),
                        new Step(
                                "Zoom in on some cells",
                                """
                                You're looking at the whole slide. Find an area of tumour (lots of small \
                                blue and brown dots), or press "Take me to a good area" and I'll jump to \
                                a cell-rich spot with plenty of brown staining.

                                → Zoom in until you can see individual nuclei. I'll move on when you're \
                                close enough.""",
                                List.of(StepAction.goToGoodArea("Take me to a good area")),
                                List.of(HighlightTarget.viewer("Scroll here to zoom in")),
                                (s, entry) -> checkZoom(s),
                                false,
                                List.of(
                                        Tip.scroll("Scroll over the image to zoom in and out"),
                                        Tip.shortcutsOf("Zoom in / out from the keyboard", "Zoom in", "Zoom out"),
                                        Tip.drag("With the Move tool, click and drag to move around"),
                                        Tip.keys("Move tool", "M"))),
                        new Step(
                                "Draw a box around some cells",
                                """
                                Now outline the cells you want to count:

                                  1.  Click the Rectangle tool (outlined), or press R.
                                  2.  On the image, press the mouse button at one corner of your area, \
                                hold it down, and drag to the opposite corner.
                                  3.  Let go. The box stays on the image as an outline. QuPath calls this \
                                an annotation.

                                Keep it small for a first try: a few hundred cells.

                                → Draw one box. I'll move on as soon as it's there.""",
                                List.of(),
                                List.of(HighlightTarget.toolbar("rectangle", "Rectangle tool (R)")),
                                (s, entry) -> checkRegion(s),
                                false,
                                List.of(
                                        Tip.keys("Rectangle tool", "R"),
                                        Tip.drag("Press, hold and drag from one corner to the other"),
                                        Tip.shortcutsOf("Undo a mistake", "Undo")),
                                Step.Demo.DRAW_RECTANGLE),
                        new Step(
                                "Open Positive cell detection",
                                """
                                With your box still selected, open the detection command from the menu \
                                bar at the top of QuPath. The outlines follow you through the menu, or \
                                press the button below to jump straight there.

                                → Open Analyze ▸ Cell detection ▸ Positive cell detection.""",
                                List.of(StepAction.openCommand("Open it for me", DIALOG)),
                                List.of(
                                        HighlightTarget.menuBar("Analyze", "1. Click Analyze"),
                                        HighlightTarget.menuItem("Cell detection", "2. Cell detection"),
                                        HighlightTarget.menuItem(DIALOG, "3. Click this")),
                                (s, entry) -> checkDialogOpen(s),
                                false),
                        new Step(
                                "Choose the settings",
                                """
                                Most defaults are fine. Change two things in the dialog:

                                  •  Score compartment → "Nucleus: DAB OD mean"
                                  •  Tick "Single threshold", so each cell is called Positive or \
                                Negative

                                → Press Next when both are set. Don't press Run yet.""",
                                List.of(),
                                List.of(
                                        HighlightTarget.dialogField(
                                                DIALOG, "Score compartment", "Nucleus: DAB OD mean"),
                                        HighlightTarget.dialogField(DIALOG, "Single threshold", "Tick this"),
                                        HighlightTarget.dialogButtonNotYet(
                                                DIALOG, "Run", "Not yet: set the fields first")),
                                (s, entry) -> checkSettings(s),
                                true),
                        new Step(
                                "Run detection",
                                """
                                QuPath will find every nucleus in your box and score its brown staining. \
                                It takes a few seconds.

                                → Press Run in the dialog.""",
                                List.of(),
                                List.of(HighlightTarget.dialogButton(DIALOG, "Run", "Press Run")),
                                (s, entry) -> checkDetection(s),
                                false),
                        new Step(
                                "Close the dialog",
                                """
                                Detection is done.

                                → Close the Positive cell detection window with the X in its corner \
                                (outlined).""",
                                List.of(),
                                List.of(HighlightTarget.windowClose(DIALOG, "Close this window")),
                                (s, entry) -> checkDialogClosed(s),
                                false,
                                List.of(Tip.reassure("Nothing is lost when you close it. Your cells stay on the "
                                        + "image, and QuPath records the command and its settings in the "
                                        + "Workflow tab."))),
                        new Step(
                                "Look at your cells",
                                """
                                Every nucleus QuPath found now has an outline: red for Positive (brown \
                                staining), blue for Negative.

                                  1.  Press M for the Move tool, so clicking doesn't draw new boxes.
                                  2.  Press "Zoom to my region".
                                  3.  Zoom in further with the mouse wheel and look around.

                                Ask yourself: do the outlines sit on real nuclei? Are brown cells red and \
                                blue cells blue?

                                → Press Next when you've had a look.""",
                                List.of(StepAction.zoomToRegion("Zoom to my region")),
                                List.of(HighlightTarget.toolbar("move", "Move tool (M)")),
                                (s, entry) -> checkLooking(s),
                                true,
                                List.of(
                                        Tip.keys("Move tool", "M"),
                                        Tip.scroll("Zoom in further to check individual cells"))),
                        new Step(
                                "Fill or hide the cells",
                                """
                                Two keyboard shortcuts you'll use all the time. The matching toolbar \
                                buttons are outlined too.

                                → Press F or D. If nothing happens, click once on the image first (with \
                                the Move tool) and try again.""",
                                List.of(),
                                List.of(
                                        HighlightTarget.toolbar("fill", "F"),
                                        HighlightTarget.toolbar("detection", "fill", "D")),
                                CellDetectionTutorial::checkOverlayToggled,
                                false,
                                List.of(
                                        Tip.keys("Fill each cell with its colour, or back to outlines", "F"),
                                        Tip.keys("Hide or show all cells, to see the tissue underneath", "D"))),
                        new Step(
                                "Inspect one cell",
                                """
                                QuPath lists a selected cell's measurements (size, shape, brown \
                                intensity and more) in the panel on the left.

                                → With the Move tool, double-click inside any outlined cell.""",
                                List.of(),
                                List.of(HighlightTarget.toolbar("move", "Move tool (M)")),
                                (s, entry) -> checkCellSelected(s),
                                false),
                        new Step(
                                "See every cell in a table",
                                """
                                The table shows all your cells at once. Each row is one cell, each column \
                                one measurement. Click a column header to sort, for example by \
                                "Nucleus: DAB OD mean", to see the most and least brown cells.

                                → Open Measure ▸ Show detection measurements.""",
                                List.of(StepAction.openCommand("Open it for me", TABLE_COMMAND)),
                                List.of(
                                        HighlightTarget.menuBar("Measure", "1. Click Measure"),
                                        HighlightTarget.menuItem(TABLE_COMMAND, "2. Click this")),
                                CellDetectionTutorial::checkNewWindow,
                                false),
                        new Step(
                                "Table → image",
                                """
                                The table and the image are linked: clicking a row selects that cell on \
                                the image and outlines it in yellow. Handy for checking outliers: sort a \
                                column, click the top row, and see what that cell actually looks like.

                                → Click any row in the table.""",
                                List.of(),
                                List.of(),
                                CellDetectionTutorial::checkRowClicked,
                                false),
                        new Step(
                                "Image → table",
                                """
                                It works the other way too: click a cell on the image and the table \
                                jumps to its row. Move the table window to one side if it covers the image.

                                → With the Move tool, click a different cell on the image.""",
                                List.of(),
                                List.of(HighlightTarget.toolbar("move", "Move tool (M)")),
                                CellDetectionTutorial::checkCellClickedOnImage,
                                false,
                                List.of(Tip.keys("Move tool", "M"))),
                        new Step("Your result", """
                                To find these numbers again, select your box (Move tool, click its \
                                outline) and look at the Annotations tab on the left: Num Detections, \
                                Num Positive and Positive %.""", List.of(), List.of(), (s, entry) -> checkResult(s), true)),
                """
                You went from a raw slide to a Ki67 labelling index:

                  ✓  set the image type so QuPath can separate the brown stain
                  ✓  zoomed in and drew a region
                  ✓  detected every nucleus and scored it Positive or Negative
                  ✓  checked the result by eye, cell by cell and in the table

                Your box and cells stay on the image. To keep them, save with File ▸ Save.

                Next: try it on your own slide, or follow QuPath's Cell classification tutorial.""");
    }

    static StepStatus checkImage(TutorialState s) {
        if (!s.imageOpen()) {
            return StepStatus.waiting(s.ownSlide() ? "Open your slide in QuPath…" : "Waiting for an image to open…");
        }
        if (s.imageName() != null && s.imageName().toUpperCase(Locale.ROOT).contains(SAMPLE_NAME)) {
            return StepStatus.done("Found " + s.imageName() + ".");
        }
        if (s.ownSlide()) {
            return StepStatus.done(
                    "Continuing on your own slide, " + s.imageName() + ". Your numbers won't match the tutorial's.");
        }
        return StepStatus.problem("That's \"" + s.imageName() + "\", not OS-2.ndpi. Open the tutorial slide, "
                + "or choose \"Use my own slide\".");
    }

    static StepStatus checkImageType(TutorialState s) {
        if (!s.imageOpen()) {
            return StepStatus.problem("No image is open. Go back a step.");
        }
        if (H_DAB.equals(s.imageType())) {
            return StepStatus.done("Image type is Brightfield (H-DAB).");
        }
        if (s.imageType() == null || "UNSET".equals(s.imageType())) {
            return StepStatus.waiting("Image type isn't set yet.");
        }
        String message =
                "Image type is " + prettyImageType(s.imageType()) + ". This tutorial needs Brightfield (H-DAB).";
        if (s.ownSlide()) {
            message += " If your slide isn't DAB-stained, positive/negative scoring won't mean anything.";
        }
        return StepStatus.problem(message);
    }

    static StepStatus checkZoom(TutorialState s) {
        if (!s.imageOpen()) {
            return StepStatus.problem("No image is open. Go back to the start.");
        }
        boolean close = Double.isNaN(s.micronsPerScreenPixel())
                ? s.viewerDownsample() <= MAX_DOWNSAMPLE_UNCALIBRATED
                : s.micronsPerScreenPixel() <= MAX_MICRONS_PER_SCREEN_PIXEL;
        return close
                ? StepStatus.done("Zoomed in. You can see individual nuclei.")
                : StepStatus.waiting("Keep zooming in. The nuclei are still too small to see clearly.");
    }

    static StepStatus checkRegion(TutorialState s) {
        if (!s.imageOpen()) {
            return StepStatus.problem("No image is open. Go back to the start.");
        }
        if (s.annotationCount() == 0) {
            return StepStatus.waiting("Waiting for you to draw a box…");
        }
        double area = s.trackedAreaMicrons2();
        if (!Double.isNaN(area) && area > LARGE_REGION_MICRONS2) {
            return StepStatus.doneWithWarning(
                    "Box drawn (" + formatArea(area) + "). That's large, so detection may take a while.");
        }
        return StepStatus.done(Double.isNaN(area) ? "Box drawn." : "Box drawn (" + formatArea(area) + ").");
    }

    static StepStatus checkDialogOpen(TutorialState s) {
        if (s.hasWindowContaining(DIALOG)) {
            return StepStatus.done("Dialog open.");
        }
        if (s.annotationCount() == 0) {
            return StepStatus.problem("Draw a box first. Go back a step.");
        }
        return StepStatus.waiting("Waiting for the dialog…");
    }

    static StepStatus checkSettings(TutorialState s) {
        if (!s.hasWindowContaining(DIALOG)) {
            return StepStatus.problem("The dialog was closed. Go back a step to reopen it.");
        }
        return StepStatus.waiting("The two fields are outlined in the dialog.");
    }

    static StepStatus checkDetection(TutorialState s) {
        if (s.detectionRunning()) {
            return StepStatus.waiting(
                    s.trackedChildCount() > 0
                            ? String.format(Locale.ROOT, "Detecting… %,d cells so far.", s.trackedChildCount())
                            : "QuPath is detecting cells. The tiles on your box show its progress…");
        }
        if (s.trackedChildCount() > 0) {
            return StepStatus.done(String.format(Locale.ROOT, "Detected %,d cells.", s.trackedChildCount()));
        }
        if (!s.hasWindowContaining(DIALOG)) {
            return StepStatus.problem("The dialog was closed before running. Go back two steps to reopen it.");
        }
        return StepStatus.waiting("Waiting for detection to finish…");
    }

    static StepStatus checkDialogClosed(TutorialState s) {
        return s.hasWindowContaining(DIALOG)
                ? StepStatus.waiting("Close the Positive cell detection window to continue.")
                : StepStatus.done("Dialog closed.");
    }

    static StepStatus checkLooking(TutorialState s) {
        if (s.trackedChildCount() == 0) {
            return StepStatus.problem("No cells in your box yet. Go back and run detection.");
        }
        return StepStatus.waiting(String.format(
                Locale.ROOT,
                "%,d cells: %,d red (Positive), %,d blue (Negative).",
                s.trackedChildCount(),
                s.positiveCount(),
                s.negativeCount()));
    }

    static StepStatus checkOverlayToggled(TutorialState s, TutorialState entry) {
        boolean toggled = s.fillDetections() != entry.fillDetections() || s.showDetections() != entry.showDetections();
        if (!toggled) {
            return StepStatus.waiting("Press F or D.");
        }
        return StepStatus.done("That's it. Press them again any time"
                + (s.showDetections() ? "." : ". The cells are hidden now; press D to bring them back."));
    }

    static StepStatus checkCellSelected(TutorialState s) {
        if ("detection".equals(s.selectedKind())) {
            String cls = s.selectedClass() == null ? "an unclassified" : "a " + s.selectedClass();
            return StepStatus.done("You selected " + cls + " cell. Its measurements are listed on the left.");
        }
        return StepStatus.waiting("Double-click inside one of the outlined cells.");
    }

    /** The measurement table's title isn't fixed, so wait for any window that wasn't open on arrival. */
    static StepStatus checkNewWindow(TutorialState s, TutorialState entry) {
        boolean opened = s.windowTitles().stream()
                .filter(Objects::nonNull)
                .anyMatch(t -> !entry.windowTitles().contains(t));
        return opened
                ? StepStatus.done("Table open. Close it whenever you like.")
                : StepStatus.waiting("Waiting for the table to open…");
    }

    static StepStatus checkRowClicked(TutorialState s, TutorialState entry) {
        if (tableClosed(s, entry)) {
            return StepStatus.problem("The table was closed. Go back a step to reopen it.");
        }
        return newCellSelected(s, entry)
                ? StepStatus.done("That row's cell is now selected on the image.")
                : StepStatus.waiting("Click any row in the table…");
    }

    static StepStatus checkCellClickedOnImage(TutorialState s, TutorialState entry) {
        if (tableClosed(s, entry)) {
            return StepStatus.problem("The table was closed. Go back two steps to reopen it.");
        }
        return newCellSelected(s, entry)
                ? StepStatus.done("Look at the table: it jumped to that cell's row.")
                : StepStatus.waiting("Click a different cell on the image…");
    }

    /** A detection is selected, and it isn't the one that was selected on arrival. */
    private static boolean newCellSelected(TutorialState s, TutorialState entry) {
        return "detection".equals(s.selectedKind()) && !Objects.equals(s.selectedId(), entry.selectedId());
    }

    /** Any window open on arrival (the table) has since closed. */
    private static boolean tableClosed(TutorialState s, TutorialState entry) {
        return !s.windowTitles().containsAll(entry.windowTitles());
    }

    static StepStatus checkResult(TutorialState s) {
        if (s.trackedChildCount() == 0) {
            return StepStatus.problem("No cells detected in your box yet. Go back and run detection.");
        }
        Optional<PositivePercent> pct = PositivePercent.of(s.positiveCount(), s.negativeCount());
        if (pct.isEmpty()) {
            return StepStatus.problem(String.format(
                    Locale.ROOT,
                    "%,d cells detected, but none were scored Positive or Negative. Check \"Single "
                            + "threshold\" was ticked, then go back and run again.",
                    s.trackedChildCount()));
        }
        PositivePercent p = pct.get();
        StringBuilder sb = new StringBuilder(String.format(
                Locale.ROOT,
                "%,d cells detected.%n%,d positive of %,d → %.1f%% positive%n(%s)",
                s.trackedChildCount(),
                p.numerator(),
                p.denominator(),
                p.value(),
                PositivePercent.FORMULA));
        if (!s.ownSlide()) {
            sb.append(String.format(
                    Locale.ROOT,
                    "%n%nThe tutorial's example region gave %,d cells and %.1f%% positive. Yours will "
                            + "differ because your region differs.",
                    TUTORIAL_CELLS,
                    TUTORIAL_PERCENT));
        }
        sb.append("\n\nPositive vs. negative depends on the threshold you chose. It's a decision, not a "
                + "measurement, so report it alongside the percentage.");
        return StepStatus.done(sb.toString());
    }

    static String prettyImageType(String type) {
        return switch (type) {
            case "BRIGHTFIELD_H_E" -> "Brightfield (H&E)";
            case "BRIGHTFIELD_H_DAB" -> "Brightfield (H-DAB)";
            case "BRIGHTFIELD_OTHER" -> "Brightfield (other)";
            case "FLUORESCENCE" -> "Fluorescence";
            case "OTHER" -> "Other";
            default -> type;
        };
    }

    static String formatArea(double microns2) {
        return microns2 >= 1_000_000
                ? String.format(Locale.ROOT, "%.2f mm²", microns2 / 1_000_000)
                : String.format(Locale.ROOT, "%,.0f µm²", microns2);
    }
}
