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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
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

import java.util.Collection;
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
    private static final int GAP = 4;
    /** The creative inventory has a row of tabs under the window, which the panel must clear. */
    private static final int CREATIVE_TABS_HEIGHT = 28;

    private static final String SWITCH_ICON = "⇄";
    private static final String MOVE_HUD_ICON = "✎";
    private static final int ICON_GAP = 6;

    /**
     * How vanilla lays out the effect list ({@code EffectsInInventory}): it starts two pixels right
     * of the window, needs at least 32 pixels there to be drawn at all, and stacks 32 pixel tall
     * entries 33 pixels apart, squeezed into 132 pixels when there are more than five.
     */
    private static final int EFFECTS_OFFSET = 2;
    private static final int EFFECT_SIZE = 32;
    private static final int EFFECT_SPACING = 33;
    private static final int EFFECTS_SQUEEZED_HEIGHT = 132;
    private static final int EFFECTS_FULL_WIDTH_ROOM = 120;
    private static final int EFFECT_TEXT_PADDING = 7;

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
        AbstractContainerScreenAccessor window = (AbstractContainerScreenAccessor) screen;
        PanelSide side = SPWalletConfig.get().inventoryPanelSide;

        int left = window.spwallet$leftPos();
        int top = window.spwallet$topPos();
        int right = left + window.spwallet$imageWidth();
        int tabs = screen instanceof CreativeModeInventoryScreen ? CREATIVE_TABS_HEIGHT : 0;
        int bottom = top + window.spwallet$imageHeight() + tabs;
        Bounds effects = effectsArea(screen, font, right, top);

        // Room on the chosen side, which decides whether the card numbers fit.
        int room = switch (side) {
            case LEFT -> left - GAP;
            case RIGHT -> screen.width - right - GAP;
            case TOP, BOTTOM, FREE -> screen.width;
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
            case TOP, BOTTOM -> left + (right - left - width) / 2;
            case FREE -> BalanceHud.position(SPWalletConfig.get().inventoryPanelX, width, screen.width);
        };
        int y = switch (side) {
            case LEFT, RIGHT -> top;
            // The creative tabs are above the window as well as below it.
            case TOP -> top - tabs - GAP - height;
            case BOTTOM -> bottom + GAP;
            case FREE -> BalanceHud.position(SPWalletConfig.get().inventoryPanelY, height, screen.height);
        };

        if (effects != null && effects.intersects(x, y, width, height)) {
            Bounds clear = clearOf(effects, x, y, width, height, screen.width, screen.height);

            if (clear != null) {
                x = clear.x();
                y = clear.y();
            } else if (side == PanelSide.RIGHT && left - GAP - width >= 0) {
                // No room on the right with the effects there, so the other side will do.
                x = left - GAP - width;
            }
        }

        // A small window can leave no room on the chosen side even without the numbers; the panel
        // then overlaps the inventory rather than being cut off by the edge of the screen.
        x = Math.clamp(x, 0, Math.max(0, screen.width - width));
        y = Math.clamp(y, 0, Math.max(0, screen.height - height));

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

        switchButton = icon(graphics, font, SWITCH_ICON, switchX, iconY, mouseX, mouseY,
                Component.translatable("gui.spwallet.panel.switch_side",
                        SPWalletConfig.get().inventoryPanelSide.next().label()));
        moveHudButton = icon(graphics, font, MOVE_HUD_ICON, moveX, iconY, mouseX, mouseY,
                Component.translatable("gui.spwallet.panel.move_hud"));

        if (!dragging && header.contains(mouseX, mouseY)
                && !switchButton.contains(mouseX, mouseY) && !moveHudButton.contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("gui.spwallet.panel.drag"), mouseX, mouseY);
        }
    }

    /**
     * Where vanilla draws the list of active effects, or {@code null} when it draws none. Mirrors
     * {@code EffectsInInventory#render}, which the game does not expose.
     */
    private static @Nullable Bounds effectsArea(Screen screen, Font font, int right, int top) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;

        if (player == null || client.level == null) {
            return null;
        }

        Collection<MobEffectInstance> effects = player.getActiveEffects();
        int x = right + EFFECTS_OFFSET;
        int room = screen.width - x;

        if (effects.isEmpty() || room < EFFECT_SIZE) {
            return null;
        }

        int maxWidth = room >= EFFECTS_FULL_WIDTH_ROOM ? room - EFFECT_TEXT_PADDING : EFFECT_SIZE;
        int width = EFFECT_SIZE;
        float tickRate = client.level.tickRateManager().tickrate();

        for (MobEffectInstance effect : effects) {
            int text = Math.max(font.width(effectName(effect)),
                    font.width(MobEffectUtil.formatDuration(effect, 1.0F, tickRate)));
            width = Math.max(width, Math.min(maxWidth, EFFECT_SIZE + text + EFFECT_TEXT_PADDING));
        }

        int spacing = effects.size() > 5 ? EFFECTS_SQUEEZED_HEIGHT / (effects.size() - 1) : EFFECT_SPACING;
        int height = (effects.size() - 1) * spacing + EFFECT_SIZE;

        return new Bounds(x, top, width, height);
    }

    private static Component effectName(MobEffectInstance effect) {
        MutableComponent name = Component.translatable(effect.getEffect().value().getDescriptionId());
        int amplifier = effect.getAmplifier();

        if (amplifier >= 1 && amplifier <= 9) {
            name.append(CommonComponents.SPACE)
                    .append(Component.translatable("enchantment.level." + (amplifier + 1)));
        }

        return name;
    }

    /**
     * The nearest place for a panel that would cover the effects: under them, above them, then
     * beside them. {@code null} when none of those fits on the screen.
     */
    private static @Nullable Bounds clearOf(Bounds effects, int x, int y, int width, int height,
                                            int screenWidth, int screenHeight) {
        List<Bounds> candidates = List.of(
                new Bounds(x, effects.y() + effects.height() + GAP, width, height),
                new Bounds(x, effects.y() - GAP - height, width, height),
                new Bounds(effects.x() + effects.width() + GAP, y, width, height),
                new Bounds(effects.x() - GAP - width, y, width, height));

        for (Bounds candidate : candidates) {
            if (candidate.x() >= 0 && candidate.y() >= 0
                    && candidate.x() + width <= screenWidth && candidate.y() + height <= screenHeight) {
                return candidate;
            }
        }

        return null;
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

        if (header != null && panel != null && header.contains(click.x(), click.y())) {
            dragging = true;
            grabX = click.x() - panel.x();
            grabY = click.y() - panel.y();
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

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
