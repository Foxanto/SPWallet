package ru.foxanto.spwallet.gui.overlay;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.util.CardInfoCache.Row;

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

    /** Slightly see-through, so a HUD panel does not hide the world behind it outright. */
    private static final int BACKGROUND = 0xD8181818;

    /**
     * What to draw.
     *
     * @param header a title line above the cards, or {@code null} for none
     * @param headerReserve width kept free at the right of the header, for buttons drawn over it
     * @param showNumbers whether to show each card's number after its name
     */
    public record Style(@Nullable Component header, int headerReserve, boolean showNumbers) {}

    private CardPanel() {}

    public static int width(Font font, List<Row> rows, Style style) {
        int left = 0;
        int right = 0;

        for (Row row : rows) {
            left = Math.max(left, font.width(label(row, style)));
            right = Math.max(right, font.width(balance(row)));
        }

        int content = STRIP_WIDTH + STRIP_GAP + left + COLUMN_GAP + right;

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
        int width = width(font, rows, style);
        int height = height(rows, style);

        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        graphics.renderOutline(x, y, width, height, EssentialColors.BORDER);

        int top = y + PADDING;

        if (style.header() != null) {
            graphics.drawString(font, style.header(), x + PADDING, top, EssentialColors.SCREEN_TITLE, true);
            top += HEADER_HEIGHT;
        }

        for (Row row : rows) {
            int color = row.color() == null ? EssentialColors.MODAL_OUTLINE : row.color();
            graphics.fill(x + PADDING, top - 1, x + PADDING + STRIP_WIDTH, top + ROW_HEIGHT, color);

            graphics.drawString(font, label(row, style), x + PADDING + STRIP_WIDTH + STRIP_GAP, top,
                    EssentialColors.MODAL_TEXT, true);

            Component balance = balance(row);
            graphics.drawString(font, balance, x + width - PADDING - font.width(balance), top,
                    row.failed() ? EssentialColors.ERROR : EssentialColors.TAB_TEXT, true);

            top += ROW_HEIGHT + ROW_GAP;
        }
    }

    private static Component label(Row row, Style style) {
        if (!style.showNumbers() || row.number() == null) {
            return Component.literal(row.name());
        }

        return Component.literal(row.name())
                .append(Component.literal(" #" + row.number()).withColor(EssentialColors.CARD_BALANCE));
    }

    private static Component balance(Row row) {
        if (row.balance() != null) {
            return Component.translatable("gui.spwallet.panel.balance", row.balance());
        }

        return Component.literal(row.failed() ? "—" : "…");
    }
}
