package ru.foxanto.spwallet.gui.overlay;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.util.CardInfoCache.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a list of cards as a small panel: a colour strip, the name, optionally the number, and the
 * balance on the right. The HUD, the inventory and the HUD position editor all draw it the same way.
 *
 * <p>Plain {@link GuiGraphics} calls rather than owo components: the HUD has no screen to host a
 * component tree, and the panel is simple enough not to need one.
 */
public final class CardPanel {
    public static final int PADDING = 4;
    private static final int STRIP_WIDTH = 2;
    private static final int STRIP_GAP = 4;
    private static final int ROW_HEIGHT = 9;
    private static final int ROW_GAP = 3;
    private static final int COLUMN_GAP = 8;
    /** Header height, the header's own line plus the gap under it. */
    private static final int HEADER_HEIGHT = 9 + 5;

    /** The mark in front of a card that says whether it is on the HUD. */
    private static final String FAVOURITE = "★";
    private static final String NOT_FAVOURITE = "☆";
    private static final int FAVOURITE_GAP = 4;

    /** A rectangle on screen, for whoever has to tell whether it was clicked. */
    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;
        }

        public boolean intersects(int x, int y, int width, int height) {
            return x < this.x + this.width && x + width > this.x
                    && y < this.y + this.height && y + height > this.y;
        }
    }

    /**
     * What to draw.
     *
     * @param header a title line above the cards, or {@code null} for none
     * @param headerReserve width kept free at the right of the header, for buttons drawn over it
     * @param showNumbers whether to show each card's number after its name
     * @param favourites whether each row carries the star that puts the card on the HUD
     */
    public record Style(@Nullable Component header, int headerReserve, boolean showNumbers,
                        boolean favourites) {}

    private CardPanel() {}

    public static int width(Font font, List<Row> rows, Style style) {
        int left = 0;
        int right = 0;

        for (Row row : rows) {
            left = Math.max(left, font.width(label(row, style)));
            right = Math.max(right, font.width(balance(row)));
        }

        int content = favouriteWidth(font, style) + STRIP_WIDTH + STRIP_GAP + left + COLUMN_GAP + right;

        if (style.header() != null) {
            content = Math.max(content, font.width(style.header()) + COLUMN_GAP + style.headerReserve());
        }

        return content + PADDING * 2;
    }

    public static int height(List<Row> rows, Style style) {
        int content = rows.size() * ROW_HEIGHT + Math.max(0, rows.size() - 1) * ROW_GAP;

        if (style.header() != null) {
            content += HEADER_HEIGHT;
        }

        return content + PADDING * 2;
    }

    /** The y of the header line, for whoever draws buttons into it. */
    public static int headerY(int y) {
        return y + PADDING;
    }

    public static void render(GuiGraphics graphics, Font font, List<Row> rows, Style style, int x, int y) {
        // No mouse to speak of on the HUD, so nothing can be hovered.
        render(graphics, font, rows, style, x, y, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    /**
     * Draws the panel and hands back where each row ended up, in the order the rows were given.
     *
     * <p>Only the panel that takes clicks has any use for that; the rest call
     * {@link #render(GuiGraphics, Font, List, Style, int, int)} and ignore it.
     */
    public static List<Bounds> render(GuiGraphics graphics, Font font, List<Row> rows, Style style,
                                      int x, int y, int mouseX, int mouseY) {
        int width = width(font, rows, style);
        int height = height(rows, style);

        graphics.fill(x, y, x + width, y + height, EssentialColors.hudBackground());
        graphics.renderOutline(x, y, width, height, EssentialColors.border());

        int top = y + PADDING;

        if (style.header() != null) {
            graphics.drawString(font, style.header(), x + PADDING, top, EssentialColors.screenTitle(), EssentialColors.textShadow());
            top += HEADER_HEIGHT;
        }

        List<Bounds> bounds = new ArrayList<>(rows.size());
        int left = x + PADDING + favouriteWidth(font, style);

        for (Row row : rows) {
            Bounds rowBounds = new Bounds(x + PADDING, top - 1, width - PADDING * 2, ROW_HEIGHT + 1);
            boolean hovered = style.favourites() && rowBounds.contains(mouseX, mouseY);

            if (hovered) {
                graphics.fill(rowBounds.x(), rowBounds.y(), rowBounds.x() + rowBounds.width(),
                        rowBounds.y() + rowBounds.height(), EssentialColors.rowHover());
            }

            if (style.favourites()) {
                String mark = row.favourite() ? FAVOURITE : NOT_FAVOURITE;
                int color = row.favourite()
                        ? EssentialColors.tabTextSelected()
                        : hovered ? EssentialColors.tabTextHovered() : EssentialColors.cardBalance();

                graphics.drawString(font, mark, x + PADDING, top, color, EssentialColors.textShadow());
            }

            int color = row.color() == null ? EssentialColors.modalOutline() : row.color();
            graphics.fill(left, top - 1, left + STRIP_WIDTH, top + ROW_HEIGHT, color);

            graphics.drawString(font, label(row, style), left + STRIP_WIDTH + STRIP_GAP, top,
                    EssentialColors.modalText(), EssentialColors.textShadow());

            Component balance = balance(row);
            graphics.drawString(font, balance, x + width - PADDING - font.width(balance), top,
                    row.failed() ? EssentialColors.error() : EssentialColors.tabText(), EssentialColors.textShadow());

            bounds.add(rowBounds);
            top += ROW_HEIGHT + ROW_GAP;
        }

        return bounds;
    }

    /** Room taken by the star column, which the two marks share so rows never shift about. */
    private static int favouriteWidth(Font font, Style style) {
        if (!style.favourites()) {
            return 0;
        }

        return Math.max(font.width(FAVOURITE), font.width(NOT_FAVOURITE)) + FAVOURITE_GAP;
    }

    private static Component label(Row row, Style style) {
        if (!style.showNumbers() || row.number() == null) {
            return Component.literal(row.name());
        }

        return Component.literal(row.name())
                .append(Component.literal(" #" + row.number()).withColor(EssentialColors.cardBalance()));
    }

    private static Component balance(Row row) {
        if (row.balance() != null) {
            return Component.translatable("gui.spwallet.panel.balance", row.balance());
        }

        return Component.literal(row.failed() ? "—" : "…");
    }
}
