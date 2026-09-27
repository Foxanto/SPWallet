package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.api.PlayerCard;
import ru.foxanto.spwallet.gui.EssentialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The cards of the player a transfer is addressed to, one of which has to be picked.
 *
 * <p>Starts empty and takes up no room; {@link #show} fills it in once the lookup comes back, and
 * {@link #message} puts a single line there instead, for "searching" and for failures.
 */
public class PlayerCardList extends FlowLayout {
    private final List<TransparentButton> rows = new ArrayList<>();
    private final Consumer<PlayerCard> onSelect;

    public PlayerCardList(Consumer<PlayerCard> onSelect) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.onSelect = onSelect;
        this.gap(2);
    }

    /** Replaces the contents with {@code cards}, selecting the first one. */
    public void show(List<PlayerCard> cards) {
        this.show(cards, null);
    }

    /**
     * Replaces the contents with {@code cards}, selecting the one numbered {@code preferred} and
     * the first one when there is no such card.
     *
     * <p>The preferred number is the one a sign named: when it turns out to be a player's name as
     * well, the card actually carrying that number is the one that was meant.
     */
    public void show(List<PlayerCard> cards, @Nullable String preferred) {
        this.clear();

        PlayerCard wanted = cards.stream()
                .filter(card -> card.number().equals(preferred))
                .findFirst()
                .orElse(cards.isEmpty() ? null : cards.get(0));

        for (PlayerCard card : cards) {
            TransparentButton row = new TransparentButton(
                    Component.literal(card.name() + " #" + card.number()),
                    EssentialColors.cardBalance(),
                    EssentialColors.tabTextHovered(),
                    EssentialColors.tabTextSelected(),
                    button -> {});

            row.onPress(button -> {
                for (TransparentButton other : this.rows) {
                    other.selected = false;
                }

                row.selected = true;
                this.onSelect.accept(card);
            });

            if (card == wanted) {
                row.selected = true;
                this.onSelect.accept(card);
            }

            this.rows.add(row);
            this.child(row);
        }
    }

    /** Replaces the contents with one line of text, and selects nothing. */
    public void message(Component text, int color) {
        this.clear();
        this.child(UIComponents.label(text).color(Color.ofArgb(color)));
    }

    /** Empties the list, so that nothing stale stays selected. */
    public void clear() {
        this.rows.clear();
        this.onSelect.accept(null);
        this.clearChildren();
    }
}
