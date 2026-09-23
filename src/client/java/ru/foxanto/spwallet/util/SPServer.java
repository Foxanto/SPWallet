package ru.foxanto.spwallet.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/** The SPWorlds server the player is currently on. */
public enum SPServer {
    SP("sp.spworlds.ru", "sp"),
    SPM("spm.spworlds.ru", "spm"),
    OTHER(null, null);

    private final String address;
    private final String key;

    SPServer(String address, String key) {
        this.address = address;
        this.key = key;
    }

    /** The server address this constant matches, or {@code null} for {@link #OTHER}. */
    public String address() {
        return this.address;
    }

    /** The key this server's cards are stored under, or {@code null} for {@link #OTHER}. */
    public String key() {
        return this.key;
    }

    public Component label() {
        return Component.translatable("gui.spwallet.server." + this.key);
    }

    /** The server the client is connected to, or {@link #OTHER} when it is not an SPWorlds one. */
    public static SPServer current() {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null) {
            return OTHER;
        }

        ServerData server = client.getCurrentServer();

        if (server == null) {
            return OTHER;
        }

        for (SPServer candidate : values()) {
            if (candidate.address != null && candidate.address.equalsIgnoreCase(server.ip)) {
                return candidate;
            }
        }

        return OTHER;
    }
}
