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
import ru.foxanto.spwallet.api.PlayerCard;
import ru.foxanto.spwallet.gui.EssentialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The receiver's card, in a field like the text boxes: «Абубамбе | 06156». With more than one card
 * the arrows at its end (or a click on it) go through them. Before a lookup, or when it fails, the
 * field says so in grey or red instead.
 */
public class TargetPicker extends BaseUIComponent {
    public static final int HEIGHT = 26;

    private final Consumer<@Nullable PlayerCard> onSelect;
    private final List<PlayerCard> cards = new ArrayList<>();
    private int index;
    private @Nullable Component message;
    private int messageColor;

    public TargetPicker(Sizing horizontal, Consumer<@Nullable PlayerCard> onSelect) {
        this.onSelect = onSelect;
        this.sizing(horizontal, Sizing.fixed(HEIGHT));
        this.message(Component.translatable("gui.spwallet.picker.hint"), EssentialColors.inputPlaceholder());
    }

    /** Shows {@code cards}, picking the one numbered {@code preferred}, or the first. */
    public void show(List<PlayerCard> cards, @Nullable String preferred) {
        this.cards.clear();
        this.cards.addAll(cards);
        this.message = null;
        this.index = 0;
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).number().equals(preferred)) this.index = i;
        }
        this.onSelect.accept(cards.isEmpty() ? null : cards.get(this.index));
    }

    public void message(Component text, int color) {
        this.cards.clear();
        this.message = text;
        this.messageColor = color;
        this.onSelect.accept(null);
    }

    public void clear() {
        this.message(Component.translatable("gui.spwallet.picker.hint"), EssentialColors.inputPlaceholder());
    }

    /** How many cards the receiver has, once looked up. */
    public int count() {
        return this.cards.size();
    }

    @Override
    public void draw(OwoUIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
        Font font = Minecraft.getInstance().font;
        EssentialTextBox.drawField(g, this.x, this.y, this.width, this.height, this.hovered && this.cards.size() > 1);
        int textY = this.y + (this.height - 8) / 2;
        int left = this.x + 9;

        if (this.message != null) {
            g.drawString(font, font.plainSubstrByWidth(this.message.getString(), this.width - 18), left, textY,
                    this.messageColor, EssentialColors.textShadow());
            return;
        }

        PlayerCard card = this.cards.get(this.index);
        String arrows = this.cards.size() > 1 ? "‹ " + (this.index + 1) + "/" + this.cards.size() + " ›" : "";
        int arrowsWidth = arrows.isEmpty() ? 0 : font.width(arrows) + 6;
        int numberWidth = font.width(card.number());
        int nameRoom = this.width - 18 - arrowsWidth - numberWidth - 10;

        g.drawString(font, font.plainSubstrByWidth(card.name(), nameRoom), left, textY,
                EssentialColors.inputText(), EssentialColors.textShadow());
        int numberX = this.x + this.width - 9 - arrowsWidth - numberWidth;
        g.fill(numberX - 5, textY - 1, numberX - 4, textY + 9, EssentialColors.success());
        g.drawString(font, card.number(), numberX, textY, EssentialColors.inputPlaceholder(), EssentialColors.textShadow());
        if (!arrows.isEmpty()) {
            g.drawString(font, arrows, this.x + this.width - 9 - font.width(arrows), textY,
                    EssentialColors.tabText(), EssentialColors.textShadow());
        }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (this.cards.size() < 2) return false;
        // The left third goes back, the rest forward.
        boolean back = click.x() < this.width / 3.0;
        this.index = Math.floorMod(this.index + (back ? -1 : 1), this.cards.size());
        this.onSelect.accept(this.cards.get(this.index));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return HEIGHT;
    }
}
