package ru.foxanto.spwallet.gui.overlay;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.config.AmountFormat;
import ru.foxanto.spwallet.config.PanelSide;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.gui.overlay.CardPanel.Bounds;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.OreCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * How much АР — diamond ore, plain and deepslate — the player carries, counting the shulker boxes in
 * the inventory, as a panel of its own beside the inventory.
 *
 * <p>It is placed like the card panel: on the side the player picked with its ⇄ button or in the
 * config, or wherever they dragged it by its header. It keeps clear of the card panel and the list of
 * active effects, so the two panels can share a side and stack up.
 *
 * <p>In a chest, barrel, ender chest or shulker box it shows up as well, counting the open container
 * next to the inventory and the two together. Hovering a shulker box anywhere adds its АР to the
 * item's tooltip.
 */
public final class OrePanel {
    private static final int PADDING = CardPanel.PADDING;
    private static final int ROW_HEIGHT = 9;
    private static final int ROW_GAP = 3;
    private static final int COLUMN_GAP = 8;
    /** The header line plus the gap under it, as on the card panel. */
    private static final int HEADER_HEIGHT = 9 + 5;
    /** The gap and divider above the total. */
    private static final int TOTAL_GAP = 5;

    private static final String SWITCH_ICON = "⇄";
    private static final int ICON_RESERVE = 10;
    /** A container's name is its row label, and a renamed chest can have a long one. */
    private static final int MAX_LABEL_WIDTH = 100;

    private static @Nullable Bounds switchButton;
    private static @Nullable Bounds panel;
    private static @Nullable Bounds header;

    private static boolean dragging;
    private static double grabX;
    private static double grabY;

    private OrePanel() {}

    /** Has to come after {@link InventoryCardPanel#register()}, so the card panel is drawn first. */
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> addTooltip(stack, lines));

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(isInventory(screen) || isContainer(screen))) {
                return;
            }

            ScreenEvents.afterRender(screen).register(OrePanel::render);
            ScreenMouseEvents.allowMouseClick(screen).register(OrePanel::allowClick);
            ScreenMouseEvents.allowMouseDrag(screen).register(OrePanel::allowDrag);
            ScreenMouseEvents.allowMouseRelease(screen).register(OrePanel::allowRelease);
            ScreenEvents.remove(screen).register(removed -> {
                if (dragging) {
                    dragging = false;
                    SPWalletConfig.HANDLER.save();
                }

                switchButton = null;
                panel = null;
                header = null;
            });
        });
    }

    private static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        switchButton = null;
        panel = null;
        header = null;

        SPWalletConfig config = SPWalletConfig.get();
        LocalPlayer player = Minecraft.getInstance().player;

        if (!config.oreCounter || player == null || CardInfoCache.server() == null) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        OreCounter.Count carried = OreCounter.count(player);
        OreCounter.Count stored = isContainer(screen)
                ? OreCounter.container(((AbstractContainerScreen<?>) screen).getMenu(), player)
                : null;
        OreCounter.Count count = stored == null ? carried : carried.plus(stored);

        if (config.orePanelHideEmpty && count.total() == 0) {
            return;
        }

        Component title = Component.translatable("gui.spwallet.ore_panel.title");
        // In the inventory the rows are the two kinds of ore; in a container, where the ore lies.
        List<Row> rows = stored == null
                ? List.of(
                        new Row(Component.translatable("gui.spwallet.ore_panel.plain"), number(count.ore())),
                        new Row(Component.translatable("gui.spwallet.ore_panel.deepslate"), number(count.deepslate())))
                : List.of(
                        new Row(Component.translatable("gui.spwallet.ore_panel.inventory"), number(carried.total())),
                        new Row(containerLabel(font, screen), number(stored.total())));
        Row total = new Row(Component.translatable("gui.spwallet.ore_panel.total"),
                AmountFormat.ore(count.total()));

        int width = font.width(title) + COLUMN_GAP + ICON_RESERVE;

        for (Row row : append(rows, total)) {
            width = Math.max(width, font.width(row.label()) + COLUMN_GAP + font.width(row.value()));
        }

        width += PADDING * 2;
        int height = PADDING * 2 + HEADER_HEIGHT + rows.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP + TOTAL_GAP + ROW_HEIGHT;

        Bounds cards = InventoryCardPanel.shown();
        Bounds placed = InventoryLayout.place(screen, font, config.orePanelSide, config.orePanelX, config.orePanelY,
                width, height, cards == null ? List.of() : List.of(cards));
        int x = placed.x();
        int y = placed.y();

        graphics.fill(x, y, x + width, y + height, EssentialColors.hudBackground());
        graphics.renderOutline(x, y, width, height, EssentialColors.border());

        if (dragging) {
            graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, EssentialColors.tabTextSelected());
        }

        int top = y + PADDING;
        graphics.drawString(font, title, x + PADDING, top, EssentialColors.screenTitle(), EssentialColors.textShadow());
        top += HEADER_HEIGHT;

        for (Row row : rows) {
            drawRow(graphics, font, row, x, width, top, EssentialColors.tabText());
            top += ROW_HEIGHT + ROW_GAP;
        }

        top += TOTAL_GAP - ROW_GAP;
        graphics.fill(x + PADDING, top - 3, x + width - PADDING, top - 2, EssentialColors.border());
        drawRow(graphics, font, total, x, width, top, EssentialColors.tabTextSelected());

        panel = placed;
        header = new Bounds(x, y, width, PADDING + font.lineHeight + 1);

        int switchX = x + width - PADDING - font.width(SWITCH_ICON);
        switchButton = InventoryLayout.icon(graphics, font, SWITCH_ICON, switchX, y + PADDING, mouseX, mouseY,
                Component.translatable("gui.spwallet.panel.switch_side", config.orePanelSide.next().label()));

        if (dragging || switchButton.contains(mouseX, mouseY)) {
            return;
        }

        if (header.contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("gui.spwallet.panel.drag"), mouseX, mouseY);
        } else if (panel.contains(mouseX, mouseY)) {
            List<Component> lines = stored == null
                    ? List.of(Component.translatable("gui.spwallet.ore_panel.loose", number(count.loose())),
                            Component.translatable("gui.spwallet.ore_panel.shulkers", number(count.inShulkers())))
                    : List.of(Component.translatable("gui.spwallet.ore_panel.plain_count", number(count.ore())),
                            Component.translatable("gui.spwallet.ore_panel.deepslate_count", number(count.deepslate())),
                            Component.translatable("gui.spwallet.ore_panel.shulkers", number(count.inShulkers())));

            graphics.setTooltipForNextFrame(font,
                    lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
        }
    }

    private static boolean isInventory(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
    }

    /** Screens of something that stores items: chests, barrels, ender chests, shulker boxes. */
    private static boolean isContainer(Screen screen) {
        return screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen;
    }

    /** The container's own name, "Сундук" or whatever it was renamed to, cut short if it is long. */
    private static Component containerLabel(Font font, Screen screen) {
        String name = screen.getTitle().getString();

        if (font.width(name) <= MAX_LABEL_WIDTH) {
            return Component.literal(name);
        }

        return Component.literal(font.plainSubstrByWidth(name, MAX_LABEL_WIDTH - font.width("…")) + "…");
    }

    private static Component number(int value) {
        return AmountFormat.ore(value);
    }

    /** Puts the АР a shulker box holds right under its name. */
    private static void addTooltip(ItemStack stack, List<Component> lines) {
        if (!SPWalletConfig.get().oreTooltip || lines.isEmpty() || CardInfoCache.server() == null
                || stack.get(DataComponents.CONTAINER) == null) {
            return;
        }

        int total = OreCounter.inside(stack).total();

        if (total > 0) {
            lines.add(1, Component.translatable("gui.spwallet.ore_panel.tooltip", number(total))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private record Row(Component label, Component value) {}

    private static List<Row> append(List<Row> rows, Row row) {
        List<Row> all = new ArrayList<>(rows);
        all.add(row);
        return all;
    }

    private static void drawRow(GuiGraphics graphics, Font font, Row row, int x, int width, int y, int valueColor) {
        graphics.drawString(font, row.label(), x + PADDING, y, EssentialColors.modalText(), EssentialColors.textShadow());
        graphics.drawString(font, row.value(), x + width - PADDING - font.width(row.value()), y,
                valueColor, EssentialColors.textShadow());
    }

    private static boolean allowClick(Screen screen, MouseButtonEvent click) {
        if (click.button() != 0) {
            return true;
        }

        if (switchButton != null && switchButton.contains(click.x(), click.y())) {
            SPWalletConfig config = SPWalletConfig.get();
            config.orePanelSide = config.orePanelSide.next();
            SPWalletConfig.HANDLER.save();
            InventoryLayout.playClick();
            return false;
        }

        if (header != null && panel != null && header.contains(click.x(), click.y())) {
            dragging = true;
            grabX = click.x() - panel.x();
            grabY = click.y() - panel.y();
            return false;
        }

        // The rest of the panel only shows numbers, but a click on it should not reach the slots under it.
        return panel == null || !panel.contains(click.x(), click.y());
    }

    private static boolean allowDrag(Screen screen, MouseButtonEvent drag, double dragX, double dragY) {
        if (!dragging || panel == null) {
            return true;
        }

        SPWalletConfig config = SPWalletConfig.get();
        config.orePanelSide = PanelSide.FREE;
        config.orePanelX = BalanceHud.fraction((int) Math.round(drag.x() - grabX), panel.width(), screen.width);
        config.orePanelY = BalanceHud.fraction((int) Math.round(drag.y() - grabY), panel.height(), screen.height);
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
