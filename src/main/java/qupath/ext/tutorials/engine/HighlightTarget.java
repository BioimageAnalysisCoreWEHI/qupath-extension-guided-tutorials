package qupath.ext.tutorials.engine;

/**
 * A control to outline on screen while a step is active. Re-resolved on every poll, so a highlight
 * appears as soon as its target exists (a dialog opens, a menu drops down) and follows it if the window
 * moves.
 *
 * @param kind where to look
 * @param window for dialog targets, a fragment of the dialog's title; otherwise unused
 * @param text what to match: tooltip fragment, tab text, menu label, button text, or field label
 * @param exclude for toolbar targets, a tooltip fragment that must <em>not</em> appear; may be {@code null}
 * @param caption short instruction drawn next to the highlight; may be {@code null}
 * @param caution draw as a "don't click this yet" warning rather than "click here"
 */
public record HighlightTarget(Kind kind, String window, String text, String exclude, String caption, boolean caution) {

    public HighlightTarget(Kind kind, String window, String text, String exclude, String caption) {
        this(kind, window, text, exclude, caption, false);
    }

    public enum Kind {
        /** A main-toolbar control whose tooltip contains {@code text}, case-insensitively. */
        TOOLBAR,
        /** The analysis-pane tab header whose text equals {@code text}; hidden once that tab is open. */
        ANALYSIS_TAB,
        /**
         * A row in the currently open analysis-pane tab (e.g. a table row) containing a cell whose text
         * equals {@code text}.
         */
        ANALYSIS_ROW,
        /** The image viewer itself, e.g. "scroll here". */
        VIEWER,
        /**
         * Guides a pick from a drop-down in whatever dialog is open (e.g. QuPath's image-type chooser),
         * one stage at a time: outline the list while the option {@code text} isn't visible; outline the
         * option once the list is open; outline OK once it's selected.
         */
        CHOICE,
        /**
         * The title-bar close button of the {@code window} dialog. It belongs to the operating system,
         * not JavaFX, so its position is estimated from the window frame.
         */
        WINDOW_CLOSE,
        /** A top-level menu in the main menu bar, e.g. "Analyze". */
        MENU_BAR,
        /** An item in a currently open drop-down menu; only visible once its parent menu is open. */
        MENU_ITEM,
        /** A button in the {@code window} dialog whose text equals {@code text}. */
        DIALOG_BUTTON,
        /** The input control for the field labelled {@code text} in the {@code window} dialog. */
        DIALOG_FIELD
    }

    public static HighlightTarget toolbar(String tooltipFragment, String caption) {
        return new HighlightTarget(Kind.TOOLBAR, null, tooltipFragment, null, caption);
    }

    public static HighlightTarget toolbar(String tooltipFragment, String excludeFragment, String caption) {
        return new HighlightTarget(Kind.TOOLBAR, null, tooltipFragment, excludeFragment, caption);
    }

    public static HighlightTarget analysisTab(String tab, String caption) {
        return new HighlightTarget(Kind.ANALYSIS_TAB, null, tab, null, caption);
    }

    public static HighlightTarget analysisRow(String text, String caption) {
        return new HighlightTarget(Kind.ANALYSIS_ROW, null, text, null, caption);
    }

    public static HighlightTarget choice(String option) {
        return new HighlightTarget(Kind.CHOICE, null, option, null, null);
    }

    public static HighlightTarget windowClose(String window, String caption) {
        return new HighlightTarget(Kind.WINDOW_CLOSE, window, null, null, caption);
    }

    public static HighlightTarget viewer(String caption) {
        return new HighlightTarget(Kind.VIEWER, null, null, null, caption);
    }

    public static HighlightTarget menuBar(String menu, String caption) {
        return new HighlightTarget(Kind.MENU_BAR, null, menu, null, caption);
    }

    public static HighlightTarget menuItem(String item, String caption) {
        return new HighlightTarget(Kind.MENU_ITEM, null, item, null, caption);
    }

    public static HighlightTarget dialogButton(String window, String button, String caption) {
        return new HighlightTarget(Kind.DIALOG_BUTTON, window, button, null, caption);
    }

    /** A button the user must <em>not</em> press yet, e.g. Run while settings are still being chosen. */
    public static HighlightTarget dialogButtonNotYet(String window, String button, String caption) {
        return new HighlightTarget(Kind.DIALOG_BUTTON, window, button, null, caption, true);
    }

    public static HighlightTarget dialogField(String window, String label, String caption) {
        return new HighlightTarget(Kind.DIALOG_FIELD, window, label, null, caption);
    }
}
