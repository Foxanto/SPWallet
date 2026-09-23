package ru.foxanto.spwallet.util;

import net.minecraft.network.chat.Component;

/** How the receiver of a transfer is addressed. */
public enum TransferMode {
    /** By card number, which is what the API itself takes. */
    NUMBER("number"),
    /** By player nickname, resolved to one of that player's cards first. */
    NICKNAME("nickname");

    private final String key;

    TransferMode(String key) {
        this.key = key;
    }

    public Component label() {
        return Component.translatable("gui.spwallet.transfer_mode." + this.key);
    }
}
