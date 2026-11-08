package qupath.ext.tutorials.support;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** {@link FieldFinder} and {@link MenuFinder} against real (unshown) controls. */
class FinderTest {

    @BeforeAll
    static void startFxToolkit() {
        boolean started;
        try {
            Platform.startup(() -> {});
            started = true;
        } catch (IllegalStateException alreadyStarted) {
            started = true;
        } catch (Throwable headlessFailure) {
            started = false;
        }
        Assumptions.assumeTrue(started, "JavaFX toolkit could not start (headless environment) — skipping");
    }

    @Test
    void fieldFinderReturnsTheRowControlOrTheCheckBoxItself() {
        ComboBox<String> compartment = new ComboBox<>();
        CheckBox singleThreshold = new CheckBox("Single threshold");
        GridPane grid = new GridPane();
        grid.add(new Label("Detection image"), 0, 0);
        grid.add(new ComboBox<String>(), 1, 0);
        grid.add(new Label("Score compartment:"), 0, 1);
        grid.add(compartment, 1, 1);
        grid.add(singleThreshold, 0, 2);
        var root = new VBox(grid);

        assertSame(compartment, FieldFinder.find(root, "score compartment").orElseThrow());
        assertSame(singleThreshold, FieldFinder.find(root, "Single threshold").orElseThrow());
        assertTrue(FieldFinder.find(root, "Threshold 2+").isEmpty());
    }

    @Test
    void menuFinderIgnoresEllipsisAndSkipsToggles() {
        MenuItem positive = new MenuItem("Positive cell detection...");
        Menu cellDetection = new Menu("Cell detection");
        cellDetection.getItems().addAll(new CheckMenuItem("Cell detection"), positive);
        MenuBar bar = new MenuBar(new Menu("Analyze", null, cellDetection));

        assertSame(positive, MenuFinder.find(bar, "Positive cell detection").orElseThrow());
        assertTrue(MenuFinder.find(bar, "Cell detection").isEmpty(), "toggles and submenus are never returned");
    }
}
