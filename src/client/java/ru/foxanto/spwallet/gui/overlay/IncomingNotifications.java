package ru.foxanto.spwallet.gui.overlay;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.config.AmountFormat;
import ru.foxanto.spwallet.api.CardColor;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.gui.HudPositionScreen;
import ru.foxanto.spwallet.util.PaymentSound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Money came in" notifications, drawn on the HUD wherever the player put them - the bottom left
 * corner unless moved in the HUD position editor.
 *
 * <p>A stopgap until the SPWorlds API can list transactions: {@link ru.foxanto.spwallet.util.CardInfoCache}
 * compares each balance it fetches with the one before and reports a rise here. So a notification
 * says how much a card gained since the last check, not who sent it, and two transfers between
 * checks show up as one.
 *
 * <p>Two settings hold notifications back. Money below {@link SPWalletConfig#incomingMinAmount} is
 * not announced at all. While {@link SPWalletConfig#doNotDisturb} is on nothing is shown, played or
 * put into the chat; what comes in is summed up per card instead and shown, one notification a
 * card, when it is turned off again. Either way the journal has every rise.
 *
 * <p>The player's own transfers get one too ({@link #pushOutgoing}), with the comment cut short;
 * the journal keeps it whole. Neither setting holds those back: the player just sent it.
 */
public final class IncomingNotifications {
    private static final long SHOWN_MS = 3_000;
    /** A summary covers more than one transfer, so it stays up longer. */
    private static final long SUMMARY_SHOWN_MS = 6_000;
    /** Minecraft's own green and red, for money in and money out. */
    private static final int IN_COLOR = 0xFF55FF55;
    private static final int OUT_COLOR = 0xFFFF5555;
    private static final long SLIDE_MS = 250;
    private static final int MAX_SHOWN = 4;

    private static final int PADDING = 4;
    private static final int STRIP_WIDTH = 2;
    private static final int STRIP_GAP = 4;
    private static final int PART_GAP = 6;
    private static final int GAP = 3;
    /** How much of a comment a notification shows before cutting it off. */
    private static final int COMMENT_LENGTH = 7;

    /** What a notification is about, which decides its title. */
    public enum Source {
        /** Money that just came in. */
        LIVE("incoming"),
        /** Money that came in while the game was closed, noticed on the first check after it started. */
        AWAY("away"),
        /** Everything that came in while do not disturb was on. */
        QUIET("quiet"),
        /** A transfer the player sent. */
        OUT("outgoing");

        private final String key;

        Source(String key) {
            this.key = key;
        }
    }

    /**
     * One notification.
     *
     * @param color the card's colour, or {@code null} when it is not known yet
     * @param count how many rises it sums up, more than one only for {@link Source#QUIET}
     * @param receiver who a transfer went to, only for {@link Source#OUT}
     * @param comment the transfer's comment, whole; it is cut short when drawn
     * @param shownAt when it appeared, in {@link System#currentTimeMillis()} terms
     */
    public record Entry(String cardName, @Nullable Integer color, int amount, int balance, Source source, int count,
                        @Nullable String receiver, @Nullable String comment, long shownAt) {}

    /** What do not disturb held back for one card so far. */
    private record Held(@Nullable Integer color, int amount, int balance, int count) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    /** By card name, in the order the cards first got money. */
    private static final Map<String, Held> HELD = new LinkedHashMap<>();

    private IncomingNotifications() {}

    /** Shows that {@code amount} just came in to a card, and plays the sound for it. */
    public static void push(String cardName, @Nullable Integer color, int amount, int balance) {
        arrived(cardName, color, amount, balance, Source.LIVE);
    }

    /** Shows that {@code amount} came in to a card while the game was closed. */
    public static void pushAway(String cardName, @Nullable Integer color, int amount, int balance) {
        arrived(cardName, color, amount, balance, Source.AWAY);
    }

    private static void arrived(String cardName, @Nullable Integer color, int amount, int balance, Source source) {
        SPWalletConfig config = SPWalletConfig.get();

        if (!config.incomingNotifications) {
            return;
        }

        // Held back whatever its size: the summary is about all of it, the threshold only about
        // what is worth interrupting for.
        if (config.doNotDisturb) {
            HELD.merge(cardName, new Held(color, amount, balance, 1), (held, added) -> new Held(
                    added.color() == null ? held.color() : added.color(),
                    held.amount() + added.amount(), added.balance(), held.count() + added.count()));
            return;
        }

        if (amount < config.incomingMinAmount) {
            return;
        }

        show(List.of(new Entry(cardName, color, amount, balance, source, 1, null, null, System.currentTimeMillis())));
        PaymentSound.playIncoming();
    }

    /**
     * Shows the player's own transfer: from which card, to whom, how much and the start of the
     * comment. No sound: the transfer already played its own.
     *
     * @param receiver the card number, with the nickname after it when it was sent by nickname
     */
    public static void pushOutgoing(String cardName, @Nullable Integer color, int amount, int balance,
                                    String receiver, @Nullable String comment) {
        show(List.of(new Entry(cardName, color, amount, balance, Source.OUT, 1, receiver,
                comment == null || comment.isEmpty() ? null : comment, System.currentTimeMillis())));
    }

    /**
     * Turns do not disturb on or off and saves that. Turned off, it shows what was held back
     * meanwhile, one notification a card, and plays the sound once for all of them.
     */
    public static void doNotDisturb(boolean on) {
        SPWalletConfig config = SPWalletConfig.get();

        if (config.doNotDisturb != on) {
            config.doNotDisturb = on;
            SPWalletConfig.HANDLER.save();
        }

        if (on || HELD.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        List<Entry> summary = new ArrayList<>();

        HELD.forEach((cardName, held) -> summary.add(new Entry(cardName, held.color(), held.amount(), held.balance(),
                Source.QUIET, held.count(), null, null, now)));
        HELD.clear();

        show(summary);
        PaymentSound.playIncoming();
    }

    /** How much do not disturb is holding back, in АР, for the button that turns it off. */
    public static int held() {
        return HELD.values().stream().mapToInt(Held::amount).sum();
    }

    private static void show(List<Entry> entries) {
        ENTRIES.addAll(entries);

        while (ENTRIES.size() > MAX_SHOWN) {
            ENTRIES.removeFirst();
        }

        if (SPWalletConfig.get().incomingToChat) {
            for (Entry entry : entries) {
                Minecraft.getInstance().gui.getChat().addMessage(chatLine(entry));
            }
        }
    }

    /** The notification as one chat line, which stays after the popup is gone. */
    private static Component chatLine(Entry entry) {
        MutableComponent line = Component.literal("[SPWallet] ").withColor(EssentialColors.cardBalance())
                .append(title(entry).copy().withColor(0xFFFFFFFF))
                .append(Component.literal(" ").append(amount(entry))
                        .withColor(entry.source() == Source.OUT ? 0xFFFFFFFF : 0xFF55FF55))
                .append(Component.literal(", ").append(balance(entry)).withColor(0xFFAAAAAA));

        // The chat has the room, so the comment goes in whole there.
        if (entry.comment() != null) {
            line.append(Component.literal(" «" + entry.comment() + "»").withColor(0xFFAAAAAA));
        }

        return line;
    }

    /** Takes every notification off the screen and forgets what was held back, for the game tests. */
    public static void clear() {
        ENTRIES.clear();
        HELD.clear();
    }

    /** The notifications on screen now, oldest first. */
    public static List<Entry> shown() {
        return List.copyOf(ENTRIES);
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(entry -> now - entry.shownAt() > shownFor(entry));

        Minecraft client = Minecraft.getInstance();

        if (ENTRIES.isEmpty()
                || client.options.hideGui
                // The editor draws its own copy, which is the one being dragged.
                || client.screen instanceof HudPositionScreen
                || client.screen instanceof ru.foxanto.spwallet.gui.WalletScreen) {
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
                CardColor.BLUE.argb(), 64, 1401, Source.LIVE, 1, null, null, Long.MIN_VALUE));
    }

    /**
     * Where the stack goes across the screen. Until the player moves it ({@link
     * SPWalletConfig#notificationY} below zero) it hangs right under the favourite cards, lined up
     * with them, as one block.
     */
    public static int x(int width, int screenWidth) {
        if (followsHud()) {
            Minecraft client = Minecraft.getInstance();
            int hudWidth = hudWidth(client.font);
            int hudX = BalanceHud.x(hudWidth, screenWidth);
            // Against the edge the cards are nearer to.
            return hudX + hudWidth / 2 > screenWidth / 2 ? hudX + hudWidth - width : hudX;
        }

        return BalanceHud.position(SPWalletConfig.get().notificationX, width, screenWidth);
    }

    public static int y(int height, int screenHeight) {
        if (followsHud()) {
            int hudHeight = hudHeight();
            int hudY = BalanceHud.y(hudHeight, screenHeight);
            if (hudHeight == 0) return hudY;
            // Under the cards, or above them when they sit in the bottom half.
            return hudY + hudHeight / 2 <= screenHeight / 2 ? hudY + hudHeight + GAP : hudY - height - GAP;
        }

        return BalanceHud.position(SPWalletConfig.get().notificationY, height, screenHeight);
    }

    /**
     * The y just under the favourite cards and the notifications hanging from them, for the call
     * cards that go under both. With nothing there, the top of the screen.
     */
    public static int stackBottom(int screenWidth, int screenHeight) {
        int hudHeight = hudHeight();
        int bottom = hudHeight == 0 ? 0 : BalanceHud.y(hudHeight, screenHeight) + hudHeight;

        if (followsHud() && !ENTRIES.isEmpty()) {
            int height = height(ENTRIES);
            bottom = Math.max(bottom, y(height, screenHeight) + height);
        }

        return Math.max(bottom, 1);
    }

    private static boolean followsHud() {
        return SPWalletConfig.get().notificationY < 0;
    }

    /** The favourite cards on the HUD now, empty while there are none to show. */
    private static List<ru.foxanto.spwallet.util.CardInfoCache.Row> hudRows() {
        ru.foxanto.spwallet.util.SPServer server = ru.foxanto.spwallet.util.CardInfoCache.server();
        if (server == null || !SPWalletConfig.get().hudEnabled) return List.of();
        return ru.foxanto.spwallet.util.CardInfoCache.favouriteRows(server);
    }

    private static int hudWidth(Font font) {
        List<ru.foxanto.spwallet.util.CardInfoCache.Row> rows = hudRows();
        return rows.isEmpty() ? 0 : CardPanel.width(font, rows, BalanceHud.STYLE);
    }

    private static int hudHeight() {
        List<ru.foxanto.spwallet.util.CardInfoCache.Row> rows = hudRows();
        return rows.isEmpty() ? 0 : CardPanel.height(rows, BalanceHud.STYLE);
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
        return entries.stream().mapToInt(IncomingNotifications::entryHeight).sum()
                + Math.max(0, entries.size() - 1) * GAP;
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
            top += entryHeight(entry) + GAP;
        }
    }

    /** How far in a notification has slid, from 0 (off screen) to 1. */
    private static float visible(Entry entry, long now) {
        if (entry.shownAt() == Long.MIN_VALUE) {
            return 1;
        }

        long age = now - entry.shownAt();
        float in = Math.min(1F, age / (float) SLIDE_MS);
        float out = Math.min(1F, (shownFor(entry) - age) / (float) SLIDE_MS);
        float t = Math.clamp(Math.min(in, out), 0F, 1F);

        // Eased, so it settles rather than stops dead.
        return 1 - (1 - t) * (1 - t);
    }

    /** One line in the HUD look: the card's strip, its name, the signed amount, then what else there is. */
    private static void drawEntry(GuiGraphics graphics, Font font, Entry entry, int x, int y, int width) {
        int height = entryHeight(entry);
        graphics.fill(x, y, x + width, y + height, CardPanel.HUD_BACKGROUND);

        int color = entry.color() == null ? 0xFF808080 : entry.color();
        graphics.fill(x + PADDING, y + PADDING - 1, x + PADDING + STRIP_WIDTH, y + height - PADDING + 1, color);

        int textX = x + PADDING + STRIP_WIDTH + STRIP_GAP;
        int top = y + PADDING;

        Component name = Component.literal(entry.cardName());
        graphics.drawString(font, name, textX, top, CardPanel.HUD_TEXT, false);
        textX += font.width(name) + PART_GAP;

        Component amount = amount(entry);
        graphics.drawString(font, amount, textX, top, entry.source() == Source.OUT ? OUT_COLOR : IN_COLOR, false);
        textX += font.width(amount);

        Component extra = extra(entry);
        if (extra != null) {
            graphics.drawString(font, extra, textX + PART_GAP, top, CardPanel.HUD_DIM, false);
        }
    }

    private static long shownFor(Entry entry) {
        return entry.source() == Source.LIVE ? SHOWN_MS : SUMMARY_SHOWN_MS;
    }

    private static int entryWidth(Font font, Entry entry) {
        int text = font.width(entry.cardName()) + PART_GAP + font.width(amount(entry));
        Component extra = extra(entry);

        if (extra != null) {
            text += PART_GAP + font.width(extra);
        }

        return PADDING * 2 + STRIP_WIDTH + STRIP_GAP + text;
    }

    private static int entryHeight(Entry entry) {
        return PADDING * 2 + 8;
    }

    /** Grey words after the amount: who a transfer went to, or that it came while away or held back. */
    private static @Nullable Component extra(Entry entry) {
        return switch (entry.source()) {
            case OUT -> entry.receiver() == null ? null : Component.literal("→ " + entry.receiver());
            case AWAY -> Component.translatable("gui.spwallet.notification.short_away");
            case QUIET -> entry.count() > 1
                    ? Component.translatable("gui.spwallet.notification.short_count", entry.count())
                    : null;
            case LIVE -> null;
        };
    }

    private static Component title(Entry entry) {
        if (entry.source() == Source.OUT) {
            return Component.translatable("gui.spwallet.notification.outgoing", entry.cardName(), entry.receiver());
        }

        return Component.translatable("gui.spwallet.notification." + entry.source().key, entry.cardName());
    }

    /** The first {@link #COMMENT_LENGTH} characters of a transfer's comment, or {@code null} for none. */
    private static @Nullable Component comment(Entry entry) {
        String comment = entry.comment();

        if (comment == null) {
            return null;
        }

        // Counted in code points, so an emoji is not cut in half.
        if (comment.codePointCount(0, comment.length()) > COMMENT_LENGTH) {
            // Without a space left dangling before the dots when the cut falls between words.
            comment = comment.substring(0, comment.offsetByCodePoints(0, COMMENT_LENGTH)).stripTrailing() + "...";
        }

        return Component.literal("«" + comment + "»");
    }

    /** {@code +123 АР} or {@code −5 АР}. */
    private static Component amount(Entry entry) {
        return Component.literal(entry.source() == Source.OUT ? "−" : "+").append(AmountFormat.card(entry.amount()));
    }

    private static Component balance(Entry entry) {
        return Component.translatable("gui.spwallet.notification.balance", AmountFormat.card(entry.balance()));
    }
}
