package qupath.ext.tutorials.live;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.tutorials.engine.HighlightTarget;
import qupath.ext.tutorials.engine.IdleTimer;
import qupath.ext.tutorials.engine.Step;
import qupath.ext.tutorials.engine.StepAction;
import qupath.ext.tutorials.engine.StepStatus;
import qupath.ext.tutorials.engine.Tip;
import qupath.ext.tutorials.engine.Tutorial;
import qupath.ext.tutorials.engine.TutorialProgress;
import qupath.ext.tutorials.engine.TutorialState;
import qupath.ext.tutorials.support.MenuFinder;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Runs one tutorial: shows the panel, polls QuPath state twice a second, feeds each step's check to
 * {@link TutorialProgress}, and keeps highlights in sync. One tutorial at a time. FX thread only.
 *
 * <p>Polling rather than per-event listeners keeps step logic stateless and leak-free; it is cheap
 * because {@link StateReader} recomputes hierarchy numbers only after a hierarchy event.
 */
public final class TutorialRunner {

    private static final Logger logger = LoggerFactory.getLogger(TutorialRunner.class);
    private static final Duration POLL = Duration.millis(500);

    /** Margin around the region when zooming to it. */
    private static final double ZOOM_MARGIN = 1.15;

    /** The practice area fills ~40% of the view, leaving surrounding tissue for context. */
    private static final double GOOD_AREA_MARGIN = 2.5;

    private static TutorialRunner active;

    private final QuPathGUI qupath;
    private final Tutorial tutorial;
    private final TutorialProgress progress;
    private final StateReader reader;
    private final ControlLocator locator;
    private final Highlighter highlighter = new Highlighter();
    private final DemoAnimator demo;
    private final SlideTargetFinder targetFinder;
    private final TutorialPanel panel;
    private final Timeline timeline;

    private boolean ownSlide;
    private boolean stopped;
    private boolean finished;
    private TutorialState atEntry;
    private final IdleTimer idle = new IdleTimer();

    private TutorialRunner(QuPathGUI qupath, Tutorial tutorial) {
        this.qupath = qupath;
        this.tutorial = tutorial;
        this.progress = new TutorialProgress(tutorial.steps().size());
        this.reader = new StateReader(qupath);
        this.locator = new ControlLocator(qupath);
        this.demo = new DemoAnimator(() -> qupath.getToolManager().getSelectedTool() != PathTools.MOVE);
        this.targetFinder = new SlideTargetFinder();
        this.panel = new TutorialPanel(qupath.getStage(), tutorial.title());
        this.timeline = new Timeline(new KeyFrame(POLL, e -> poll()));
        timeline.setCycleCount(Animation.INDEFINITE);

        panel.back.setOnAction(e -> {
            progress.back();
            enterStep(null);
        });
        panel.skip.setOnAction(e -> {
            progress.next();
            enterStep(null);
        });
        panel.next.setOnAction(e -> {
            if (finished) {
                stop();
            } else {
                progress.next();
                enterStep(null);
            }
        });
        // On the completion screen the Exit button reads "Start again".
        panel.exit.setOnAction(e -> {
            if (finished) {
                start(qupath, tutorial);
            } else {
                stop();
            }
        });
        panel.stage.setOnHidden(e -> stop());
    }

    /** Starts {@code tutorial}, replacing any tutorial already running. */
    public static void start(QuPathGUI qupath, Tutorial tutorial) {
        if (active != null) {
            active.stop();
        }
        active = new TutorialRunner(qupath, tutorial);
        active.begin();
    }

    private void begin() {
        panel.placeBeside(qupath.getStage());
        panel.stage.show();
        enterStep(null);
        timeline.play();
    }

    private void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        timeline.stop();
        highlighter.clear();
        demo.hide();
        targetFinder.shutdown();
        reader.close();
        panel.stage.close();
        if (active == this) {
            active = null;
        }
    }

    /**
     * Stops polling and pointing, and shows the completion screen: the last step's result, the
     * tutorial's recap, and the last step's own text (where to find the numbers again). Reached
     * automatically on arriving at the last step, so the end is unmistakable rather than one more step
     * that waits for a Finish click.
     */
    private void finish(Step lastStep, StepStatus result) {
        finished = true;
        timeline.stop();
        highlighter.clear();
        demo.hide();
        panel.setNudge(null);
        panel.showFinished(tutorial.title(), tutorial.completion() + "\n\n" + lastStep.instruction(), result);
    }

    /** Points at the step's first "click here" outline, else its first button, else offers Skip. */
    private static String nudgeFor(Step step) {
        for (HighlightTarget target : step.highlights()) {
            if (!target.caution()
                    && target.caption() != null
                    && !target.caption().isBlank()) {
                return "Still there? Look for the pulsing orange outline: \"" + target.caption() + "\".";
            }
        }
        if (!step.actions().isEmpty()) {
            return "Still there? Try the button above: \""
                    + step.actions().get(0).label() + "\".";
        }
        return "Take your time. If you're stuck, read the step again or press Skip.";
    }

    private Step current() {
        return tutorial.steps().get(progress.index());
    }

    private void enterStep(String lastDoneMessage) {
        highlighter.clear();
        demo.hide();
        atEntry = reader.read(ownSlide);
        idle.reset(atEntry, System.currentTimeMillis());
        panel.setNudge(null);
        panel.showLastDone(lastDoneMessage);
        panel.showStep(
                tutorial.title(), progress.index(), progress.stepCount(), current(), this::resolveKeys, this::handle);
        poll();
    }

    /** A tip's literal keys plus the live shortcuts of its commands, as QuPath's menus define them. */
    private List<String> resolveKeys(Tip tip) {
        List<String> keys = new ArrayList<>(tip.keys());
        for (String command : tip.keysFromCommands()) {
            MenuFinder.find(qupath.getMenuBar(), command)
                    .map(MenuItem::getAccelerator)
                    .map(KeyCombination::getDisplayText)
                    .filter(text -> !text.isBlank())
                    .ifPresent(keys::add);
        }
        return keys;
    }

    private void poll() {
        if (stopped) {
            return;
        }
        Step step = current();
        TutorialState state = reader.read(ownSlide);
        StepStatus status = step.check().apply(state, atEntry);
        panel.showStatus(status);
        if (progress.isLast()) {
            finish(step, status);
            return;
        }

        TutorialProgress.Decision decision = progress.onPoll(status, step.manual());
        if (decision == TutorialProgress.Decision.ADVANCE) {
            panel.flashComplete();
            enterStep(status.message());
            return;
        }
        if (decision == TutorialProgress.Decision.SKIP) {
            enterStep("Already done, skipped \"" + step.title() + "\": " + status.message());
            return;
        }
        boolean showNext = step.manual() || decision == TutorialProgress.Decision.ALREADY_DONE;
        panel.setNext(showNext, progress.index() == progress.stepCount() - 2 ? "Finish" : "Next");

        // Nothing left to point at once the step is satisfied.
        List<Highlighter.Target> targets = new ArrayList<>();
        if (!status.complete() || step.manual()) {
            for (HighlightTarget target : step.highlights()) {
                if (target.kind() == HighlightTarget.Kind.CHOICE) {
                    for (ControlLocator.Pointer p : locator.choice(target.text())) {
                        targets.add(new Highlighter.Target(p.node(), p.caption(), false));
                    }
                    continue;
                }
                if (target.kind() == HighlightTarget.Kind.WINDOW_CLOSE) {
                    locator.closeButton(target.window())
                            .ifPresent(area -> targets.add(
                                    Highlighter.Target.onScreen(area.window(), area.bounds(), target.caption())));
                    continue;
                }
                locator.resolve(target)
                        .ifPresent(node -> targets.add(new Highlighter.Target(
                                node,
                                target.caption(),
                                target.caution(),
                                target.kind() == HighlightTarget.Kind.VIEWER,
                                target.kind() == HighlightTarget.Kind.ANALYSIS_ROW)));
            }
        }
        highlighter.sync(targets);

        // Stuck? Nothing changed in QuPath for a while on a step that waits for the user to act.
        // Not while QuPath itself is busy detecting: the user has done their part.
        boolean stuck = !step.manual()
                && !status.complete()
                && !state.detectionRunning()
                && idle.onPoll(state, System.currentTimeMillis());
        highlighter.setAttention(stuck);
        panel.setNudge(stuck ? nudgeFor(step) : null);

        targetFinder.update(qupath.getImageData());
        if (step.demo() == Step.Demo.DRAW_RECTANGLE && !status.complete() && qupath.getViewer() != null) {
            demo.show(qupath.getViewer(), targetFinder.current());
        } else {
            demo.hide();
        }
    }

    private void handle(StepAction action) {
        switch (action.kind()) {
            case OPEN_URL -> {
                if (!QuPathGUI.openInBrowser(action.argument())) {
                    panel.showNote("Couldn't open a browser. The link is: " + action.argument());
                }
            }
            case OPEN_FILE -> openFile();
            case OWN_SLIDE -> {
                ownSlide = true;
                panel.showNote("");
            }
            case OPEN_COMMAND ->
                MenuFinder.find(qupath.getMenuBar(), action.argument())
                        .ifPresentOrElse(
                                item -> Platform.runLater(item::fire),
                                () -> panel.showNote("Couldn't find \"" + action.argument()
                                        + "\" in QuPath's menus. Open it from the menu yourself."));
            case SELECT_TAB -> {
                if (!locator.selectTab(action.argument())) {
                    panel.showNote("Couldn't find the " + action.argument() + " tab.");
                }
            }
            case ZOOM_TO_REGION -> {
                ROI roi = reader.trackedRoi();
                if (roi == null) {
                    panel.showNote("No region to zoom to.");
                } else {
                    zoomTo(
                            roi.getBoundsX(),
                            roi.getBoundsY(),
                            roi.getBoundsWidth(),
                            roi.getBoundsHeight(),
                            ZOOM_MARGIN);
                }
            }
            case GO_TO_GOOD_AREA -> {
                SlideTargetFinder.Target target = targetFinder.current();
                if (target == null) {
                    panel.showNote("Still looking for a good area on this slide… try again in a moment.");
                } else {
                    panel.showNote("");
                    zoomTo(target.x(), target.y(), target.width(), target.height(), GOOD_AREA_MARGIN);
                }
            }
        }
        poll();
    }

    /** Navigation only: centres the viewer on a region and zooms so it fills {@code 1/margin} of the view. */
    private void zoomTo(double x, double y, double width, double height, double margin) {
        QuPathViewer viewer = qupath.getViewer();
        if (viewer == null) {
            return;
        }
        double viewWidth = Math.max(1, viewer.getView().getWidth());
        double viewHeight = Math.max(1, viewer.getView().getHeight());
        double downsample = margin * Math.max(width / viewWidth, height / viewHeight);
        downsample = Math.max(viewer.getMinDownsample(), Math.min(viewer.getMaxDownsample(), downsample));
        viewer.setDownsampleFactor(downsample);
        viewer.setCenterPixelLocation(x + width / 2, y + height / 2);
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open OS-2.ndpi");
        File file = chooser.showOpenDialog(panel.stage);
        if (file == null) {
            return;
        }
        try {
            if (!qupath.openImage(qupath.getViewer(), file.getAbsolutePath())) {
                panel.showNote("QuPath didn't open " + file.getName() + ".");
            }
        } catch (IOException e) {
            logger.warn("Could not open {}", file, e);
            panel.showNote("Couldn't open " + file.getName() + ": " + e.getMessage());
        }
    }
}
