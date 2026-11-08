package qupath.ext.tutorials.engine;

import java.util.List;

/**
 * An ordered list of steps.
 *
 * @param title shown in the menu and the panel header
 * @param sourceUrl the QuPath documentation page this tutorial follows
 * @param steps the steps, in order
 * @param completion shown on the "Tutorial complete" screen: what the user did and what to try next
 */
public record Tutorial(String title, String sourceUrl, List<Step> steps, String completion) {}
