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
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.gui.EssentialColors;

import java.util.function.Consumer;

/**
 * One entry of the card list: the card's name, its balance, a star and a delete button.
 *
 * <p>The star is the same one the inventory panel carries: it decides whether the card is drawn on
 * the HUD while playing.
 */
public class CardButton extends FlowLayout {
    /** U+1F5D1 WASTEBASKET, provided by the mod's font entry in assets/minecraft/font/default.json. */
    private static final String DELETE_ICON = "🗑";

    private static final String FAVOURITE_ICON = "★";
    private static final String NOT_FAVOURITE_ICON = "☆";

    /** Width of the strip along the left edge that shows the card's colour. */
    private static final int COLOR_STRIP_WIDTH = 3;

    public final Card card;

    private final CardNumberButton numberButton = new CardNumberButton();

    /** The card's colour, which is only known once {@code /accounts/me} has answered. */
    private int color = EssentialColors.border();

    public boolean selected = false;
    private Consumer<Card> onPress = card -> {};

    public CardButton(Card card, Consumer<Card> onDelete) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);

        this.card = card;

        TransparentButton favouriteButton = new TransparentButton(Component.literal(NOT_FAVOURITE_ICON),
                EssentialColors.tabText(),
                EssentialColors.modalText(),
                EssentialColors.tabTextSelected(),
                button -> {});
        favourite(favouriteButton, SPWalletClient.cards().isFavourite(card.id()));
        favouriteButton.onPress(button ->
                favourite(button, SPWalletClient.cards().toggleFavourite(card.id())));

        TransparentButton deleteButton = new TransparentButton(Component.literal(DELETE_ICON),
                EssentialColors.tabText(),
                EssentialColors.modalText(),
                EssentialColors.error(),
                button -> onDelete.accept(this.card));
        deleteButton.tooltip(Component.translatable("gui.spwallet.description.delete_card.tooltip"));

        this.child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                        .child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                                .child(UIComponents.label(Component.literal(card.name()))
                                        .color(Color.ofArgb(EssentialColors.modalText()))
                                        .shadow(EssentialColors.textShadow()))
                                .child(this.numberButton)
                                // Eats the leftover width, which pins the delete button to the
                                // right edge instead of letting a long name push it out of view.
                                // The height has to be pinned: a spacer expands on both axes.
                                .child(UIComponents.spacer().verticalSizing(Sizing.fixed(0)))
                                .child(favouriteButton)
                                .child(deleteButton)
                                .gap(4))
                        .child(new CardBalanceLabel(card)
                                .color(Color.ofArgb(EssentialColors.cardBalance()))
                                .shadow(false))
                        .gap(4)
                        .margins(Insets.top(2))
                        .verticalAlignment(VerticalAlignment.CENTER))
                .margins(Insets.both(10, 8)));
    }

    /** Puts the star into the state {@code favourite} and says what pressing it would do. */
    private static void favourite(TransparentButton button, boolean favourite) {
        button.selected = favourite;
        button.text(Component.literal(favourite ? FAVOURITE_ICON : NOT_FAVOURITE_ICON));
        button.tooltip(Component.translatable(favourite
                ? "gui.spwallet.panel.unfavourite"
                : "gui.spwallet.panel.favourite"));
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
            this.surface(Surface.flat(EssentialColors.border()));
        } else {
            this.surface(Surface.flat(EssentialColors.background()));
        }

        super.draw(context, mouseX, mouseY, partialTicks, delta);

        // Drawn rather than laid out: a child cannot take the row's height, which comes from the
        // text next to it.
        context.fill(this.x, this.y, this.x + COLOR_STRIP_WIDTH, this.y + this.height, this.color);
    }
}
