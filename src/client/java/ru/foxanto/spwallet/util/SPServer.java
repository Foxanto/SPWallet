package ru.foxanto.spwallet.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** The SPWorlds server the player is currently on. */
public enum SPServer {
    SP("sp", "sp.spworlds.ru", "sp.spworlds.org"),
    SPM("spm", "spm.spworlds.ru", "spm.spworlds.org"),
    OTHER(null);

    private final String key;
    private final List<String> hosts;

    SPServer(String key, String... hosts) {
        this.key = key;
        this.hosts = List.of(hosts);
    }

    /** The main address of this server, or {@code null} for {@link #OTHER}. */
    public String address() {
        return this.hosts.isEmpty() ? null : this.hosts.get(0);
    }

    /**
     * The server an address in the server list points to: either domain of it, with or without a
     * port, any case, and with a trailing dot or a subdomain in front.
     */
    public static SPServer byAddress(String address) {
        String host = address.toLowerCase(Locale.ROOT).trim();
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);

        for (SPServer candidate : values()) {
            for (String known : candidate.hosts) {
                if (host.equals(known) || host.endsWith("." + known)) {
                    return candidate;
                }
            }
        }

        return OTHER;
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

        return byAddress(server.ip);
    }
}
