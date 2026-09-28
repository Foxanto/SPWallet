package ru.foxanto.spwallet.config;

import net.minecraft.network.chat.Component;

/**
 * Where the card panel sits in the inventory: against one side of the inventory window, or wherever
 * the player dragged it ({@link #FREE}).
 */
public enum PanelSide {
    LEFT("left"),
    TOP("top"),
    RIGHT("right"),
    BOTTOM("bottom"),
    /** Wherever the player dragged the panel by its header, saved in the config as a fraction. */
    FREE("free");

    private final String key;

    PanelSide(String key) {
        this.key = key;
    }

    public Component label() {
        return Component.translatable("config.spwallet.panel_side." + this.key);
    }

    /**
     * The side after this one, for the switch button on the panel itself. {@link #FREE} is skipped:
     * the panel gets there by being dragged, not by the button.
     */
    public PanelSide next() {
        PanelSide[] sides = values();
        PanelSide next = sides[(this.ordinal() + 1) % sides.length];
        return next == FREE ? next.next() : next;
    }
}
