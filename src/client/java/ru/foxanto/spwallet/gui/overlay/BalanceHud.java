package ru.foxanto.spwallet.gui.overlay;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.HudPositionScreen;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.SPServer;

import java.util.List;

/**
 * The balances of the favourite cards, drawn on the HUD wherever the player has put them.
 *
 * <p>Only the cards starred in the inventory panel are here, and with none starred there is no
 * panel at all - that is what the star is for.
 */
public final class BalanceHud {
    /** Kept between the panel and the edge of the screen, whatever the saved position. */
    public static final int MARGIN = 4;

    public static final CardPanel.Style STYLE = new CardPanel.Style(null, 0, false, false);

    private BalanceHud() {}

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();

        if (!SPWalletConfig.get().hudEnabled
                || client.options.hideGui
                || client.gui.getDebugOverlay().showDebugScreen()
                // The editor draws its own copy, which is the one being dragged.
                || client.screen instanceof HudPositionScreen) {
            return;
        }

        SPServer server = CardInfoCache.server();

        if (server == null) {
            return;
        }

        List<CardInfoCache.Row> rows = CardInfoCache.favouriteRows(server);

        if (rows.isEmpty()) {
            return;
        }

        int width = CardPanel.width(client.font, rows, STYLE);
        int height = CardPanel.height(rows, STYLE);

        CardPanel.render(graphics, client.font, rows, STYLE,
                x(width, graphics.guiWidth()), y(height, graphics.guiHeight()));
    }

    /**
     * Where the panel goes across the screen. The position is saved as a fraction of the room there
     * is, rather than in pixels, so it stays in the same corner when the window or the GUI scale
     * changes and the panel can never end up off screen.
     */
    public static int x(int width, int screenWidth) {
        return MARGIN + Math.round(SPWalletConfig.get().hudX * room(width, screenWidth));
    }

    public static int y(int height, int screenHeight) {
        return MARGIN + Math.round(SPWalletConfig.get().hudY * room(height, screenHeight));
    }

    /** The inverse of {@link #x}/{@link #y}: the fraction a panel edge at {@code position} is at. */
    public static float fraction(int position, int size, int screenSize) {
        int room = room(size, screenSize);
        return room == 0 ? 0 : Math.clamp((position - MARGIN) / (float) room, 0F, 1F);
    }

    private static int room(int size, int screenSize) {
        return Math.max(0, screenSize - size - MARGIN * 2);
    }
}
