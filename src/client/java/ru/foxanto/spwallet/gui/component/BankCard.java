package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.config.AmountFormat;
import ru.foxanto.spwallet.gui.Shapes;
import ru.foxanto.spwallet.util.CardInfoCache;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One saved card drawn like a bank card: its colour, the name, a big balance, the balance in stacks
 * underneath and the number in the corner. The selected card slides out of the stack to the left,
 * the hovered one half way; the square in the top corner opens the card's menu.
 */
public class BankCard extends BaseUIComponent {
    public static final int HEIGHT = 62;
    /** How far the selected card stands out of the stack, and the hovered one half of it. */
    public static final int SLIDE = 14;
    private static final int RADIUS = 5;
    private static final int MENU_SIZE = 9;
    private static final int NEUTRAL = 0xFF5B6475;

    public final Card card;
    private final Consumer<BankCard> onSelect;
    private final BiConsumer<BankCard, int[]> onMenu;
    private @Nullable CardInfoCache.Row row;
    public boolean selected;
    private float slide;

    public BankCard(Card card, int width, Consumer<BankCard> onSelect, BiConsumer<BankCard, int[]> onMenu) {
        this.card = card;
        this.onSelect = onSelect;
        this.onMenu = onMenu;
        this.sizing(Sizing.fixed(width + SLIDE), Sizing.fixed(HEIGHT));
    }

    /** The card's latest number, colour and balance, as the stack read them this frame. */
    public void row(@Nullable CardInfoCache.Row row) {
        this.row = row;
    }

    private int cardWidth() {
        return this.width - SLIDE;
    }

    @Override
    public void draw(OwoUIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
        Font font = Minecraft.getInstance().font;
        float target = this.selected ? 1F : this.hovered ? 0.5F : 0F;
        this.slide += (target - this.slide) * Math.min(1F, delta * 0.45F);
        int x = this.x + SLIDE - Math.round(this.slide * SLIDE);
        int y = this.y;
        int w = this.cardWidth();
        int h = this.height;

        int color = this.row != null && this.row.color() != null ? this.row.color() : NEUTRAL;
        // A soft shadow under the card, then the card, then a lighter sweep across its lower right.
        Shapes.rounded(g, x + 1, y + 2, w, h, RADIUS, 0x40000000);
        Shapes.rounded(g, x, y, w, h, RADIUS, color);
        for (int i = 0; i < h - 4; i++) {
            int from = x + Math.round(w * 0.58F - i * 0.55F);
            g.fill(Math.max(x + 3, from), y + 2 + i, x + w - 3, y + 3 + i, 0x14FFFFFF);
        }

        int text = 0xFFFFFFFF;
        g.drawString(font, font.plainSubstrByWidth(this.card.name(), w - MENU_SIZE - 16), x + 7, y + 6, text, true);

        // The menu square: brighter under the mouse.
        int mx = x + w - MENU_SIZE - 6;
        int my = y + 6;
        boolean overMenu = mouseX >= mx && mouseX < mx + MENU_SIZE && mouseY >= my && mouseY < my + MENU_SIZE;
        Shapes.rounded(g, mx, my, MENU_SIZE, MENU_SIZE, 2, overMenu ? 0xF0FFFFFF : 0xB8FFFFFF);
        g.fill(mx + 2, my + 3, mx + MENU_SIZE - 2, my + 4, color);
        g.fill(mx + 2, my + 5, mx + MENU_SIZE - 2, my + 6, color);

        Integer balance = this.row == null ? null : this.row.balance();
        Component big = balance != null
                ? AmountFormat.TOTAL.format(balance)
                : Component.literal(this.row != null && this.row.failed() ? "—" : "…");
        // Twice the size, unless that would run past the card's edge.
        float scale = Math.min(2F, (w - 14) / (float) Math.max(1, font.width(big)));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + 7, y + 20 + (2F - scale) * 4);
        pose.scale(scale, scale);
        g.drawString(font, big, 0, 0, text, true);
        pose.popMatrix();

        String number = this.row == null || this.row.number() == null ? "" : this.row.number();
        int numberWidth = font.width(number);
        g.drawString(font, number, x + w - 7 - numberWidth, y + h - 13, 0xF0FFFFFF, true);

        // The balance in stacks, where it fits beside the number.
        if (balance != null && balance >= AmountFormat.STACK) {
            Component stacks = AmountFormat.STACKS.format(balance);
            if (font.width(stacks) + numberWidth + 20 <= w) {
                g.drawString(font, stacks, x + 7, y + h - 13, 0xD8FFFFFF, false);
            }
        }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        int w = this.cardWidth();
        int offset = SLIDE - Math.round(this.slide * SLIDE);
        double cx = click.x() - offset;
        int mx = w - MENU_SIZE - 6;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

        if (cx >= mx - 2 && cx < mx + MENU_SIZE + 2 && click.y() >= 4 && click.y() < 6 + MENU_SIZE + 2) {
            this.onMenu.accept(this, new int[] {this.x + offset + mx, this.y + 6 + MENU_SIZE + 2});
        } else {
            this.onSelect.accept(this);
        }
        return true;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return this.width;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return HEIGHT;
    }
}
