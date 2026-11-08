package qupath.ext.tutorials.engine;

/**
 * The result of checking one step against the current QuPath state.
 *
 * @param complete whether the step's goal is met
 * @param message short feedback shown under the instruction; may be empty
 * @param level how the message is styled
 */
public record StepStatus(boolean complete, String message, Level level) {

    public enum Level {
        INFO,
        OK,
        WARN
    }

    public static StepStatus waiting(String message) {
        return new StepStatus(false, message, Level.INFO);
    }

    public static StepStatus problem(String message) {
        return new StepStatus(false, message, Level.WARN);
    }

    public static StepStatus done(String message) {
        return new StepStatus(true, message, Level.OK);
    }

    /** Complete, but with something the user should notice. */
    public static StepStatus doneWithWarning(String message) {
        return new StepStatus(true, message, Level.WARN);
    }
}
