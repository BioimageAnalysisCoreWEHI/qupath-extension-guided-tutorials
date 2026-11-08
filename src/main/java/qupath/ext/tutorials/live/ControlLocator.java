package qupath.ext.tutorials.live;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.ext.tutorials.engine.HighlightTarget;
import qupath.ext.tutorials.support.FieldFinder;
import qupath.ext.tutorials.support.MenuFinder;
import qupath.lib.gui.QuPathGUI;

/** Resolves a {@link HighlightTarget} to the live control it names, if it is currently on screen. */
final class ControlLocator {

    private final QuPathGUI qupath;

    ControlLocator(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    Optional<Node> resolve(HighlightTarget target) {
        return switch (target.kind()) {
            case TOOLBAR -> findTooltipped(qupath.getToolBar(), target.text(), target.exclude());
            case ANALYSIS_TAB ->
                isTabSelected(target.text())
                        ? Optional.empty()
                        : findTabHeader(qupath.getAnalysisTabPane(), target.text());
            case ANALYSIS_ROW -> findRowInOpenTab(target.text());
            case WINDOW_CLOSE -> Optional.empty(); // not a node: see closeButton()
            case CHOICE -> Optional.empty(); // several staged pointers: see choice()
            case VIEWER -> Optional.ofNullable(qupath.getViewer()).map(v -> (Node) v.getView());
            case MENU_BAR -> findMenuHeader(target.text());
            case MENU_ITEM -> findOpenMenuItem(target.text());
            case DIALOG_BUTTON -> findDialog(target.window()).flatMap(root -> findButton(root, target.text()));
            case DIALOG_FIELD -> findDialog(target.window()).flatMap(root -> FieldFinder.find(root, target.text()));
        };
    }

    /** A control to outline, with its caption. */
    record Pointer(Node node, String caption) {}

    /**
     * Guides picking {@code option} from a drop-down in whatever dialog is open, one stage at a time:
     *
     * <ol>
     *   <li>the list is open and shows the option → outline the option ("Choose this");
     *   <li>the option is already chosen in a dialog → outline OK ("Press OK");
     *   <li>the option is a radio/toggle button not yet chosen → outline it;
     *   <li>otherwise a dialog has a drop-down → outline it ("Open this list").
     * </ol>
     *
     * Ignores QuPath's main window, the tutorial's own panel, and the tutorial's own highlight popups.
     */
    List<Pointer> choice(String option) {
        Predicate<Node> isOption = labelled(option).and(Node::isVisible);

        for (Window w : Window.getWindows()) {
            if (w instanceof PopupWindow && w.isShowing() && isForeignScene(w)) {
                Optional<Node> cell = find(w.getScene().getRoot(), isOption);
                if (cell.isPresent()) {
                    return List.of(new Pointer(ancestorWithStyle(cell.get(), "list-cell"), "3. Choose this"));
                }
            }
        }
        for (Stage dialog : dialogs()) {
            Parent root = dialog.getScene().getRoot();
            Optional<Node> shown = find(root, isOption);
            if (shown.isPresent()) {
                Node n = shown.get();
                if (n instanceof Toggle toggle && !toggle.isSelected()) {
                    return List.of(new Pointer(n, "3. Choose this"));
                }
                return findButton(root, "OK")
                        .map(ok -> List.of(new Pointer(ok, "4. Press OK")))
                        .orElse(List.of());
            }
            Optional<Node> list = find(root, node -> node instanceof ComboBoxBase<?> || node instanceof ChoiceBox<?>);
            if (list.isPresent()) {
                return List.of(new Pointer(list.get(), "3. Open this list"));
            }
        }
        return List.of();
    }

    /** Showing dialogs other than QuPath's main window and the tutorial's own panel. */
    private List<Stage> dialogs() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof Stage)
                .map(w -> (Stage) w)
                .filter(Stage::isShowing)
                .filter(s -> s != qupath.getStage())
                .filter(s -> s.getTitle() == null || !s.getTitle().startsWith(TutorialPanel.TITLE_PREFIX))
                .filter(s -> s.getScene() != null)
                .toList();
    }

    /** Not one of the tutorial's own highlight popups (whose content is always mouse-transparent). */
    private static boolean isForeignScene(Window w) {
        return w.getScene() != null && !w.getScene().getRoot().isMouseTransparent();
    }

    /** A window and an area of the screen within it. */
    record ScreenArea(Window window, Bounds bounds) {}

    private static final boolean MAC =
            System.getProperty("os.name", "").toLowerCase().contains("mac");

    /**
     * Estimated screen area of the title-bar close button of the dialog whose title contains {@code
     * titleFragment}. The button is drawn by the operating system, so it has no JavaFX node: its size is
     * a typical one, and its position comes from the gap between the window frame and its content
     * (title-bar height, side border). Top-right on Windows/Linux, top-left on macOS.
     */
    Optional<ScreenArea> closeButton(String titleFragment) {
        return findStage(titleFragment).map(stage -> {
            Scene scene = stage.getScene();
            double side = scene == null ? 0 : Math.max(0, scene.getX());
            double titleBar = scene == null ? 30 : Math.max(20, scene.getY() - (side > 0 ? 1 : 0));
            double width = MAC ? 20 : 46;
            double x = MAC ? stage.getX() + 6 : stage.getX() + stage.getWidth() - side - width;
            double y = stage.getY() + (MAC ? 4 : (side > 0 ? 1 : 0));
            double height = MAC ? Math.min(titleBar, 20) : titleBar;
            return new ScreenArea(stage, new BoundingBox(x, y, width, height));
        });
    }

    /** Selects the analysis-pane tab named {@code text}; returns whether one was found. */
    boolean selectTab(String text) {
        TabPane pane = qupath.getAnalysisTabPane();
        for (Tab tab : pane.getTabs()) {
            if (text.equalsIgnoreCase(tab.getText())) {
                pane.getSelectionModel().select(tab);
                return true;
            }
        }
        return false;
    }

    private static Optional<Node> findTooltipped(ToolBar bar, String fragment, String exclude) {
        String want = fragment.toLowerCase();
        String avoid = exclude == null ? null : exclude.toLowerCase();
        for (Node item : bar.getItems()) {
            Optional<Node> found = find(item, node -> {
                if (!(node instanceof Control control) || control.getTooltip() == null) {
                    return false;
                }
                Tooltip tip = control.getTooltip();
                String text = tip.getText() == null ? "" : tip.getText().toLowerCase();
                return text.contains(want) && (avoid == null || !text.contains(avoid));
            });
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private boolean isTabSelected(String text) {
        Tab selected = qupath.getAnalysisTabPane().getSelectionModel().getSelectedItem();
        return selected != null && text.equalsIgnoreCase(selected.getText());
    }

    /**
     * Searches only the open tab's content (other tabs' content is hidden but still laid out), for a
     * cell showing {@code text}, and outlines its whole table row.
     */
    private Optional<Node> findRowInOpenTab(String text) {
        Tab selected = qupath.getAnalysisTabPane().getSelectionModel().getSelectedItem();
        if (selected == null || selected.getContent() == null) {
            return Optional.empty();
        }
        return find(selected.getContent(), labelled(text).and(Node::isVisible))
                .map(cell -> ancestorWithStyle(cell, "table-row-cell"));
    }

    private static Node ancestorWithStyle(Node node, String styleClass) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n.getStyleClass().contains(styleClass)) {
                return n;
            }
        }
        return node;
    }

    /** Tab headers are skin nodes styled {@code .tab}; find the one whose label shows {@code text}. */
    private static Optional<Node> findTabHeader(TabPane pane, String text) {
        for (Node header : pane.lookupAll(".tab")) {
            if (find(header, labelled(text)).isPresent()) {
                return Optional.of(header);
            }
        }
        return Optional.empty();
    }

    /** Menu-bar headers are skin buttons styled {@code .menu}. */
    private Optional<Node> findMenuHeader(String text) {
        for (Node header : qupath.getMenuBar().lookupAll(".menu")) {
            if (labelled(text).test(header) || find(header, labelled(text)).isPresent()) {
                return Optional.of(header);
            }
        }
        return Optional.empty();
    }

    /** Items of an open drop-down live in a showing {@link ContextMenu} window; outline the whole row. */
    private static Optional<Node> findOpenMenuItem(String text) {
        for (Window w : Window.getWindows()) {
            if (w instanceof ContextMenu menu && menu.isShowing() && menu.getScene() != null) {
                Optional<Node> label = find(menu.getScene().getRoot(), labelled(text));
                if (label.isPresent()) {
                    return Optional.of(ancestorWithStyle(label.get(), "menu-item"));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Parent> findDialog(String titleFragment) {
        return findStage(titleFragment)
                .map(s -> s.getScene() == null ? null : s.getScene().getRoot());
    }

    private Optional<Stage> findStage(String titleFragment) {
        String needle = titleFragment.toLowerCase();
        return Window.getWindows().stream()
                .filter(w -> w instanceof Stage)
                .map(w -> (Stage) w)
                .filter(Stage::isShowing)
                .filter(s -> s != qupath.getStage())
                .filter(s -> s.getTitle() != null && s.getTitle().toLowerCase().contains(needle))
                .findFirst();
    }

    private static Optional<Node> findButton(Parent root, String text) {
        return find(root, node -> node instanceof ButtonBase b && text.equalsIgnoreCase(b.getText()));
    }

    /** Matches a {@link Labeled} by text, ignoring case and any trailing ellipsis. */
    private static Predicate<Node> labelled(String text) {
        String want = MenuFinder.normalise(text);
        return node -> node instanceof Labeled l && want.equals(MenuFinder.normalise(l.getText()));
    }

    /** Depth-first search of {@code node} and its descendants. */
    private static Optional<Node> find(Node node, Predicate<Node> match) {
        if (match.test(node)) {
            return Optional.of(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Optional<Node> found = find(child, match);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
