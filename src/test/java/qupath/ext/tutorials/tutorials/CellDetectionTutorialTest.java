package qupath.ext.tutorials.tutorials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.tutorials.engine.StepStatus;
import qupath.ext.tutorials.engine.TutorialState;

class CellDetectionTutorialTest {

    private static TutorialState.Builder slide() {
        return TutorialState.builder().image("OS-2.ndpi", "BRIGHTFIELD_H_DAB");
    }

    @Test
    void imageStepWaitsMatchesTheSampleAndAcceptsOwnSlideOnlyWhenChosen() {
        assertFalse(CellDetectionTutorial.checkImage(TutorialState.builder().build())
                .complete());
        assertTrue(CellDetectionTutorial.checkImage(slide().build()).complete());

        var other = TutorialState.builder().image("other.svs", "UNSET");
        StepStatus wrong = CellDetectionTutorial.checkImage(other.build());
        assertFalse(wrong.complete());
        assertEquals(StepStatus.Level.WARN, wrong.level());
        assertTrue(
                CellDetectionTutorial.checkImage(other.ownSlide(true).build()).complete());
    }

    @Test
    void imageTypeStepRequiresHDab() {
        assertTrue(CellDetectionTutorial.checkImageType(slide().build()).complete());
        StepStatus he = CellDetectionTutorial.checkImageType(
                TutorialState.builder().image("OS-2.ndpi", "BRIGHTFIELD_H_E").build());
        assertFalse(he.complete());
        assertTrue(he.message().contains("Brightfield (H&E)"));
    }

    @Test
    void zoomStepUsesMicronsPerScreenPixelOrDownsampleWhenUncalibrated() {
        assertFalse(
                CellDetectionTutorial.checkZoom(slide().zoom(64, 29).build()).complete());
        assertTrue(CellDetectionTutorial.checkZoom(slide().zoom(2, 0.9).build()).complete());
        assertTrue(CellDetectionTutorial.checkZoom(slide().zoom(4, Double.NaN).build())
                .complete());
    }

    @Test
    void largeRegionCompletesWithAWarning() {
        StepStatus s =
                CellDetectionTutorial.checkRegion(slide().annotations(1, 5e6).build());
        assertTrue(s.complete());
        assertEquals(StepStatus.Level.WARN, s.level());
    }

    @Test
    void overlayAndTableStepsCompareAgainstTheStateOnArrival() {
        var entry = slide().overlay(true, false).windowTitles(List.of()).build();
        assertFalse(CellDetectionTutorial.checkOverlayToggled(entry, entry).complete());
        assertTrue(CellDetectionTutorial.checkOverlayToggled(
                        slide().overlay(true, true).build(), entry)
                .complete());

        assertTrue(CellDetectionTutorial.checkNewWindow(
                        slide().windowTitles(List.of("Detections: OS-2.ndpi")).build(), entry)
                .complete());
    }

    @Test
    void detectionStillRunningIsNotDoneEvenWithSomeCells() {
        var running = slide().windowTitles(List.of("Positive cell detection"))
                .annotations(1, 250_000)
                .detections(640, 60, 580)
                .detectionRunning(true)
                .build();
        StepStatus s = CellDetectionTutorial.checkDetection(running);
        assertFalse(s.complete());
        assertTrue(s.message().contains("640 cells so far"), s.message());
    }

    @Test
    void linkedSelectionStepsNeedADifferentCellAndAnOpenTable() {
        var entry = slide().windowTitles(List.of("Detections"))
                .selected("detection", "Positive", "cell-a")
                .build();
        assertFalse(CellDetectionTutorial.checkRowClicked(entry, entry).complete(), "same cell still selected");
        assertTrue(CellDetectionTutorial.checkRowClicked(
                        slide().windowTitles(List.of("Detections"))
                                .selected("detection", "Negative", "cell-b")
                                .build(),
                        entry)
                .complete());

        StepStatus closed = CellDetectionTutorial.checkCellClickedOnImage(
                slide().selected("detection", "Negative", "cell-b").build(), entry);
        assertFalse(closed.complete());
        assertTrue(closed.message().contains("table was closed"));
    }

    @Test
    void resultShowsDerivationOrExplainsMissingScoring() {
        StepStatus ok = CellDetectionTutorial.checkResult(
                slide().annotations(1, 250_000).detections(1088, 135, 953).build());
        assertTrue(ok.message().contains("135 positive of 1,088"), ok.message());
        assertTrue(ok.message().contains("12.4% positive"), ok.message());
        assertTrue(ok.message().contains("threshold"), "the threshold caveat travels with the number");

        StepStatus unscored = CellDetectionTutorial.checkResult(
                slide().annotations(1, 250_000).detections(500, 0, 0).build());
        assertFalse(unscored.complete());
        assertTrue(unscored.message().contains("Single threshold"));
    }
}
