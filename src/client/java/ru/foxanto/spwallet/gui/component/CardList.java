package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.api.AccountCard;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.api.SPWorldsApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The list of saved cards for one server, with exactly one card selected at a time. */
public class CardList extends FlowLayout {
    private final List<CardButton> buttons = new ArrayList<>();

    public CardList(List<Card> cards, Consumer<Card> onSelect, Consumer<Card> onDelete) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);

        // Padding, not a margin: a margin on a fill(100) layout pushes it past the
        // scroll viewport and clips whatever sits at the right edge of a card row.
        this.padding(Insets.right(3));
        this.child(UIComponents.box(Sizing.fill(100), Sizing.fixed(5)).color(Color.ofArgb(0)));

        for (Card card : cards) {
            CardButton button = new CardButton(card, onDelete);

            button.onPress(selected -> {
                for (CardButton other : this.buttons) {
                    other.selected = false;
                }

                button.selected = true;
                onSelect.accept(selected);
            });

            if (this.buttons.isEmpty()) {
                button.selected = true;
                onSelect.accept(card);
            }

            this.buttons.add(button);
            this.child(button);
        }

        this.loadCardNumbers(cards);
    }

    /**
     * Fills in the card numbers and colours.
     *
     * <p>{@code /accounts/me} answers with every card of the account, so one request with any saved
     * card's token covers the whole list.
     */
    private void loadCardNumbers(List<Card> cards) {
        if (cards.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        SPWorldsApi.accountCards(cards.get(0))
                .thenAcceptAsync(accountCards -> {
                    for (CardButton button : this.buttons) {
                        AccountCard accountCard = accountCards.get(button.card.id());

                        if (accountCard != null) {
                            button.number(accountCard.number());
                            button.color(accountCard.color());
                        }
                    }
                }, client)
                .exceptionally(throwable -> {
                    // Not worth bothering the player about: the row simply stays without a number
                    // and keeps the neutral strip.
                    SPWallet.LOGGER.warn("Could not read the card numbers of the account", throwable);
                    return null;
                });
    }
}
