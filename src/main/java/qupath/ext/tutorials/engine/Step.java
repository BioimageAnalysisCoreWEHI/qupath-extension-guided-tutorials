package qupath.ext.tutorials.engine;

import java.util.List;
import java.util.function.BiFunction;

/**
 * One tutorial step: tell the user what to do, offer actions, point at controls, and check QuPath's
 * state to see whether it is done.
 *
 * @param title short heading
 * @param instruction what to do, in plain English
 * @param actions buttons offered on this step
 * @param highlights controls outlined while this step is active and not yet complete
 * @param check derives a {@link StepStatus} from {@code (now, atEntry)} — the current state and the
 *     state when the user arrived on this step, so a check can ask "did this change since?"
 * @param manual if {@code true}, the step never auto-advances — the user presses Next. Used where
 *     nothing observable changes (e.g. setting dialog fields); {@code check} then only gives feedback.
 * @param tips illustrated mouse/keyboard hints shown under the instruction
 * @param demo an animation played over the viewer until the step is complete
 */
public record Step(
        String title,
        String instruction,
        List<StepAction> actions,
        List<HighlightTarget> highlights,
        BiFunction<TutorialState, TutorialState, StepStatus> check,
        boolean manual,
        List<Tip> tips,
        Demo demo) {

    public enum Demo {
        NONE,
        /** A cursor pressing and dragging out a rectangle. */
        DRAW_RECTANGLE
    }

    public Step(
            String title,
            String instruction,
            List<StepAction> actions,
            List<HighlightTarget> highlights,
            BiFunction<TutorialState, TutorialState, StepStatus> check,
            boolean manual,
            List<Tip> tips) {
        this(title, instruction, actions, highlights, check, manual, tips, Demo.NONE);
    }

    public Step(
            String title,
            String instruction,
            List<StepAction> actions,
            List<HighlightTarget> highlights,
            BiFunction<TutorialState, TutorialState, StepStatus> check,
            boolean manual) {
        this(title, instruction, actions, highlights, check, manual, List.of(), Demo.NONE);
    }
}
