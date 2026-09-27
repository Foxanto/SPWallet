package ru.foxanto.spwallet.config;

import net.minecraft.network.chat.Component;

/** Which of the two palettes the mod's screens and panels are drawn in. */
public enum Theme {
    DARK("dark"),
    LIGHT("light");

    private final String key;

    Theme(String key) {
        this.key = key;
    }

    public Component label() {
        return Component.translatable("config.spwallet.theme." + this.key);
    }
}
