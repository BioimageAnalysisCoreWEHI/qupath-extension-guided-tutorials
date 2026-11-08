package qupath.ext.tutorials.support;

import java.util.List;
import java.util.Optional;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;

/**
 * Pure walk of a {@link MenuBar} for a plain command by its label, at any depth. A trailing
 * {@code "..."} or {@code "…"} is ignored on both sides, so a tutorial can say "Positive cell
 * detection" without knowing how QuPath decorates the item. Toggles, radio items, submenus and
 * separators are never returned.
 */
public final class MenuFinder {

    private MenuFinder() {}

    public static Optional<MenuItem> find(MenuBar menuBar, String label) {
        String wanted = normalise(label);
        for (Menu menu : menuBar.getMenus()) {
            Optional<MenuItem> found = find(menu.getItems(), wanted);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<MenuItem> find(List<MenuItem> items, String wanted) {
        for (MenuItem item : items) {
            if (item instanceof Menu submenu) {
                Optional<MenuItem> found = find(submenu.getItems(), wanted);
                if (found.isPresent()) {
                    return found;
                }
            } else if (!(item instanceof CheckMenuItem
                            || item instanceof RadioMenuItem
                            || item instanceof SeparatorMenuItem)
                    && wanted.equals(normalise(item.getText()))) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /** Trimmed, lower-cased, with any trailing {@code "..."} / {@code "…"} removed. */
    public static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.endsWith("...")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        } else if (trimmed.endsWith("…")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.trim().toLowerCase();
    }
}
