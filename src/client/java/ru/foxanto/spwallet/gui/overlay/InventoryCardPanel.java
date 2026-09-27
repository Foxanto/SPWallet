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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.config.PanelSide;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.gui.HudPositionScreen;
import ru.foxanto.spwallet.gui.overlay.CardPanel.Bounds;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.mixin.client.AbstractContainerScreenAccessor;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.SPServer;

import java.util.List;

/**
 * The saved cards beside the player's inventory, on whichever side the player picked.
 *
 * <p>Its header carries two buttons: one moves the panel to the next side, the other opens the HUD
 * position editor, since the inventory is where a player is most likely to look for either.
 *
 * <p>Every card is listed here, favourite or not, and clicking a row is what makes a card a
 * favourite: only those are drawn on the HUD.
 */
public final class InventoryCardPanel {
    private static final int GAP = 4;
    /** The creative inventory has a row of tabs under the window, which the panel must clear. */
    private static final int CREATIVE_TABS_HEIGHT = 28;

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

    /** Where each card row was last drawn, and which card it belongs to. */
    private static List<Bounds> cardRows = List.of();
    private static List<CardInfoCache.Row> cardsShown = List.of();

    private InventoryCardPanel() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) {
                return;
            }

            ScreenEvents.afterRender(screen).register(InventoryCardPanel::render);
            ScreenMouseEvents.allowMouseClick(screen).register(InventoryCardPanel::allowClick);
            ScreenEvents.remove(screen).register(removed -> {
                switchButton = null;
                moveHudButton = null;
                cardRows = List.of();
                cardsShown = List.of();
            });
        });
    }

    private static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        switchButton = null;
        moveHudButton = null;
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
        AbstractContainerScreenAccessor window = (AbstractContainerScreenAccessor) screen;
        PanelSide side = SPWalletConfig.get().inventoryPanelSide;

        int left = window.spwallet$leftPos();
        int top = window.spwallet$topPos();
        int right = left + window.spwallet$imageWidth();
        int bottom = top + window.spwallet$imageHeight()
                + (screen instanceof CreativeModeInventoryScreen ? CREATIVE_TABS_HEIGHT : 0);

        // Room on the chosen side, which decides whether the card numbers fit.
        int room = switch (side) {
            case LEFT -> left - GAP;
            case RIGHT -> screen.width - right - GAP;
            case BOTTOM -> screen.width;
        };

        CardPanel.Style style = STYLES.getLast();

        for (CardPanel.Style candidate : STYLES) {
            if (CardPanel.width(font, rows, candidate) <= room) {
                style = candidate;
                break;
            }
        }

        int width = CardPanel.width(font, rows, style);
        int height = CardPanel.height(rows, style);

        int x = switch (side) {
            case LEFT -> left - GAP - width;
            case RIGHT -> right + GAP;
            case BOTTOM -> left + (right - left - width) / 2;
        };
        int y = side == PanelSide.BOTTOM ? bottom + GAP : top;

        // A small window can leave no room on the chosen side even without the numbers; the panel
        // then overlaps the inventory rather than being cut off by the edge of the screen.
        x = Math.clamp(x, 0, Math.max(0, screen.width - width));
        y = Math.clamp(y, 0, Math.max(0, screen.height - height));

        cardRows = CardPanel.render(graphics, font, rows, style, x, y, mouseX, mouseY);
        cardsShown = rows;

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

        switchButton = icon(graphics, font, SWITCH_ICON, switchX, iconY, mouseX, mouseY,
                Component.translatable("gui.spwallet.panel.switch_side",
                        SPWalletConfig.get().inventoryPanelSide.next().label()));
        moveHudButton = icon(graphics, font, MOVE_HUD_ICON, moveX, iconY, mouseX, mouseY,
                Component.translatable("gui.spwallet.panel.move_hud"));
    }

    private static Bounds icon(GuiGraphics graphics, Font font, String icon, int x, int y,
                               int mouseX, int mouseY, Component tooltip) {
        // One pixel of slack around the glyph, which is a small target otherwise.
        Bounds bounds = new Bounds(x - 1, y - 1, font.width(icon) + 2, font.lineHeight + 1);
        boolean hovered = bounds.contains(mouseX, mouseY);

        graphics.drawString(font, icon, x, y,
                hovered ? EssentialColors.tabTextSelected() : EssentialColors.tabText(),
                EssentialColors.textShadow());

        if (hovered) {
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }

        return bounds;
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
            playClick();
            return false;
        }

        if (moveHudButton != null && moveHudButton.contains(click.x(), click.y())) {
            playClick();
            Minecraft.getInstance().setScreen(new HudPositionScreen(screen));
            return false;
        }

        for (int i = 0; i < cardRows.size() && i < cardsShown.size(); i++) {
            if (cardRows.get(i).contains(click.x(), click.y())) {
                SPWalletClient.cards().toggleFavourite(cardsShown.get(i).id());
                playClick();
                return false;
            }
        }

        return true;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
