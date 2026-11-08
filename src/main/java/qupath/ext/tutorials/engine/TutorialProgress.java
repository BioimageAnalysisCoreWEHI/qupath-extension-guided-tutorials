package qupath.ext.tutorials.engine;

import java.util.Objects;

/**
 * Pure step-advancement rules, separated from the FX glue so they are unit-testable.
 *
 * <ul>
 *   <li>A step auto-advances when it <em>becomes</em> complete while the user is on it, and stays
 *       complete <em>with the same message</em> for {@link #STABLE_POLLS} consecutive polls. Messages
 *       carry counts ("Detected 1,088 cells"), so a detection run that is still adding cells in batches
 *       keeps resetting the streak and the step waits for the final number. It also leaves the green
 *       confirmation on screen long enough to read.
 *   <li>A step that is already complete when reached <em>moving forward</em> is skipped
 *       ({@link Decision#SKIP}) — e.g. QuPath already asked for the image type when the slide opened,
 *       so there is nothing to point at.
 *   <li>A step that is already complete when reached by <em>Back</em> reports
 *       {@link Decision#ALREADY_DONE} so the panel offers Next instead; otherwise Back would bounce
 *       straight forward again.
 *   <li>Manual steps and the last step never auto-advance.
 * </ul>
 */
public final class TutorialProgress {

    /** Consecutive identical complete polls required before auto-advancing (3 × 500 ms). */
    static final int STABLE_POLLS = 3;

    public enum Decision {
        STAY,
        ALREADY_DONE,
        /** The step became complete; the index has moved on. */
        ADVANCE,
        /** The step was already complete on arrival; the index has moved on. */
        SKIP
    }

    private final int stepCount;
    private int index;
    private boolean forward;
    private boolean firstPoll;
    private boolean sawIncomplete;
    private boolean alreadyDone;
    private int completeStreak;
    private String streakMessage;

    public TutorialProgress(int stepCount) {
        if (stepCount <= 0) {
            throw new IllegalArgumentException("A tutorial needs at least one step");
        }
        this.stepCount = stepCount;
        enter(0, true);
    }

    public int index() {
        return index;
    }

    public int stepCount() {
        return stepCount;
    }

    public boolean isLast() {
        return index == stepCount - 1;
    }

    public boolean isFirst() {
        return index == 0;
    }

    /**
     * Feeds one poll's status for the current step.
     *
     * @return what the panel should do; on {@link Decision#ADVANCE}/{@link Decision#SKIP} the index
     *     has already moved
     */
    public Decision onPoll(StepStatus status, boolean manual) {
        boolean wasFirstPoll = firstPoll;
        firstPoll = false;
        if (manual) {
            return Decision.STAY;
        }
        if (!status.complete()) {
            sawIncomplete = true;
            alreadyDone = false;
            completeStreak = 0;
            return Decision.STAY;
        }
        if (wasFirstPoll) {
            if (forward && !isLast()) {
                enter(index + 1, true);
                return Decision.SKIP;
            }
            alreadyDone = true;
        }
        if (alreadyDone) {
            return Decision.ALREADY_DONE;
        }
        if (completeStreak == 0 || !Objects.equals(status.message(), streakMessage)) {
            completeStreak = 0;
            streakMessage = status.message();
        }
        completeStreak++;
        if (sawIncomplete && completeStreak >= STABLE_POLLS && !isLast()) {
            enter(index + 1, true);
            return Decision.ADVANCE;
        }
        return Decision.STAY;
    }

    /** Moves forward one step (Next / Skip); no-op on the last step. */
    public void next() {
        if (!isLast()) {
            enter(index + 1, true);
        }
    }

    /** Moves back one step; no-op on the first step. */
    public void back() {
        if (!isFirst()) {
            enter(index - 1, false);
        }
    }

    private void enter(int newIndex, boolean movingForward) {
        index = newIndex;
        forward = movingForward;
        firstPoll = true;
        sawIncomplete = false;
        alreadyDone = false;
        completeStreak = 0;
        streakMessage = null;
    }
}
