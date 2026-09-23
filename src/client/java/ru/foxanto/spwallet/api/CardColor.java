package ru.foxanto.spwallet.api;

import org.jetbrains.annotations.Nullable;

/**
 * The colours a card can have on SPWorlds.
 *
 * <p>The API sends a card's colour as an index into this list, in this order ({@code "color": 5} is
 * yellow), not as RGB. The shades are picked to look like the site's; the API does not say.
 */
public enum CardColor {
    BLUE(0xFF3B82F6),
    PURPLE(0xFF8B5CF6),
    PINK(0xFFEC4899),
    RED(0xFFEF4444),
    ORANGE(0xFFF97316),
    YELLOW(0xFFEAB308),
    GREEN(0xFF22C55E),
    CYAN(0xFF06B6D4);

    private final int argb;

    CardColor(int argb) {
        this.argb = argb;
    }

    public int argb() {
        return this.argb;
    }

    /** The colour at {@code index}, or {@code null} for an index this list does not know yet. */
    public static @Nullable CardColor byIndex(int index) {
        CardColor[] colors = values();
        return index >= 0 && index < colors.length ? colors[index] : null;
    }
}
