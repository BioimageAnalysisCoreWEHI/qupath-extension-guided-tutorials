package qupath.ext.tutorials.live;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Persistent outlines over live controls, kept in sync with the current step: {@link #sync} is called
 * on every poll with the nodes that should be outlined, and creates, moves or removes popups to match.
 * Purely visual and mouse-transparent — never clicks anything. FX thread only.
 *
 * <p>Adapted from qupath-claude-mcp's {@code UiHighlighter}, which shows one outline for a few seconds;
 * a tutorial step needs several, for as long as the step lasts, following their windows if moved.
 */
final class Highlighter {

    /**
     * @param caution outline as "don't click this yet" (blue-grey, dashed) rather than "click here"
     * @param inside draw inside {@code node} (which must be a {@link Pane}) as a mouse-transparent layer,
     *     rather than in a popup window on top of it. Required for large targets the user interacts
     *     with, like the image viewer: a popup covering it swallows scroll-wheel events (zoom), even
     *     where it is transparent.
     * @param captionBelow put the caption under the outline instead of beside it — for targets with
     *     content to their right that the caption would cover, like a table row
     */
    record Target(
            Node node,
            String caption,
            boolean caution,
            boolean inside,
            Window window,
            Bounds screen,
            boolean captionBelow) {
        Target(Node node, String caption, boolean caution) {
            this(node, caption, caution, false, null, null, false);
        }

        Target(Node node, String caption, boolean caution, boolean inside, boolean captionBelow) {
            this(node, caption, caution, inside, null, null, captionBelow);
        }

        /** An area of the screen with no JavaFX node, e.g. an OS-drawn title-bar button. */
        static Target onScreen(Window window, Bounds screen, String caption) {
            return new Target(null, caption, false, false, window, screen, false);
        }

        Object key() {
            return node != null ? node : window;
        }
    }

    /**
     * @param popup the popup window, or {@code null} when drawn inside {@code host}
     * @param host the pane the outline was added to, or {@code null} when drawn in a popup
     * @param pulse heartbeat animation used to get attention; {@code null} for "not yet" outlines
     */
    private record Entry(Popup popup, Pane host, Pane content, Rectangle rect, Label caption, Timeline pulse) {
        void remove() {
            if (popup != null) {
                popup.hide();
            } else {
                host.getChildren().remove(content);
            }
        }
    }

    private static final double PADDING = 5;

    /** Targets wider than this (e.g. the whole viewer) get their caption inside, not beside, the outline. */
    private static final double CAPTION_INSIDE_WIDTH = 400;

    /** Transparent room around the outline so the attention glow isn't clipped by the popup's edge. */
    private static final double GLOW_ROOM = 24;

    private static final String COLOR = "#ff5a1f";
    /** Deliberately not red: red sits too close to the orange "click here" and reads as the same thing. */
    private static final String CAUTION_COLOR = "#546e7a";

    private final Map<Object, Entry> shown = new IdentityHashMap<>();
    private boolean attention;

    /**
     * Makes every "click here" outline pulse and glow (or stop), to draw the eye when the user seems
     * stuck. "Not yet" outlines never pulse — they should stay calm.
     */
    void setAttention(boolean on) {
        if (on == attention) {
            return;
        }
        attention = on;
        shown.values().forEach(e -> pulse(e, on));
    }

    void sync(List<Target> targets) {
        Set<Object> wanted = new HashSet<>();
        for (Target target : targets) {
            if (place(target)) {
                wanted.add(target.key());
            }
        }
        shown.keySet().removeIf(node -> {
            if (wanted.contains(node)) {
                return false;
            }
            pulse(shown.get(node), false);
            shown.get(node).remove();
            return true;
        });
    }

    void clear() {
        shown.values().forEach(e -> {
            pulse(e, false);
            e.remove();
        });
        shown.clear();
        attention = false;
    }

    private static void pulse(Entry entry, boolean on) {
        if (entry.pulse() == null) {
            return;
        }
        if (on) {
            entry.pulse().playFromStart();
        } else {
            entry.pulse().stop();
            entry.rect().setStrokeWidth(3);
            ((DropShadow) entry.rect().getEffect()).setRadius(0);
        }
    }

    /** @return whether the target is visible on screen and now outlined */
    private boolean place(Target target) {
        if (target.inside() && target.node() instanceof Pane host) {
            return placeInside(target, host);
        }
        Node node = target.node();
        Window window;
        Bounds b;
        if (node == null) {
            window = target.window();
            b = target.screen();
        } else {
            window = node.getScene() == null ? null : node.getScene().getWindow();
            b = node.isVisible() ? node.localToScreen(node.getBoundsInLocal()) : null;
        }
        if (window == null || !window.isShowing() || b == null || b.getWidth() <= 0) {
            return false;
        }
        double x = b.getMinX() - PADDING - GLOW_ROOM;
        double y = b.getMinY() - PADDING - GLOW_ROOM;
        double w = b.getWidth() + 2 * PADDING;
        double h = b.getHeight() + 2 * PADDING;

        Entry entry = shown.get(target.key());
        if (entry == null) {
            entry = create(target.caption(), target.caution(), null);
            entry.popup().show(window, x, y);
            shown.put(target.key(), entry);
            if (attention) {
                pulse(entry, true);
            }
        }
        entry.rect().setWidth(w);
        entry.rect().setHeight(h);
        // Keep transparent room on every side so the attention glow isn't clipped by the popup edge.
        ((Pane) entry.rect().getParent())
                .setMinSize(w + 2 * GLOW_ROOM, h + 2 * GLOW_ROOM + (target.captionBelow() ? 30 : 0));
        Label caption = entry.caption();
        if (caption != null) {
            if (target.captionBelow()) {
                caption.setLayoutX(GLOW_ROOM);
                caption.setLayoutY(GLOW_ROOM + h + 4);
            } else if (w > CAPTION_INSIDE_WIDTH) {
                // Beside a viewer-sized target would be off screen: tuck it into the top-left corner.
                caption.setLayoutX(GLOW_ROOM + 12);
                caption.setLayoutY(GLOW_ROOM + 12);
            } else {
                caption.setLayoutX(GLOW_ROOM + w + 6);
                caption.setLayoutY(GLOW_ROOM + Math.max(0, (h - caption.getHeight()) / 2));
            }
        }
        entry.popup().setX(x);
        entry.popup().setY(y);
        return true;
    }

    /** Outlines the inner edge of {@code host} with a layer added to the host itself. */
    private boolean placeInside(Target target, Pane host) {
        if (host.getScene() == null || !host.isVisible() || host.getWidth() <= 0) {
            return false;
        }
        Entry entry = shown.get(host);
        if (entry == null) {
            entry = create(target.caption(), target.caution(), host);
            entry.content().setManaged(false); // never disturb the host's own layout
            host.getChildren().add(entry.content());
            shown.put(host, entry);
            if (attention) {
                pulse(entry, true);
            }
        }
        double inset = 4;
        entry.rect().setLayoutX(inset);
        entry.rect().setLayoutY(inset);
        entry.rect().setWidth(Math.max(0, host.getWidth() - 2 * inset));
        entry.rect().setHeight(Math.max(0, host.getHeight() - 2 * inset));
        if (entry.caption() != null) {
            entry.caption().setLayoutX(inset + 12);
            entry.caption().setLayoutY(inset + 12);
        }
        entry.content().toFront();
        return true;
    }

    private static Entry create(String caption, boolean caution, Pane host) {
        String color = caution ? CAUTION_COLOR : COLOR;
        Rectangle rect = new Rectangle();
        rect.setFill(caution ? Color.web(CAUTION_COLOR, 0.25) : Color.TRANSPARENT);
        rect.setStroke(Color.web(color));
        rect.setStrokeWidth(3);
        rect.setArcWidth(8);
        rect.setArcHeight(8);
        if (caution) {
            rect.getStrokeDashArray().setAll(8.0, 5.0);
        }

        rect.setLayoutX(GLOW_ROOM);
        rect.setLayoutY(GLOW_ROOM);
        Pane content = new Pane(rect);
        Label label = null;
        if (caption != null && !caption.isBlank()) {
            label = new Label((caution ? "⛔ " : "") + caption);
            label.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-padding: 3 8; "
                    + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 4;");
            content.getChildren().add(label);
        }
        content.setMouseTransparent(true);
        content.setPickOnBounds(false);
        Timeline pulse = caution ? null : heartbeat(rect, color);
        if (host != null) {
            return new Entry(null, host, content, rect, label, pulse);
        }

        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);
        popup.getContent().add(content);
        return new Entry(popup, null, content, rect, label, pulse);
    }

    /** A double-beat thicken-and-glow, then a rest, looping: hard to miss in peripheral vision. */
    private static Timeline heartbeat(Rectangle rect, String color) {
        DropShadow glow = new DropShadow(BlurType.GAUSSIAN, Color.web(color), 0, 0.5, 0, 0);
        rect.setEffect(glow);
        var width = rect.strokeWidthProperty();
        var radius = glow.radiusProperty();
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(width, 3), new KeyValue(radius, 0)),
                new KeyFrame(Duration.millis(220), new KeyValue(width, 7), new KeyValue(radius, 22)),
                new KeyFrame(Duration.millis(440), new KeyValue(width, 3), new KeyValue(radius, 6)),
                new KeyFrame(Duration.millis(660), new KeyValue(width, 7), new KeyValue(radius, 22)),
                new KeyFrame(Duration.millis(1000), new KeyValue(width, 3), new KeyValue(radius, 0)),
                new KeyFrame(Duration.millis(1700)));
        t.setCycleCount(Animation.INDEFINITE);
        return t;
    }
}
