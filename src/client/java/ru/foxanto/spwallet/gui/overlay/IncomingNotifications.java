package ru.foxanto.spwallet.gui.overlay;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.api.CardColor;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.gui.HudPositionScreen;
import ru.foxanto.spwallet.util.PaymentSound;

import java.util.ArrayList;
import java.util.List;

/**
 * "Money came in" notifications, drawn on the HUD wherever the player put them - the bottom left
 * corner unless moved in the HUD position editor.
 *
 * <p>A stopgap until the SPWorlds API can list transactions: {@link ru.foxanto.spwallet.util.CardInfoCache}
 * compares each balance it fetches with the one before and reports a rise here. So a notification
 * says how much a card gained since the last check, not who sent it, and two transfers between
 * checks show up as one.
 */
public final class IncomingNotifications {
    private static final long SHOWN_MS = 6_000;
    private static final long SLIDE_MS = 250;
    private static final int MAX_SHOWN = 4;

    private static final int PADDING = 4;
    private static final int STRIP_WIDTH = 2;
    private static final int STRIP_GAP = 5;
    private static final int LINE_GAP = 3;
    private static final int GAP = 3;

    /**
     * One notification.
     *
     * @param color the card's colour, or {@code null} when it is not known yet
     * @param shownAt when it appeared, in {@link System#currentTimeMillis()} terms
     */
    public record Entry(String cardName, @Nullable Integer color, int amount, int balance, long shownAt) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private IncomingNotifications() {}

    /** Shows that {@code amount} came in to a card, and plays the sound for it. */
    public static void push(String cardName, @Nullable Integer color, int amount, int balance) {
        if (!SPWalletConfig.get().incomingNotifications) {
            return;
        }

        ENTRIES.add(new Entry(cardName, color, amount, balance, System.currentTimeMillis()));

        while (ENTRIES.size() > MAX_SHOWN) {
            ENTRIES.removeFirst();
        }

        PaymentSound.playIncoming();
    }

    /** The notifications on screen now, oldest first. */
    public static List<Entry> shown() {
        return List.copyOf(ENTRIES);
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(entry -> now - entry.shownAt() > SHOWN_MS);

        Minecraft client = Minecraft.getInstance();

        if (ENTRIES.isEmpty()
                || client.options.hideGui
                // The editor draws its own copy, which is the one being dragged.
                || client.screen instanceof HudPositionScreen) {
            return;
        }

        int width = width(client.font, ENTRIES);
        int height = height(ENTRIES);

        render(graphics, client.font, ENTRIES,
                x(width, graphics.guiWidth()), y(height, graphics.guiHeight()), graphics.guiWidth(), now);
    }

    /** Placeholder notifications for the position editor, so there is something to drag. */
    public static List<Entry> sample() {
        return List.of(new Entry(Component.translatable("gui.spwallet.panel.example_card").getString(),
                CardColor.BLUE.argb(), 64, 1401, Long.MIN_VALUE));
    }

    public static int x(int width, int screenWidth) {
        return BalanceHud.position(SPWalletConfig.get().notificationX, width, screenWidth);
    }

    public static int y(int height, int screenHeight) {
        return BalanceHud.position(SPWalletConfig.get().notificationY, height, screenHeight);
    }

    /** The width of the widest notification, which the whole stack is laid out by. */
    public static int width(Font font, List<Entry> entries) {
        int width = 0;

        for (Entry entry : entries) {
            width = Math.max(width, entryWidth(font, entry));
        }

        return width;
    }

    public static int height(List<Entry> entries) {
        return entries.size() * entryHeight() + Math.max(0, entries.size() - 1) * GAP;
    }

    /**
     * Draws the stack at {@code x, y}, oldest on top. Each notification slides in from the nearer
     * side of the screen and back out when its time is up; one shown at {@link Long#MIN_VALUE}, as
     * the editor's are, stays put.
     */
    public static void render(GuiGraphics graphics, Font font, List<Entry> entries, int x, int y,
                              int screenWidth, long now) {
        int width = width(font, entries);
        boolean fromRight = x + width / 2 > screenWidth / 2;
        int top = y;

        for (Entry entry : entries) {
            int entryWidth = entryWidth(font, entry);
            // Lined up against the edge the stack is nearer to.
            int left = fromRight ? x + width - entryWidth : x;
            int travel = fromRight ? screenWidth - left : left + entryWidth;
            int offset = (int) ((1 - visible(entry, now)) * travel);

            drawEntry(graphics, font, entry, fromRight ? left + offset : left - offset, top, entryWidth);
            top += entryHeight() + GAP;
        }
    }

    /** How far in a notification has slid, from 0 (off screen) to 1. */
    private static float visible(Entry entry, long now) {
        if (entry.shownAt() == Long.MIN_VALUE) {
            return 1;
        }

        long age = now - entry.shownAt();
        float in = Math.min(1F, age / (float) SLIDE_MS);
        float out = Math.min(1F, (SHOWN_MS - age) / (float) SLIDE_MS);
        float t = Math.clamp(Math.min(in, out), 0F, 1F);

        // Eased, so it settles rather than stops dead.
        return 1 - (1 - t) * (1 - t);
    }

    private static void drawEntry(GuiGraphics graphics, Font font, Entry entry, int x, int y, int width) {
        int height = entryHeight();
        graphics.fill(x, y, x + width, y + height, EssentialColors.hudBackground());
        graphics.renderOutline(x, y, width, height, EssentialColors.border());

        int color = entry.color() == null ? EssentialColors.modalOutline() : entry.color();
        graphics.fill(x + PADDING, y + PADDING - 1, x + PADDING + STRIP_WIDTH, y + height - PADDING + 1, color);

        int textX = x + PADDING + STRIP_WIDTH + STRIP_GAP;
        int top = y + PADDING;

        graphics.drawString(font, title(entry), textX, top, EssentialColors.modalText(), EssentialColors.textShadow());

        Component amount = amount(entry);
        graphics.drawString(font, amount, textX, top + font.lineHeight + LINE_GAP,
                EssentialColors.tabTextSelected(), EssentialColors.textShadow());
        graphics.drawString(font, balance(entry), textX + font.width(amount) + 6, top + font.lineHeight + LINE_GAP,
                EssentialColors.cardBalance(), EssentialColors.textShadow());
    }

    private static int entryWidth(Font font, Entry entry) {
        int text = Math.max(font.width(title(entry)), font.width(amount(entry)) + 6 + font.width(balance(entry)));
        return PADDING * 2 + STRIP_WIDTH + STRIP_GAP + text;
    }

    private static int entryHeight() {
        return PADDING * 2 + 9 + LINE_GAP + 9;
    }

    private static Component title(Entry entry) {
        return Component.translatable("gui.spwallet.notification.incoming", entry.cardName());
    }

    private static Component amount(Entry entry) {
        return Component.translatable("gui.spwallet.notification.amount", entry.amount());
    }

    private static Component balance(Entry entry) {
        return Component.translatable("gui.spwallet.notification.balance", entry.balance());
    }
}
