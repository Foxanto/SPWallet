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
import ru.foxanto.spwallet.gui.component.EssentialButton;

/** A modal showing a title, a message and a single confirmation button. */
public class MessageScreen extends EssentialScreen {
    private final Component message;

    public MessageScreen(Component title, Component message) {
        super(title);
        this.message = message;
    }

    /** Replaces the current screen with this message. */
    public static void open(Component title, Component message) {
        Minecraft.getInstance().setScreen(new MessageScreen(title, message));
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
                .child(UIContainers.verticalFlow(Sizing.fill(35), Sizing.content())
                        .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                                .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                                        .child(UIComponents.label(this.title)
                                                .color(Color.ofArgb(EssentialColors.modalText()))
                                                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                                                .shadow(EssentialColors.textShadow())
                                                .horizontalSizing(Sizing.fill(100)))
                                        .child(UIComponents.label(this.message)
                                                .color(Color.ofArgb(EssentialColors.modalText()))
                                                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                                                .shadow(EssentialColors.textShadow())
                                                .horizontalSizing(Sizing.fill(100)))
                                        .gap(13))
                                .child(new EssentialButton(EssentialButton.Style.BLUE,
                                        Component.translatable("gui.spwallet.button.ok"),
                                        button -> this.onClose())
                                        .horizontalSizing(Sizing.fill(100)))
                                .gap(18)
                                .margins(Insets.of(17)))
                        .surface(Surface.flat(EssentialColors.background())
                                .and(Surface.outline(EssentialColors.modalOutline()))))
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER)
                .surface(Surface.VANILLA_TRANSLUCENT);
    }
}
