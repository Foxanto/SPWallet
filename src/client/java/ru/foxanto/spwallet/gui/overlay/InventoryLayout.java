package ru.foxanto.spwallet.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.gui.overlay.CardPanel.Bounds;
import ru.foxanto.spwallet.mixin.client.AbstractContainerScreenAccessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Where a panel beside the inventory, or beside a container screen, goes: against the side the player picked or where they dragged
 * it, then moved out from over whatever it must not cover — the vanilla list of active effects, and
 * the other panels already drawn this frame.
 */
final class InventoryLayout {
    static final int GAP = 4;
    /** The creative inventory has a row of tabs under the window, which the panels must clear. */
    private static final int CREATIVE_TABS_HEIGHT = 28;

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

    /** The inventory window, with the creative tabs counted in above and below it. */
    record Window(int left, int top, int right, int bottom, int tabs) {
        Bounds bounds() {
            return new Bounds(this.left, this.top - this.tabs, this.right - this.left,
                    this.bottom - this.top + this.tabs);
        }
    }

    private InventoryLayout() {}

    static Window window(Screen screen) {
        AbstractContainerScreenAccessor window = (AbstractContainerScreenAccessor) screen;
        int left = window.spwallet$leftPos();
        int top = window.spwallet$topPos();
        int tabs = screen instanceof CreativeModeInventoryScreen ? CREATIVE_TABS_HEIGHT : 0;

        return new Window(left, top, left + window.spwallet$imageWidth(),
                top + window.spwallet$imageHeight() + tabs, tabs);
    }

    /** How wide a panel on {@code side} may be before it runs off the screen. */
    static int room(PanelSide side, Window window, Screen screen) {
        return switch (side) {
            case LEFT -> window.left() - GAP;
            case RIGHT -> screen.width - window.right() - GAP;
            case TOP, BOTTOM, FREE -> screen.width;
        };
    }

    /**
     * Places a panel of the given size.
     *
     * @param freeX where a {@link PanelSide#FREE} panel sits across the screen, as a fraction
     * @param freeY the same, down the screen
     * @param others panels already drawn beside this inventory, which this one must not cover
     */
    static Bounds place(Screen screen, Font font, PanelSide side, float freeX, float freeY,
                        int width, int height, List<Bounds> others) {
        Window window = window(screen);

        int wantedX = switch (side) {
            case LEFT -> window.left() - GAP - width;
            case RIGHT -> window.right() + GAP;
            case TOP, BOTTOM -> window.left() + (window.right() - window.left() - width) / 2;
            case FREE -> BalanceHud.position(freeX, width, screen.width);
        };
        int wantedY = switch (side) {
            case LEFT, RIGHT -> window.top();
            // The creative tabs are above the window as well as below it.
            case TOP -> window.top() - window.tabs() - GAP - height;
            case BOTTOM -> window.bottom() + GAP;
            case FREE -> BalanceHud.position(freeY, height, screen.height);
        };

        // Onto the screen before anything else: a panel too wide for its side is pulled in over the
        // window, and the places it moves to from there must be worked out from where it really is.
        int x = Math.clamp(wantedX, 0, Math.max(0, screen.width - width));
        int y = Math.clamp(wantedY, 0, Math.max(0, screen.height - height));

        List<Bounds> blockers = new ArrayList<>(others);
        Bounds effects = effectsArea(screen, font, window.right(), window.top());

        if (effects != null) {
            blockers.add(effects);
        }

        Bounds placed = new Bounds(x, y, width, height);

        if (blockers.stream().anyMatch(blocker -> blocker.intersects(x, y, width, height))) {
            Bounds clear = clearOf(blockers, placed, side, window, screen);

            if (clear != null) {
                placed = clear;
            }
        }

        // A small window can leave no room on the chosen side; the panel then overlaps the
        // inventory rather than being cut off by the edge of the screen.
        return new Bounds(
                Math.clamp(placed.x(), 0, Math.max(0, screen.width - width)),
                Math.clamp(placed.y(), 0, Math.max(0, screen.height - height)),
                width, height);
    }

    /**
     * The nearest place for a panel that would cover a blocker: next to a blocker along the side the
     * panel is on (above it for a panel on top, under it otherwise), then across from it. A panel on
     * the right may also go to the left of the window. {@code null} when nothing fits on the screen
     * clear of every blocker, and of the window too unless the panel was over it to begin with.
     */
    private static @Nullable Bounds clearOf(List<Bounds> blockers, Bounds panel, PanelSide side,
                                            Window window, Screen screen) {
        int x = panel.x();
        int y = panel.y();
        int width = panel.width();
        int height = panel.height();
        Bounds windowBounds = window.bounds();
        boolean avoidWindow = !windowBounds.intersects(x, y, width, height);

        // The blockers in the way first, so the panel moves as little as it can.
        List<Bounds> ordered = new ArrayList<>(blockers);
        ordered.sort((a, b) -> Boolean.compare(!a.intersects(x, y, width, height), !b.intersects(x, y, width, height)));

        List<Bounds> candidates = new ArrayList<>();

        for (Bounds blocker : ordered) {
            Bounds under = new Bounds(x, blocker.y() + blocker.height() + GAP, width, height);
            Bounds above = new Bounds(x, blocker.y() - GAP - height, width, height);

            candidates.add(side == PanelSide.TOP ? above : under);
            candidates.add(side == PanelSide.TOP ? under : above);
            candidates.add(new Bounds(blocker.x() + blocker.width() + GAP, y, width, height));
            candidates.add(new Bounds(blocker.x() - GAP - width, y, width, height));
        }

        if (side == PanelSide.RIGHT) {
            candidates.add(new Bounds(window.left() - GAP - width, y, width, height));
        }

        for (Bounds candidate : candidates) {
            boolean onScreen = candidate.x() >= 0 && candidate.y() >= 0
                    && candidate.x() + width <= screen.width && candidate.y() + height <= screen.height;
            boolean clear = blockers.stream().noneMatch(blocker ->
                    blocker.intersects(candidate.x(), candidate.y(), width, height));
            boolean offWindow = !avoidWindow || !windowBounds.intersects(candidate.x(), candidate.y(), width, height);

            if (onScreen && clear && offWindow) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Where vanilla draws the list of active effects, or {@code null} when it draws none. Mirrors
     * {@code EffectsInInventory#render}, which the game does not expose.
     */
    private static @Nullable Bounds effectsArea(Screen screen, Font font, int right, int top) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;

        // Chests and the like draw no effects; only the player's own inventory does.
        if (player == null || client.level == null
                || !(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) {
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

    /** Draws a one-glyph button into a panel header and hands back where it can be clicked. */
    static Bounds icon(GuiGraphics graphics, Font font, String icon, int x, int y,
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

    static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
