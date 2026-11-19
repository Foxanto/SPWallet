package ru.foxanto.spwallet.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.util.DebugData;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skins of other players, drawn the way the game draws players: through the real player renderer,
 * on a stand-in player that only exists to be drawn. That is what makes the Ears mod work here — it
 * hooks the player renderer, so its ears, tails, wings and snouts come along on every skin and head
 * the wallet shows. Without Ears the same code draws a plain vanilla player.
 *
 * <p>A skin is looked up once per name: the profile by name, its textures from the session server,
 * then the texture through the game's own skin manager and cache. Until it is in, the default skin
 * for that UUID stands in.
 */
public final class PlayerSkins {
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private PlayerSkins() {}

    private static final class Entry {
        volatile GameProfile profile;
        volatile PlayerSkin skin;
        @Nullable Stand stand;
        @Nullable ClientLevel standLevel;
    }

    /** A player that is never in the world, only drawn, wearing whatever skin was looked up. */
    private static final class Stand extends RemotePlayer {
        private final Entry entry;

        Stand(ClientLevel level, Entry entry) {
            super(level, entry.profile);
            this.entry = entry;
        }

        @Override
        public PlayerSkin getSkin() {
            return this.entry.skin;
        }
    }

    /** The skin for {@code name}, the default one until the real one has come in. */
    public static PlayerSkin skin(String name) {
        return entry(name).skin;
    }

    /**
     * Draws the whole player standing in the box, turning to follow the mouse like the player in
     * the inventory does.
     */
    public static void drawModel(GuiGraphics graphics, String name, int x1, int y1, int x2, int y2,
                                 int scale, float mouseX, float mouseY) {
        Stand stand = stand(name);

        if (stand == null) {
            int size = Math.min(x2 - x1, y2 - y1) / 2;
            PlayerFaceRenderer.draw(graphics, skin(name), (x1 + x2 - size) / 2, (y1 + y2 - size) / 2, size);
            return;
        }

        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, scale, 0.0625F,
                mouseX, mouseY, stand);
    }

    /**
     * Draws the player's head in a {@code size} square: the model again, scaled up and cut down to
     * the head, so whatever Ears puts on a head (ears, horns, a snout) shows too. The model looks
     * straight ahead.
     */
    public static void drawHead(GuiGraphics graphics, String name, int x, int y, int size) {
        Stand stand = stand(name);

        if (stand == null) {
            PlayerFaceRenderer.draw(graphics, skin(name), x, y, size);
            return;
        }

        // The box is wider and taller than the head, so ears above and beside it are not cut off;
        // the renderer centres the model, and the y offset lifts the head into the middle.
        // The head is half a block tall, so this scale makes it about four fifths of the box; the
        // offset moves the point the renderer centres on from the middle of the body up to the
        // middle of the head.
        int pad = size / 4;
        int scale = Math.round(size * 1.85F);
        float centreX = x + size / 2F;
        float centreY = y + size / 2F;
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x - pad, y - pad, x + size + pad, y + size + pad,
                scale, 0.68F, centreX, centreY, stand);
    }

    private static @Nullable Stand stand(String name) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;

        if (level == null) {
            return null;
        }

        Entry entry = entry(name);

        // A new world means a new level, and the old stand-in would draw against the old one.
        if (entry.stand == null || entry.standLevel != level) {
            entry.stand = new Stand(level, entry);
            entry.standLevel = level;
        }

        return entry.stand;
    }

    private static Entry entry(String name) {
        return CACHE.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> {
            Entry entry = new Entry();
            UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
            entry.profile = new GameProfile(offline, name);
            entry.skin = DefaultPlayerSkin.get(offline);
            load(name, entry);
            return entry;
        });
    }

    private static void load(String name, Entry entry) {
        Minecraft client = Minecraft.getInstance();

        // Someone in the tab list: the game already has their skin.
        if (client.getConnection() != null) {
            PlayerInfo info = client.getConnection().getPlayerInfoIgnoreCase(name);

            if (info != null) {
                entry.profile = info.getProfile();
                entry.skin = info.getSkin();
                return;
            }
        }

        // While debugging nothing goes out; the local player's skin stands in for everyone.
        if (DebugData.enabled()) {
            if (client.player != null) entry.skin = client.player.getSkin();
            return;
        }

        CompletableFuture.supplyAsync(() -> client.services().profileResolver().fetchByName(name))
                .thenCompose(found -> {
                    Optional<GameProfile> profile = found;
                    if (profile.isEmpty()) return CompletableFuture.completedFuture(Optional.<PlayerSkin>empty());
                    // The textures come with the full profile from the session server.
                    var full = client.services().sessionService().fetchProfile(profile.get().id(), true);
                    GameProfile withTextures = full == null ? profile.get() : full.profile();
                    entry.profile = withTextures;
                    return client.getSkinManager().get(withTextures);
                })
                .thenAccept(skin -> skin.ifPresent(found -> entry.skin = found))
                .exceptionally(error -> {
                    SPWallet.LOGGER.debug("Could not load the skin of {}", name, error);
                    return null;
                });
    }
}
