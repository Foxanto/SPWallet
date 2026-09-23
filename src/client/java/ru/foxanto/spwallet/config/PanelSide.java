package ru.foxanto.spwallet.config;

import net.minecraft.network.chat.Component;

/** Which side of the inventory window the card panel sits on. */
public enum PanelSide {
    LEFT("left"),
    BOTTOM("bottom"),
    RIGHT("right");

    private final String key;

    PanelSide(String key) {
        this.key = key;
    }

    public Component label() {
        return Component.translatable("config.spwallet.panel_side." + this.key);
    }

    /** The side after this one, for the switch button on the panel itself. */
    public PanelSide next() {
        PanelSide[] sides = values();
        return sides[(this.ordinal() + 1) % sides.length];
    }
}
