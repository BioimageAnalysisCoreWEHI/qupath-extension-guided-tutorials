package qupath.ext.tutorials;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import qupath.ext.tutorials.live.TutorialRunner;
import qupath.ext.tutorials.tutorials.CellDetectionTutorial;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Guided walkthroughs of QuPath's own documentation tutorials, inside QuPath. Adds {@code Extensions ▸
 * Guided tutorials}. No AI, no network requests, and nothing here changes image data — the user does
 * every click; the extension explains, points, and checks.
 */
public class GuidedTutorialsExtension implements QuPathExtension {

    private static final String NAME = "Guided tutorials";
    private static final Version QUPATH_VERSION = Version.parse("v0.7.0");

    private boolean isInstalled;

    @Override
    public void installExtension(QuPathGUI qupath) {
        if (isInstalled) {
            return;
        }
        isInstalled = true;

        Menu menu = qupath.getMenu("Extensions>" + NAME, true);
        MenuItem cellDetection = new MenuItem("Cell detection (Ki67)");
        cellDetection.setOnAction(e -> TutorialRunner.start(qupath, CellDetectionTutorial.create()));
        menu.getItems().add(cellDetection);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Step-by-step guided walkthroughs of QuPath's own tutorials";
    }

    @Override
    public Version getQuPathVersion() {
        return QUPATH_VERSION;
    }

    @Override
    public Version getVersion() {
        var v = getClass().getPackage().getImplementationVersion();
        return (v == null) ? QuPathExtension.super.getVersion() : Version.parse(v);
    }
}
