package ru.foxanto.spwallet.gui;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.gui.component.EssentialButton;
import ru.foxanto.spwallet.util.SPServer;

/** Asks whether a card the server just announced in chat should be saved. */
public class AddCardScreen extends EssentialScreen {
    private final SPServer server;
    private final Card card;

    public AddCardScreen(SPServer server, Card card) {
        super(Component.translatable("gui.spwallet.title.add_card"));
        this.server = server;
        this.card = card;
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
                .child(UIContainers.verticalFlow(Sizing.fill(35), Sizing.content())
                        .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                                .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                                        .child(UIComponents.label(this.title)
                                                .color(Color.ofArgb(EssentialColors.MODAL_TEXT))
                                                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                                                .shadow(true)
                                                .horizontalSizing(Sizing.fill(100)))
                                        .child(UIComponents.label(Component
                                                        .translatable("gui.spwallet.description.want_add_card")
                                                        .append("\n" + this.card.name() + "?"))
                                                .color(Color.ofArgb(EssentialColors.MODAL_TEXT))
                                                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                                                .shadow(true)
                                                .horizontalSizing(Sizing.fill(100)))
                                        .gap(13))
                                .child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                                        .child(new EssentialButton(EssentialButton.Style.NEUTRAL,
                                                Component.translatable("gui.spwallet.button.no"),
                                                button -> this.onClose())
                                                .horizontalSizing(Sizing.fill(47)))
                                        .child(new EssentialButton(EssentialButton.Style.BLUE,
                                                Component.translatable("gui.spwallet.button.yes"),
                                                button -> {
                                                    SPWalletClient.cards().add(this.server, this.card);
                                                    this.onClose();
                                                })
                                                .horizontalSizing(Sizing.fill(47)))
                                        .gap(8)
                                        .horizontalAlignment(HorizontalAlignment.CENTER))
                                .gap(18)
                                .margins(Insets.of(17)))
                        .surface(Surface.flat(EssentialColors.BACKGROUND)
                                .and(Surface.outline(EssentialColors.MODAL_OUTLINE))))
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER)
                .surface(Surface.VANILLA_TRANSLUCENT);
    }
}
