package qupath.ext.tutorials.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdleTimerTest {

    @Test
    void nudgesOnlyAfterNothingChangesForTheWholeDelay() {
        var still =
                TutorialState.builder().image("OS-2.ndpi", "BRIGHTFIELD_H_DAB").build();
        var zoomed = TutorialState.builder()
                .image("OS-2.ndpi", "BRIGHTFIELD_H_DAB")
                .zoom(2, 0.9)
                .build();
        var idle = new IdleTimer();
        idle.reset(still, 0);

        assertFalse(idle.onPoll(still, IdleTimer.NUDGE_AFTER_MS - 1));
        assertFalse(idle.onPoll(zoomed, IdleTimer.NUDGE_AFTER_MS + 1), "any change restarts the clock");
        assertFalse(idle.onPoll(zoomed, 2 * IdleTimer.NUDGE_AFTER_MS));
        assertTrue(idle.onPoll(zoomed, 2 * IdleTimer.NUDGE_AFTER_MS + 1));
    }
}
