package ru.foxanto.spwallet.gui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.gui.EssentialColors;

/**
 * The card's number next to its name. Clicking it puts the number on the clipboard.
 *
 * <p>The number is not part of a saved card, it arrives from {@code /accounts/me} after the screen
 * is already up, so this starts out empty and takes up no room until {@link #number} is called.
 */
public class CardNumberButton extends TransparentButton {
    private String number = null;

    public CardNumberButton() {
        super(Component.empty(),
                EssentialColors.cardBalance(),
                EssentialColors.tabTextHovered(),
                EssentialColors.tabTextHovered(),
                button -> {});

        this.shadow(false);
        this.onPress(button -> this.copy());
    }

    /** Shows {@code number}, relaying out the row around it. */
    public void number(String number) {
        this.number = number;
        this.text(Component.literal("#" + number));
        this.tooltip(Component.translatable("gui.spwallet.description.copy_card_number"));
    }

    private void copy() {
        if (this.number == null) {
            return;
        }

        Minecraft.getInstance().keyboardHandler.setClipboard(this.number);
        this.tooltip(Component.translatable("gui.spwallet.description.card_number_copied"));
    }
}
