package qupath.ext.tutorials.support;

import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.layout.GridPane;

/**
 * Pure walk of a dialog's node tree to find the input control for a labelled parameter, e.g. the
 * <em>Score compartment</em> drop-down in QuPath's Positive cell detection dialog.
 *
 * <p>QuPath's parameter panels lay out each parameter as a label and a control in one
 * {@link GridPane} row. Boolean parameters may instead be a {@code CheckBox} carrying the label as its
 * own text. So: find a {@link Labeled} whose text matches; if it is itself a button (check box),
 * return it; otherwise return the control further along the same grid row, falling back to the label
 * itself so there is always something to point at.
 *
 * <p>No QuPath dependency — unit-testable against a plain node tree.
 */
public final class FieldFinder {

    private FieldFinder() {}

    /**
     * @param root the dialog's scene root
     * @param label the parameter's label, matched case-insensitively ignoring a trailing colon
     * @return the node to highlight, or empty if no label matched
     */
    public static Optional<Node> find(Parent root, String label) {
        Optional<Labeled> match = findLabeled(root, normalise(label));
        if (match.isEmpty()) {
            return Optional.empty();
        }
        Labeled labeled = match.get();
        if (labeled instanceof ButtonBase) {
            return Optional.of(labeled);
        }
        return Optional.of(rowControl(labeled).orElse(labeled));
    }

    private static Optional<Labeled> findLabeled(Parent parent, String wanted) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Labeled labeled && wanted.equals(normalise(labeled.getText()))) {
                return Optional.of(labeled);
            }
            if (child instanceof Parent childParent) {
                Optional<Labeled> found = findLabeled(childParent, wanted);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** The first node to the right of {@code label} in the same {@link GridPane} row. */
    private static Optional<Node> rowControl(Node label) {
        if (!(label.getParent() instanceof GridPane grid)) {
            return Optional.empty();
        }
        int row = indexOrZero(GridPane.getRowIndex(label));
        int column = indexOrZero(GridPane.getColumnIndex(label));
        Node best = null;
        int bestColumn = Integer.MAX_VALUE;
        for (Node sibling : grid.getChildren()) {
            int siblingColumn = indexOrZero(GridPane.getColumnIndex(sibling));
            if (sibling != label
                    && indexOrZero(GridPane.getRowIndex(sibling)) == row
                    && siblingColumn > column
                    && siblingColumn < bestColumn) {
                best = sibling;
                bestColumn = siblingColumn;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int indexOrZero(Integer index) {
        return index == null ? 0 : index;
    }

    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.endsWith(":")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed.toLowerCase();
    }
}
