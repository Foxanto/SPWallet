package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.gui.EssentialColors;

import java.util.function.Consumer;

/** One entry of the card list: the card's name, its balance and a delete button. */
public class CardButton extends FlowLayout {
    /** U+1F5D1 WASTEBASKET, provided by the mod's font entry in assets/minecraft/font/default.json. */
    private static final String DELETE_ICON = "🗑";

    /** Width of the strip along the left edge that shows the card's colour. */
    private static final int COLOR_STRIP_WIDTH = 3;

    public final Card card;

    private final CardNumberButton numberButton = new CardNumberButton();

    /** The card's colour, which is only known once {@code /accounts/me} has answered. */
    private int color = EssentialColors.BORDER;

    public boolean selected = false;
    private Consumer<Card> onPress = card -> {};

    public CardButton(Card card, Consumer<Card> onDelete) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);

        this.card = card;

        TransparentButton deleteButton = new TransparentButton(Component.literal(DELETE_ICON),
                EssentialColors.TAB_TEXT,
                EssentialColors.MODAL_TEXT,
                EssentialColors.ERROR,
                button -> onDelete.accept(this.card));
        deleteButton.tooltip(Component.translatable("gui.spwallet.description.delete_card.tooltip"));

        this.child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                        .child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                                .child(UIComponents.label(Component.literal(card.name()))
                                        .color(Color.ofArgb(EssentialColors.MODAL_TEXT))
                                        .shadow(true))
                                .child(this.numberButton)
                                // Eats the leftover width, which pins the delete button to the
                                // right edge instead of letting a long name push it out of view.
                                // The height has to be pinned: a spacer expands on both axes.
                                .child(UIComponents.spacer().verticalSizing(Sizing.fixed(0)))
                                .child(deleteButton)
                                .gap(4))
                        .child(new CardBalanceLabel(card)
                                .color(Color.ofArgb(EssentialColors.CARD_BALANCE))
                                .shadow(false))
                        .gap(4)
                        .margins(Insets.top(2))
                        .verticalAlignment(VerticalAlignment.CENTER))
                .margins(Insets.both(10, 8)));
    }

    public CardButton onPress(Consumer<Card> onPress) {
        this.onPress = onPress;
        return this;
    }

    /** Shows the card's number, once {@link CardList} has looked it up. */
    public void number(String number) {
        this.numberButton.number(number);
    }

    /** Colours the strip on the left, once {@link CardList} has looked the card up. */
    public void color(@Nullable Integer argb) {
        if (argb != null) {
            this.color = argb;
        }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        this.onPress.accept(this.card);
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

        return super.onMouseDown(click, doubled);
    }

    @Override
    public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.selected || this.hovered) {
            this.surface(Surface.flat(EssentialColors.BORDER));
        } else {
            this.surface(Surface.flat(EssentialColors.BACKGROUND));
        }

        super.draw(context, mouseX, mouseY, partialTicks, delta);

        // Drawn rather than laid out: a child cannot take the row's height, which comes from the
        // text next to it.
        context.fill(this.x, this.y, this.x + COLOR_STRIP_WIDTH, this.y + this.height, this.color);
    }
}
