package qupath.ext.tutorials.engine;

/**
 * A button the tutorial panel offers on a step. Every action navigates or points — none changes the
 * user's image data.
 *
 * @param label the button text
 * @param kind what the button does
 * @param argument a URL for {@link Kind#OPEN_URL}, a command name for {@link Kind#OPEN_COMMAND},
 *     otherwise unused
 */
public record StepAction(String label, Kind kind, String argument) {

    public enum Kind {
        /** Open {@code argument} in the system browser. QuPath itself makes no network request. */
        OPEN_URL,
        /** Pick an image file and open it in the viewer. */
        OPEN_FILE,
        /** Continue the tutorial on whatever image the user has open. */
        OWN_SLIDE,
        /** Fire the menu command whose label is {@code argument} (trailing "..." ignored). */
        OPEN_COMMAND,
        /** Select the analysis-pane tab named {@code argument}. */
        SELECT_TAB,
        /** Centre the viewer on the tracked annotation and zoom so it fills the view. */
        ZOOM_TO_REGION,
        /** Centre the viewer on a good practice area of the slide (preset or found by scanning). */
        GO_TO_GOOD_AREA
    }

    public static StepAction goToGoodArea(String label) {
        return new StepAction(label, Kind.GO_TO_GOOD_AREA, null);
    }

    public static StepAction zoomToRegion(String label) {
        return new StepAction(label, Kind.ZOOM_TO_REGION, null);
    }

    public static StepAction openUrl(String label, String url) {
        return new StepAction(label, Kind.OPEN_URL, url);
    }

    public static StepAction openFile(String label) {
        return new StepAction(label, Kind.OPEN_FILE, null);
    }

    public static StepAction ownSlide(String label) {
        return new StepAction(label, Kind.OWN_SLIDE, null);
    }

    public static StepAction openCommand(String label, String command) {
        return new StepAction(label, Kind.OPEN_COMMAND, command);
    }

    public static StepAction selectTab(String label, String tab) {
        return new StepAction(label, Kind.SELECT_TAB, tab);
    }
}
