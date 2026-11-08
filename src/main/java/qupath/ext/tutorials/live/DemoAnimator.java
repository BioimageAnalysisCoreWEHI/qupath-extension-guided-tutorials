package qupath.ext.tutorials.live;

import java.awt.geom.Point2D;
import java.util.function.BooleanSupplier;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.tutorials.support.DabHotspot;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;

/**
 * A looping "ghost" demonstration drawn over the viewer: a cursor presses, drags, and a dashed
 * rectangle grows behind it, then fades — showing what press-hold-drag-release looks like before the
 * user tries it. Purely visual; it changes nothing in the image.
 *
 * <p>A demo box inevitably reads as "draw <em>here</em>", so where it plays matters. In order:
 *
 * <ol>
 *   <li>Over the slide's known good practice area ({@link SlideTargetFinder}), pinned to the tissue:
 *       it moves and scales with the image as the user pans and zooms.
 *   <li>Otherwise over the brownest part of what is currently on screen, measured from the visible
 *       pixels with the image's own stain vectors.
 *   <li>Otherwise in the centre, with a caption saying any area with cells will do.
 * </ol>
 *
 * <p>Loops only until the user first presses the mouse on the viewer <em>with a drawing tool</em> —
 * at that point they have decided to try, so it goes away for the rest of the step rather than
 * sitting on top of their own drawing. Pressing to pan with the Move tool doesn't count. FX thread
 * only.
 */
final class DemoAnimator {

    private static final double DEFAULT_W = 170;
    private static final double DEFAULT_H = 115;
    private static final double MIN_SIDE = 50;
    private static final double MAX_SIDE = 320;
    private static final double MARGIN = 30;
    private static final String COLOR = "#ff5a1f";
    private static final Logger logger = LoggerFactory.getLogger(DemoAnimator.class);

    /** Sample every Nth screen pixel when measuring DAB; plenty for a box-sized average. */
    private static final int SAMPLE_STEP = 4;

    /** Mean DAB OD over the box below which no area is "clearly brown" (background is ~0). */
    private static final double MIN_MEAN_DAB = 0.08;

    private enum Source {
        TARGET,
        ON_SCREEN,
        CENTRE
    }

    /** Box position and size in view coordinates. */
    private record Placement(double x, double y, double w, double h, Source source) {}

    private final Rectangle box = new Rectangle();
    private final Group cursor = cursor();
    private final Circle press = new Circle(0, 0, 0);
    private final Label caption = new Label();
    private final Pane content = new Pane();
    private Timeline timeline;
    private double animatedW = -1;
    private double animatedH = -1;

    private QuPathViewer viewer;
    private boolean dismissed;
    private String placedFor;
    private Placement placement;
    private final BooleanSupplier drawingToolSelected;
    private final EventHandler<MouseEvent> onPress = e -> {
        if (isDrawingTool()) {
            dismiss();
        }
    };

    /** @param drawingToolSelected whether a press on the viewer would draw (i.e. not the Move tool) */
    DemoAnimator(BooleanSupplier drawingToolSelected) {
        this.drawingToolSelected = drawingToolSelected;

        box.setFill(Color.web(COLOR, 0.08));
        box.setStroke(Color.web(COLOR));
        box.setStrokeWidth(2.5);
        box.getStrokeDashArray().setAll(9.0, 6.0);
        box.setX(MARGIN);
        box.setY(MARGIN);

        press.setFill(Color.TRANSPARENT);
        press.setStroke(Color.web(COLOR));
        press.setStrokeWidth(2);
        press.setCenterX(MARGIN);
        press.setCenterY(MARGIN);

        caption.setStyle("-fx-background-color: " + COLOR + "; -fx-text-fill: white; -fx-padding: 3 8; "
                + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 4;");
        caption.setLayoutX(MARGIN);

        content.getChildren().addAll(box, press, cursor, caption);
        content.setMouseTransparent(true);
        content.setPickOnBounds(false);
        content.setManaged(false); // never disturb the viewer's own layout
    }

    /**
     * Shows (or keeps showing) the demo, re-placing it whenever the view is panned, zoomed or resized
     * or the practice area becomes known. A no-op once the user has pressed the mouse on the viewer
     * with a drawing tool, until {@link #hide} resets it for a new step.
     *
     * @param target the slide's practice area, or {@code null} if not known (yet)
     */
    void show(QuPathViewer viewer, SlideTargetFinder.Target target) {
        if (dismissed) {
            return;
        }
        if (this.viewer != viewer) {
            detach();
            this.viewer = viewer;
            viewer.getView().addEventFilter(MouseEvent.MOUSE_PRESSED, onPress);
        }
        Pane view = viewer.getView();
        if (view.getScene() == null
                || view.getWidth() < DEFAULT_W + 2 * MARGIN
                || view.getHeight() < DEFAULT_H + 2 * MARGIN) {
            hideLayer();
            return;
        }
        String key = viewer.getDownsampleFactor() + "|" + viewer.getCenterPixelX() + "|" + viewer.getCenterPixelY()
                + "|" + view.getWidth() + "|" + view.getHeight() + "|" + target;
        if (!key.equals(placedFor)) {
            placedFor = key;
            // The demo lives inside the viewer, so keep it out of the snapshot used to find brown areas.
            content.setVisible(false);
            placement = place(viewer, target);
            content.setVisible(true);
            caption.setText(
                    switch (placement.source()) {
                        case TARGET -> "Press · hold · drag · let go\nThis is a good brown, cell-rich area to try";
                        case ON_SCREEN ->
                            "Press · hold · drag · let go\nExample only: draw around brown cells like these";
                        case CENTRE -> "Press · hold · drag · let go\nExample only: draw your box anywhere with cells";
                    });
        }
        animate(placement.w(), placement.h());
        // Drawn as a mouse-transparent layer inside the viewer, not a popup window over it: a popup
        // swallows scroll-wheel (zoom) events even where it is transparent.
        content.setLayoutX(placement.x() - MARGIN);
        content.setLayoutY(placement.y() - MARGIN);
        if (content.getParent() != view) {
            if (content.getParent() instanceof Pane old) {
                old.getChildren().remove(content);
            }
            view.getChildren().add(content);
            timeline.playFromStart();
        }
        content.toFront();
    }

    /** Stops the demo and re-arms it, ready for the next step that wants it. */
    void hide() {
        placedFor = null;
        dismissed = false;
        hideLayer();
        detach();
    }

    private Placement place(QuPathViewer viewer, SlideTargetFinder.Target target) {
        Pane view = viewer.getView();
        if (target != null) {
            Placement pinned = pinToTarget(viewer, target);
            if (pinned != null) {
                return pinned;
            }
        }
        Placement onScreen = brownestOnScreen(viewer);
        return onScreen != null
                ? onScreen
                : new Placement(
                        (view.getWidth() - DEFAULT_W) / 2,
                        (view.getHeight() - DEFAULT_H) / 2,
                        DEFAULT_W,
                        DEFAULT_H,
                        Source.CENTRE);
    }

    /**
     * The practice area in view coordinates, if its centre is on screen and it is big enough to draw
     * in. When zoomed in so far that it exceeds {@link #MAX_SIDE}, the demo uses a box of that size
     * centred inside it.
     */
    private static Placement pinToTarget(QuPathViewer viewer, SlideTargetFinder.Target target) {
        Point2D tl = viewer.imagePointToComponentPoint(new Point2D.Double(target.x(), target.y()), null, false);
        Point2D br = viewer.imagePointToComponentPoint(
                new Point2D.Double(target.x() + target.width(), target.y() + target.height()), null, false);
        double w = br.getX() - tl.getX();
        double h = br.getY() - tl.getY();
        double cx = tl.getX() + w / 2;
        double cy = tl.getY() + h / 2;
        Pane view = viewer.getView();
        boolean centreVisible = cx > 0 && cy > 0 && cx < view.getWidth() && cy < view.getHeight();
        if (!centreVisible || w < MIN_SIDE || h < MIN_SIDE) {
            return null;
        }
        double scale = Math.min(1, MAX_SIDE / Math.max(w, h));
        w *= scale;
        h *= scale;
        return new Placement(cx - w / 2, cy - h / 2, w, h, Source.TARGET);
    }

    /**
     * Snapshots what the viewer shows, converts sampled pixels to DAB optical density with the image's
     * stain vectors, and returns the brownest default-sized box, or {@code null} if nothing is brown.
     */
    private static Placement brownestOnScreen(QuPathViewer viewer) {
        Pane view = viewer.getView();
        try {
            WritableImage shot = view.snapshot(null, null);
            double scale = shot.getWidth() / Math.max(1, view.getWidth()); // HiDPI snapshots are larger
            int step = Math.max(1, (int) Math.round(SAMPLE_STEP * scale));
            int gw = (int) (shot.getWidth() / step);
            int gh = (int) (shot.getHeight() / step);
            PixelReader reader = shot.getPixelReader();
            int[] rgb = new int[gw * gh];
            for (int y = 0; y < gh; y++) {
                for (int x = 0; x < gw; x++) {
                    rgb[y * gw + x] = reader.getArgb(x * step, y * step);
                }
            }
            float[] dab = new float[rgb.length];
            ImageData<?> imageData = viewer.getImageData();
            ColorDeconvolutionStains stains = imageData == null ? null : imageData.getColorDeconvolutionStains();
            if (stains != null) {
                ColorTransformer.getTransformedPixels(rgb, ColorTransformer.ColorTransformMethod.Stain_2, dab, stains);
            } else {
                ColorTransformer.getSimpleTransformedPixels(rgb, ColorTransformer.ColorTransformMethod.DAB_H_DAB, dab);
            }
            double cell = step / scale; // grid cell size in view coordinates
            int boxW = (int) Math.ceil(DEFAULT_W / cell);
            int boxH = (int) Math.ceil(DEFAULT_H / cell);
            return DabHotspot.find(dab, gw, gh, boxW, boxH, MIN_MEAN_DAB)
                    .map(h -> new Placement(h.x() * cell, h.y() * cell, DEFAULT_W, DEFAULT_H, Source.ON_SCREEN))
                    .orElse(null);
        } catch (RuntimeException e) {
            logger.debug("Could not measure DAB for demo placement", e);
            return null;
        }
    }

    /** (Re)builds the press–drag–hold–fade loop for a box of this size, if the size changed. */
    private void animate(double w, double h) {
        if (timeline != null && Math.abs(w - animatedW) < 2 && Math.abs(h - animatedH) < 2) {
            return;
        }
        boolean wasPlaying = timeline != null && timeline.getStatus() == Animation.Status.RUNNING;
        if (timeline != null) {
            timeline.stop();
        }
        animatedW = w;
        animatedH = h;
        caption.setLayoutY(MARGIN + h + 14);
        content.setPrefSize(w + 2 * MARGIN + 160, h + 2 * MARGIN + 50);

        Interpolator ease = Interpolator.EASE_BOTH;
        timeline = new Timeline(
                // Reset: nothing drawn, cursor at the start corner, visible.
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(content.opacityProperty(), 1),
                        new KeyValue(box.widthProperty(), 0),
                        new KeyValue(box.heightProperty(), 0),
                        new KeyValue(cursor.translateXProperty(), MARGIN),
                        new KeyValue(cursor.translateYProperty(), MARGIN),
                        new KeyValue(press.radiusProperty(), 0),
                        new KeyValue(press.opacityProperty(), 1)),
                // Press: a ripple at the start corner.
                new KeyFrame(
                        Duration.millis(450),
                        new KeyValue(press.radiusProperty(), 14),
                        new KeyValue(press.opacityProperty(), 0),
                        new KeyValue(box.widthProperty(), 0),
                        new KeyValue(box.heightProperty(), 0),
                        new KeyValue(cursor.translateXProperty(), MARGIN),
                        new KeyValue(cursor.translateYProperty(), MARGIN)),
                // Drag: the box grows with the cursor at its far corner.
                new KeyFrame(
                        Duration.millis(1800),
                        new KeyValue(box.widthProperty(), w, ease),
                        new KeyValue(box.heightProperty(), h, ease),
                        new KeyValue(cursor.translateXProperty(), MARGIN + w, ease),
                        new KeyValue(cursor.translateYProperty(), MARGIN + h, ease)),
                // Let go: hold the finished box, then fade.
                new KeyFrame(Duration.millis(2700), new KeyValue(content.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(3200), new KeyValue(content.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(3800)));
        timeline.setCycleCount(Animation.INDEFINITE);
        if (wasPlaying) {
            timeline.playFromStart();
        }
    }

    private boolean isDrawingTool() {
        try {
            return drawingToolSelected.getAsBoolean();
        } catch (RuntimeException e) {
            return true; // can't tell: assume they're drawing, and get out of the way
        }
    }

    private void dismiss() {
        dismissed = true;
        hideLayer();
        detach();
    }

    private void hideLayer() {
        if (timeline != null) {
            timeline.stop();
        }
        if (content.getParent() instanceof Pane parent) {
            parent.getChildren().remove(content);
        }
    }

    private void detach() {
        if (viewer != null) {
            viewer.getView().removeEventFilter(MouseEvent.MOUSE_PRESSED, onPress);
            viewer = null;
        }
    }

    /** A classic arrow pointer, tip at (0, 0). */
    private static Group cursor() {
        Polygon arrow = new Polygon(0, 0, 0, 20, 5, 15.5, 8.5, 23, 12, 21.5, 8.5, 14, 15, 14);
        arrow.setFill(Color.WHITE);
        arrow.setStroke(Color.BLACK);
        arrow.setStrokeWidth(1.2);
        return new Group(arrow);
    }
}
