package qupath.ext.tutorials.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import qupath.ext.tutorials.engine.TutorialProgress.Decision;

class TutorialProgressTest {

    private static final StepStatus WAITING = StepStatus.waiting("");
    private static final StepStatus DONE = StepStatus.done("");

    private static void pollDone(TutorialProgress p, int times) {
        for (int i = 0; i < times; i++) {
            assertEquals(Decision.STAY, p.onPoll(DONE, false));
        }
    }

    @Test
    void advancesOnlyAfterBecomingCompleteAndStayingUnchanged() {
        var p = new TutorialProgress(3);
        assertEquals(Decision.STAY, p.onPoll(WAITING, false));

        // Detection adds cells in batches: a changing count keeps resetting the streak.
        assertEquals(Decision.STAY, p.onPoll(StepStatus.done("Detected 1 cells."), false));
        assertEquals(Decision.STAY, p.onPoll(StepStatus.done("Detected 1 cells."), false));
        assertEquals(Decision.STAY, p.onPoll(StepStatus.done("Detected 640 cells."), false));
        assertEquals(Decision.STAY, p.onPoll(StepStatus.done("Detected 1,088 cells."), false));
        assertEquals(Decision.STAY, p.onPoll(StepStatus.done("Detected 1,088 cells."), false));
        assertEquals(Decision.ADVANCE, p.onPoll(StepStatus.done("Detected 1,088 cells."), false));
        assertEquals(1, p.index());
    }

    @Test
    void alreadyCompleteStepIsSkippedGoingForwardButOffersNextGoingBack() {
        var p = new TutorialProgress(3);
        assertEquals(Decision.SKIP, p.onPoll(DONE, false), "e.g. image type set when the slide opened");
        assertEquals(1, p.index());

        p.back();
        assertEquals(Decision.ALREADY_DONE, p.onPoll(DONE, false), "Back must not bounce forward");
        assertEquals(Decision.ALREADY_DONE, p.onPoll(DONE, false));
        assertEquals(0, p.index());

        // Undoing and redoing the step while on it does advance.
        p.onPoll(WAITING, false);
        pollDone(p, TutorialProgress.STABLE_POLLS - 1);
        assertEquals(Decision.ADVANCE, p.onPoll(DONE, false));
    }

    @Test
    void manualAndLastStepsNeverAutoAdvance() {
        var p = new TutorialProgress(2);
        p.onPoll(WAITING, true);
        assertEquals(Decision.STAY, p.onPoll(DONE, true));

        p.next();
        assertEquals(Decision.ALREADY_DONE, p.onPoll(DONE, false), "the last step is never skipped");
        p.onPoll(WAITING, false);
        pollDone(p, TutorialProgress.STABLE_POLLS + 1);
        assertEquals(1, p.index());
    }
}
