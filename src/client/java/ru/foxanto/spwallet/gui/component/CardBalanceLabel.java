package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.api.SPWorldsApi;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.util.CardInfoCache;

/** Shows a card's balance, replacing the placeholder once the API answers. */
public class CardBalanceLabel extends LabelComponent {
    public CardBalanceLabel(Card card) {
        super(Component.translatable("gui.spwallet.description.balance").append("..."));

        Minecraft client = Minecraft.getInstance();
        long requestedAt = System.currentTimeMillis();

        SPWorldsApi.balance(card)
                .thenAcceptAsync(balance -> {
                    // The HUD shows the same balance; no point in it asking again. Passed on as a
                    // check rather than a change, so money that came in since the last check is
                    // still noticed instead of quietly becoming the new baseline.
                    CardInfoCache.balanceFetched(card, balance, requestedAt);
                    this.text(Component.translatable("gui.spwallet.description.balance")
                            .append(String.valueOf(balance)));
                }, client)
                .exceptionallyAsync(throwable -> {
                    SPWallet.LOGGER.warn("Could not read the balance of card {}", card.id(), throwable);

                    this.text(Component.translatable("gui.spwallet.description.balance_error"));
                    this.color(Color.ofArgb(EssentialColors.error()));

                    return null;
                }, client);
    }
}
