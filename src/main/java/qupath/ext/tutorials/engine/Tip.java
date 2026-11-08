package qupath.ext.tutorials.engine;

import java.util.List;

/**
 * A small illustrated hint shown under a step's instruction: an icon, key chips, and a few words.
 *
 * @param icon the drawing to show
 * @param keys literal key labels, e.g. {@code "R"}
 * @param keysFromCommands menu commands whose keyboard shortcut is read from QuPath's menus at runtime
 *     and shown as extra key chips, e.g. {@code "Zoom in"} — so the platform's real shortcut is shown,
 *     never a guess; commands without a shortcut are silently omitted
 * @param text what the hint means
 */
public record Tip(Icon icon, List<String> keys, List<String> keysFromCommands, String text) {

    public enum Icon {
        MOUSE_SCROLL,
        MOUSE_DRAG,
        KEYBOARD,
        /** Not a how-to: a prominent reassurance, e.g. "nothing is lost when you close this". */
        REASSURE
    }

    public static Tip reassure(String text) {
        return new Tip(Icon.REASSURE, List.of(), List.of(), text);
    }

    public static Tip scroll(String text) {
        return new Tip(Icon.MOUSE_SCROLL, List.of(), List.of(), text);
    }

    public static Tip drag(String text) {
        return new Tip(Icon.MOUSE_DRAG, List.of(), List.of(), text);
    }

    public static Tip keys(String text, String... keys) {
        return new Tip(Icon.KEYBOARD, List.of(keys), List.of(), text);
    }

    public static Tip shortcutsOf(String text, String... commands) {
        return new Tip(Icon.KEYBOARD, List.of(), List.of(commands), text);
    }
}
