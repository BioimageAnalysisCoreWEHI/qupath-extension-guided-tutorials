package qupath.ext.tutorials.engine;

import java.util.Objects;

/**
 * Notices when the user seems stuck: nothing in QuPath has changed for {@link #NUDGE_AFTER_MS} since
 * the step began or since the last change. Pure; the caller supplies the clock.
 */
public final class IdleTimer {

    /** How long a step may sit unchanged before the tutorial tries to get attention. */
    public static final long NUDGE_AFTER_MS = 12_000;

    private TutorialState lastState;
    private long lastChange;

    /** Starts timing a new step. */
    public void reset(TutorialState state, long nowMs) {
        lastState = state;
        lastChange = nowMs;
    }

    /**
     * @return whether the user has been idle long enough to nudge; any change in {@code state} restarts
     *     the clock
     */
    public boolean onPoll(TutorialState state, long nowMs) {
        if (!Objects.equals(state, lastState)) {
            lastState = state;
            lastChange = nowMs;
            return false;
        }
        return nowMs - lastChange >= NUDGE_AFTER_MS;
    }
}
