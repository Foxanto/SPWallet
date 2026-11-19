package ru.foxanto.spwallet.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Rounded rectangles out of plain fills, since the GUI only draws square ones. */
public final class Shapes {
    private Shapes() {}

    /**
     * Fills a rectangle whose corners are cut to a quarter circle of {@code radius} pixels, row by
     * row, so it stays crisp at the pixel scale everything else is drawn at.
     */
    public static void rounded(GuiGraphics g, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));

        if (r == 0) {
            g.fill(x, y, x + width, y + height, color);
            return;
        }

        g.fill(x, y + r, x + width, y + height - r, color);

        for (int i = 0; i < r; i++) {
            // How far in the corner pushes this row, measured at the middle of the pixel.
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
            g.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    /** A one-pixel rounded outline, for fields and panels. */
    public static void roundedOutline(GuiGraphics g, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        g.fill(x + r, y, x + width - r, y + 1, color);
        g.fill(x + r, y + height - 1, x + width - r, y + height, color);
        g.fill(x, y + r, x + 1, y + height - r, color);
        g.fill(x + width - 1, y + r, x + width, y + height - r, color);

        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            int next = i + 1 < r ? (int) Math.round(r - Math.sqrt(r * r - (r - i - 1.5) * (r - i - 1.5))) : 0;
            int span = Math.max(1, inset - next);
            g.fill(x + inset - span + 1, y + i, x + inset + 1, y + i + 1, color);
            g.fill(x + width - inset - 1, y + i, x + width - inset + span - 1, y + i + 1, color);
            g.fill(x + inset - span + 1, y + height - i - 1, x + inset + 1, y + height - i, color);
            g.fill(x + width - inset - 1, y + height - i - 1, x + width - inset + span - 1, y + height - i, color);
        }
    }

    /** {@code color} with its RGB pushed towards white by {@code amount} (0–1). */
    public static int lighter(int color, float amount) {
        int a = color >>> 24;
        int r = (color >> 16) & 255;
        int gr = (color >> 8) & 255;
        int b = color & 255;
        r += Math.round((255 - r) * amount);
        gr += Math.round((255 - gr) * amount);
        b += Math.round((255 - b) * amount);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    /** {@code color} with its RGB pushed towards black by {@code amount} (0–1). */
    public static int darker(int color, float amount) {
        int a = color >>> 24;
        int r = Math.round(((color >> 16) & 255) * (1 - amount));
        int gr = Math.round(((color >> 8) & 255) * (1 - amount));
        int b = Math.round((color & 255) * (1 - amount));
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }
}
