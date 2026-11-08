package qupath.ext.tutorials.live;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import qupath.ext.tutorials.engine.Step;
import qupath.ext.tutorials.engine.StepAction;
import qupath.ext.tutorials.engine.StepStatus;
import qupath.ext.tutorials.engine.Tip;

/**
 * The floating tutorial window: step text, illustrated tips, action buttons, live status, and
 * navigation. View only.
 *
 * <p>The window's border shows the step's state at a glance: blue-grey while waiting for the user,
 * green when the step is complete (and briefly when moving on), amber when something needs fixing.
 */
final class TutorialPanel {

    private static final double WIDTH = 400;
    private static final Duration FLASH = Duration.seconds(1.5);

    private static final String WAITING = "#607d8b";
    private static final String GREEN = "#2e7d32";
    private static final String AMBER = "#b26a00";

    /** Same orange as the on-screen outlines: "this is what to do". */
    private static final String ACTION = "#ff5a1f";

    private static final String TITLE_STYLE = "-fx-font-size: 16px; -fx-font-weight: bold;";

    /** The panel's window title prefix, so other code can tell the tutorial's own window apart. */
    static final String TITLE_PREFIX = "Tutorial: ";

    final Stage stage = new Stage(StageStyle.UTILITY);
    final Button back = new Button("Back");
    final Button skip = new Button("Skip");
    final Button next = new Button("Next");
    final Button exit = new Button("Exit");

    private final VBox root;
    private final Label header = new Label();
    private final Label lastDone = new Label();
    private final Label title = new Label();
    private final VBox instruction = new VBox(8);
    private final VBox tips = new VBox(6);
    private final FlowPane actions = new FlowPane(6, 6);
    private final Label status = new Label();
    private final Label note = new Label();
    private final Label nudge = new Label();
    private final Timeline nudgePulse = new Timeline();
    private boolean nudgeOn;
    private boolean nudgeBlink;
    private final PauseTransition flash = new PauseTransition(FLASH);

    private String borderColor = WAITING;
    private boolean flashing;

    TutorialPanel(Window owner, String tutorialTitle) {
        stage.initOwner(owner);
        stage.setTitle(TITLE_PREFIX + tutorialTitle);

        header.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
        lastDone.setWrapText(true);
        lastDone.setMaxWidth(Double.MAX_VALUE);
        lastDone.setStyle(banner(GREEN, "#e8f5e9", "#1b5e20", 13, true));
        title.setStyle(TITLE_STYLE);
        title.setWrapText(true);
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setMinHeight(Region.USE_PREF_SIZE);
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: " + AMBER + ";");
        nudge.setWrapText(true);
        nudge.setMaxWidth(Double.MAX_VALUE);
        nudge.setStyle(banner(AMBER, "#fff8e1", "#7a4100", 13, true));
        show(nudge, false);
        nudgePulse.getKeyFrames().add(new KeyFrame(Duration.millis(600), e -> {
            nudgeBlink = !nudgeBlink;
            applyBorder();
        }));
        nudgePulse.setCycleCount(Animation.INDEFINITE);

        next.setDefaultButton(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox nav = new HBox(6, back, skip, spacer, exit, next);
        nav.setAlignment(Pos.CENTER_LEFT);

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        root = new VBox(10, header, lastDone, title, instruction, tips, actions, status, nudge, note, grow, nav);
        root.setPadding(new Insets(14));
        root.setPrefWidth(WIDTH);
        root.setMinHeight(420);
        applyBorder();

        flash.setOnFinished(e -> {
            flashing = false;
            applyBorder();
        });

        stage.setScene(new Scene(root));
        stage.setResizable(true);
    }

    void placeBeside(Stage main) {
        stage.setX(Math.max(0, main.getX() + main.getWidth() - WIDTH - 40));
        stage.setY(main.getY() + 120);
    }

    void showStep(
            String tutorialTitle,
            int index,
            int count,
            Step step,
            Function<Tip, List<String>> keyResolver,
            Consumer<StepAction> onAction) {
        header.setText(tutorialTitle + "  ·  Step " + (index + 1) + " of " + count
                + (index == count - 1 ? "  ·  last step" : ""));
        title.setText(step.title());
        title.setStyle(TITLE_STYLE);
        exit.setText("Exit");
        back.setVisible(true);
        setInstruction(step.instruction());

        tips.getChildren().clear();
        for (Tip tip : step.tips()) {
            List<String> keys = keyResolver.apply(tip);
            if (tip.icon() == Tip.Icon.KEYBOARD && keys.isEmpty()) {
                continue; // a shortcut tip whose shortcut QuPath doesn't define
            }
            tips.getChildren().add(TipGraphics.row(tip, keys));
        }
        show(tips, !tips.getChildren().isEmpty());

        actions.getChildren().clear();
        for (StepAction action : step.actions()) {
            Button b = new Button(action.label());
            b.setOnAction(e -> onAction.accept(action));
            actions.getChildren().add(b);
        }
        show(actions, !step.actions().isEmpty());

        status.setText("");
        note.setText("");
        back.setDisable(index == 0);
        skip.setVisible(index < count - 1);
        stage.sizeToScene();
    }

    void showStatus(StepStatus s) {
        boolean empty = s.message() == null || s.message().isBlank();
        status.setText(
                empty
                        ? ""
                        : switch (s.level()) {
                            case OK -> "✓ " + s.message();
                            case WARN -> "⚠ " + s.message();
                            case INFO -> s.message();
                        });
        // Short confirmations read as a headline; long ones (the result summary) stay regular weight.
        boolean bold = !empty && s.message().length() < 120;
        status.setStyle(
                empty
                        ? ""
                        : switch (s.level()) {
                            case OK -> banner(GREEN, "#e8f5e9", "#1b5e20", 15, bold);
                            case WARN -> banner(AMBER, "#fff3e0", "#7a4100", 14, false);
                            case INFO -> "-fx-opacity: 0.8;";
                        });
        borderColor = s.level() == StepStatus.Level.WARN ? AMBER : s.complete() ? GREEN : WAITING;
        applyBorder();
    }

    /**
     * Replaces the step view with an unmistakable "Tutorial complete" screen: big green title, what the
     * user did, and their result. {@link #next} becomes Close and {@link #exit} becomes Start again.
     */
    void showFinished(String tutorialTitle, String completion, StepStatus result) {
        header.setText(tutorialTitle + "  ·  Finished");
        show(lastDone, false);
        title.setText("🎉  Tutorial complete!");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + GREEN + ";");
        setInstruction(completion);
        tips.getChildren().clear();
        show(tips, false);
        actions.getChildren().clear();
        show(actions, false);
        boolean hasResult =
                result != null && result.message() != null && !result.message().isBlank();
        status.setText(hasResult ? result.message() : "");
        // A result that isn't there (e.g. the user skipped detection) still shows, in amber, saying why.
        status.setStyle(
                !hasResult
                        ? ""
                        : result.complete()
                                ? banner(GREEN, "#e8f5e9", "#1b5e20", 15, false)
                                : banner(AMBER, "#fff3e0", "#7a4100", 14, false));
        note.setText("");
        back.setVisible(false);
        skip.setVisible(false);
        exit.setText("Start again");
        setNext(true, "Close");
        borderColor = GREEN;
        flashComplete();
        stage.sizeToScene();
    }

    /**
     * Renders instruction text paragraph by paragraph (blank-line separated). A paragraph starting with
     * {@code →} is what the user must do to move on, and is drawn as a bold callout in the outline
     * colour so it can't be missed among the explanation.
     */
    private void setInstruction(String text) {
        instruction.getChildren().clear();
        for (String paragraph : text.split("\n\\s*\n")) {
            String trimmed = paragraph.strip();
            Label label;
            if (trimmed.startsWith("→")) {
                label = new Label("➜  " + trimmed.substring(1).strip());
                label.setStyle(banner(ACTION, "#fff1eb", "#8a2d00", 14, true));
            } else {
                label = new Label(paragraph.stripTrailing());
            }
            label.setWrapText(true);
            label.setMaxWidth(Double.MAX_VALUE);
            label.setMinHeight(Region.USE_PREF_SIZE);
            instruction.getChildren().add(label);
        }
    }

    void showLastDone(String message) {
        boolean has = message != null && !message.isBlank();
        lastDone.setText(has ? "✓ " + message : "");
        show(lastDone, has);
    }

    /** Briefly turns the window border green, e.g. when a step completes and the tutorial moves on. */
    void flashComplete() {
        flashing = true;
        applyBorder();
        flash.playFromStart();
    }

    /** Feedback from an action button, e.g. a command that could not be found. Cleared on step change. */
    void showNote(String message) {
        note.setText(message);
    }

    void setNext(boolean visible, String text) {
        next.setVisible(visible);
        next.setText(text);
    }

    /**
     * Shows a gentle "still there?" hint and pulses the window border amber, or clears both when
     * {@code message} is {@code null}. Never brings the window to the front: that would take keyboard
     * focus from QuPath, and some steps need QuPath to receive key presses.
     */
    void setNudge(String message) {
        boolean on = message != null;
        if (on) {
            nudge.setText("👋 " + message);
        }
        if (on == nudgeOn) {
            return;
        }
        nudgeOn = on;
        show(nudge, on);
        if (on) {
            nudgePulse.playFromStart();
        } else {
            nudgePulse.stop();
            nudgeBlink = false;
            applyBorder();
        }
        stage.sizeToScene();
    }

    private void applyBorder() {
        String color = flashing ? GREEN : (nudgeOn && nudgeBlink) ? AMBER : borderColor;
        root.setStyle("-fx-border-color: " + color + "; -fx-border-width: 5;");
    }

    private static void show(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }

    private static String banner(String edge, String background, String text, int size, boolean bold) {
        return "-fx-background-color: " + background + "; -fx-border-color: " + edge + "; "
                + "-fx-border-width: 0 0 0 5; -fx-text-fill: " + text + "; -fx-font-size: " + size + "px; "
                + (bold ? "-fx-font-weight: bold; " : "") + "-fx-padding: 8 10; -fx-background-radius: 3;";
    }
}
