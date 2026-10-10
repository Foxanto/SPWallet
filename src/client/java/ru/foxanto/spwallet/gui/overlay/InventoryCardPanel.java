package ru.foxanto.spwallet.gui.overlay;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.config.PanelSide;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.gui.HudPositionScreen;
import ru.foxanto.spwallet.gui.overlay.CardPanel.Bounds;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.SPServer;

import java.util.List;

/**
 * The saved cards beside the player's inventory, on whichever side the player picked, or wherever
 * they dragged it by its header.
 *
 * <p>Its header carries two buttons: one moves the panel to the next side, the other opens the HUD
 * position editor, since the inventory is where a player is most likely to look for either.
 *
 * <p>The panel keeps clear of the vanilla list of active effects, which is drawn to the right of the
 * inventory and would otherwise be hidden under the panel.
 *
 * <p>Every card is listed here, favourite or not, and clicking a row is what makes a card a
 * favourite: only those are drawn on the HUD.
 */
public final class InventoryCardPanel {
    private static final String SWITCH_ICON = "⇄";
    private static final String MOVE_HUD_ICON = "✎";
    private static final int ICON_GAP = 6;

    /**
     * The panel with card numbers, and without them for when that is too wide for the side it is
     * on: at the usual GUI scales there are only about 150 pixels beside the inventory.
     */
    private static final List<CardPanel.Style> STYLES = List.of(
            new CardPanel.Style(Component.literal("SPWallet"), 20, true, true),
            new CardPanel.Style(Component.literal("SPWallet"), 20, false, true));

    /** Where the two header buttons were last drawn, for the click that follows. */
    private static @Nullable Bounds switchButton;
    private static @Nullable Bounds moveHudButton;

    /** Where the panel and its header were last drawn, for dragging it about. */
    private static @Nullable Bounds panel;
    private static @Nullable Bounds header;

    private static boolean dragging;
    private static double grabX;
    private static double grabY;

    /** Where each card row was last drawn, and which card it belongs to. */
    private static List<Bounds> cardRows = List.of();
    private static List<CardInfoCache.Row> cardsShown = List.of();

    private InventoryCardPanel() {}

    /** Where the panel was drawn this frame, for the panels drawn after it to keep clear of. */
    static @Nullable Bounds shown() {
        return panel;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) {
                return;
            }

            ScreenEvents.afterRender(screen).register(InventoryCardPanel::render);
            ScreenMouseEvents.allowMouseClick(screen).register(InventoryCardPanel::allowClick);
            ScreenMouseEvents.allowMouseDrag(screen).register(InventoryCardPanel::allowDrag);
            ScreenMouseEvents.allowMouseRelease(screen).register(InventoryCardPanel::allowRelease);
            ScreenEvents.remove(screen).register(removed -> {
                if (dragging) {
                    dragging = false;
                    SPWalletConfig.HANDLER.save();
                }

                switchButton = null;
                moveHudButton = null;
                panel = null;
                header = null;
                cardRows = List.of();
                cardsShown = List.of();
            });
        });
    }

    private static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        switchButton = null;
        moveHudButton = null;
        panel = null;
        header = null;
        cardRows = List.of();
        cardsShown = List.of();

        if (!SPWalletConfig.get().inventoryPanel) {
            return;
        }

        SPServer server = CardInfoCache.server();

        if (server == null) {
            return;
        }

        List<CardInfoCache.Row> rows = CardInfoCache.rows(server);

        if (rows.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        SPWalletConfig config = SPWalletConfig.get();
        PanelSide side = config.inventoryPanelSide;
        // Room on the chosen side, which decides whether the card numbers fit.
        int room = InventoryLayout.room(side, InventoryLayout.window(screen), screen);

        CardPanel.Style style = STYLES.getLast();

        for (CardPanel.Style candidate : STYLES) {
            if (CardPanel.width(font, rows, candidate) <= room) {
                style = candidate;
                break;
            }
        }

        int width = CardPanel.width(font, rows, style);
        int height = CardPanel.height(rows, style);
        Bounds placed = InventoryLayout.place(screen, font, side, config.inventoryPanelX, config.inventoryPanelY,
                width, height, List.of());
        int x = placed.x();
        int y = placed.y();

        cardRows = CardPanel.render(graphics, font, rows, style, x, y, mouseX, mouseY);
        cardsShown = rows;
        panel = new Bounds(x, y, width, height);
        header = new Bounds(x, y, width, CardPanel.PADDING + font.lineHeight + 1);

        if (dragging) {
            graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, EssentialColors.tabTextSelected());
        }

        for (int i = 0; i < rows.size(); i++) {
            if (cardRows.get(i).contains(mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(font, Component.translatable(rows.get(i).favourite()
                        ? "gui.spwallet.panel.unfavourite"
                        : "gui.spwallet.panel.favourite"), mouseX, mouseY);
                break;
            }
        }

        int iconY = CardPanel.headerY(y);
        int switchX = x + width - CardPanel.PADDING - font.width(SWITCH_ICON);
        int moveX = switchX - ICON_GAP - font.width(MOVE_HUD_ICON);

        switchButton = InventoryLayout.icon(graphics, font, SWITCH_ICON, switchX, iconY, mouseX, mouseY,
                Component.translatable("gui.spwallet.panel.switch_side",
                        SPWalletConfig.get().inventoryPanelSide.next().label()));
        moveHudButton = InventoryLayout.icon(graphics, font, MOVE_HUD_ICON, moveX, iconY, mouseX, mouseY,
                Component.translatable("gui.spwallet.panel.move_hud"));

        if (!dragging && header.contains(mouseX, mouseY)
                && !switchButton.contains(mouseX, mouseY) && !moveHudButton.contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("gui.spwallet.panel.drag"), mouseX, mouseY);
        }
    }

    /** Takes clicks on the header buttons, which would otherwise land on the inventory. */
    private static boolean allowClick(Screen screen, MouseButtonEvent click) {
        if (click.button() != 0) {
            return true;
        }

        if (switchButton != null && switchButton.contains(click.x(), click.y())) {
            SPWalletConfig config = SPWalletConfig.get();
            config.inventoryPanelSide = config.inventoryPanelSide.next();
            SPWalletConfig.HANDLER.save();
            InventoryLayout.playClick();
            return false;
        }

        if (moveHudButton != null && moveHudButton.contains(click.x(), click.y())) {
            InventoryLayout.playClick();
            Minecraft.getInstance().setScreen(new HudPositionScreen(screen));
            return false;
        }

        if (header != null && panel != null && header.contains(click.x(), click.y())) {
            dragging = true;
            grabX = click.x() - panel.x();
            grabY = click.y() - panel.y();
            return false;
        }

        for (int i = 0; i < cardRows.size() && i < cardsShown.size(); i++) {
            if (cardRows.get(i).contains(click.x(), click.y())) {
                SPWalletClient.cards().toggleFavourite(cardsShown.get(i).id());
                InventoryLayout.playClick();
                return false;
            }
        }

        return true;
    }

    /** Moves the panel while its header is held, which makes its position a free one. */
    private static boolean allowDrag(Screen screen, MouseButtonEvent drag, double dragX, double dragY) {
        if (!dragging || panel == null) {
            return true;
        }

        SPWalletConfig config = SPWalletConfig.get();
        config.inventoryPanelSide = PanelSide.FREE;
        config.inventoryPanelX = BalanceHud.fraction((int) Math.round(drag.x() - grabX), panel.width(), screen.width);
        config.inventoryPanelY = BalanceHud.fraction((int) Math.round(drag.y() - grabY), panel.height(), screen.height);
        return false;
    }

    private static boolean allowRelease(Screen screen, MouseButtonEvent release) {
        if (!dragging) {
            return true;
        }

        dragging = false;
        SPWalletConfig.HANDLER.save();
        return false;
    }
}
