package qupath.ext.tutorials.live;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import qupath.ext.tutorials.engine.Tip;

/** Draws a {@link Tip} as a row: a small vector icon, key chips, and the hint text. No image files. */
final class TipGraphics {

    private static final Color INK = Color.web("#607d8b");
    private static final Color ACCENT = Color.web("#ff5a1f");

    private TipGraphics() {}

    /** @param keys the resolved key labels (literal + shortcuts read from QuPath's menus) */
    static Node row(Tip tip, List<String> keys) {
        if (tip.icon() == Tip.Icon.REASSURE) {
            Label callout = new Label("🛡  " + tip.text());
            callout.setWrapText(true);
            callout.setMaxWidth(Double.MAX_VALUE);
            callout.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #1565c0; -fx-border-width: 0 0 0 5; "
                    + "-fx-text-fill: #0d47a1; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 12; "
                    + "-fx-background-radius: 3;");
            return callout;
        }
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(icon(tip.icon()));
        for (String key : keys) {
            row.getChildren().add(keyChip(key));
        }
        Label text = new Label(tip.text());
        text.setWrapText(true);
        row.getChildren().add(text);
        return row;
    }

    private static Node icon(Tip.Icon icon) {
        return switch (icon) {
            case MOUSE_SCROLL -> mouse(true);
            case MOUSE_DRAG -> mouse(false);
            case KEYBOARD -> keyboard();
            case REASSURE -> new Group(); // drawn as a callout by row()
        };
    }

    /** A mouse body; with a highlighted wheel and up/down arrows, or with a drag arrow beside it. */
    private static Node mouse(boolean scroll) {
        Rectangle body = new Rectangle(0, 0, 18, 28);
        body.setArcWidth(16);
        body.setArcHeight(16);
        body.setFill(Color.TRANSPARENT);
        body.setStroke(INK);
        body.setStrokeWidth(1.6);
        Line split = new Line(9, 0, 9, 10);
        split.setStroke(INK);
        Rectangle wheel = new Rectangle(7.5, 4, 3, 6);
        wheel.setArcWidth(3);
        wheel.setArcHeight(3);
        wheel.setFill(scroll ? ACCENT : INK);
        Group g = new Group(body, split, wheel);
        if (scroll) {
            g.getChildren().addAll(arrow(26, 2, true), arrow(26, 26, false));
        } else {
            Line shaft = new Line(22, 22, 32, 12);
            shaft.setStroke(ACCENT);
            shaft.setStrokeWidth(2);
            Polygon head = new Polygon(34, 10, 27, 11, 33, 17);
            head.setFill(ACCENT);
            g.getChildren().addAll(shaft, head);
        }
        return g;
    }

    private static Node arrow(double x, double y, boolean up) {
        Polygon p = up ? new Polygon(x, y, x - 4, y + 6, x + 4, y + 6) : new Polygon(x, y, x - 4, y - 6, x + 4, y - 6);
        p.setFill(ACCENT);
        return p;
    }

    private static Node keyboard() {
        Group g = new Group();
        Rectangle frame = new Rectangle(0, 6, 30, 18);
        frame.setArcWidth(5);
        frame.setArcHeight(5);
        frame.setFill(Color.TRANSPARENT);
        frame.setStroke(INK);
        frame.setStrokeWidth(1.6);
        g.getChildren().add(frame);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                Rectangle key = new Rectangle(4 + col * 6, 10 + row * 5, 4, 3);
                key.setFill(INK);
                g.getChildren().add(key);
            }
        }
        Rectangle space = new Rectangle(8, 20, 14, 2);
        space.setFill(INK);
        g.getChildren().add(space);
        return g;
    }

    private static Node keyChip(String key) {
        Label chip = new Label(key);
        chip.setStyle("-fx-font-family: monospace; -fx-font-weight: bold; -fx-padding: 2 7; "
                + "-fx-border-color: #90a4ae; -fx-border-width: 1 1 3 1; -fx-border-radius: 4; "
                + "-fx-background-radius: 4;");
        return chip;
    }
}
