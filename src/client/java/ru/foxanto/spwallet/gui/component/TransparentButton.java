package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Consumer;

/** A label that behaves like a button: no background, only the text colour reacts. */
public class TransparentButton extends LabelComponent {
    private final Color textColor;
    private final Color hoverColor;
    private final Color selectedColor;

    public boolean selected = false;
    private Consumer<TransparentButton> onPress;

    public TransparentButton(Component message, int textColor, int hoverColor, int selectedColor,
                             Consumer<TransparentButton> onPress) {
        super(message);

        this.textColor = Color.ofArgb(textColor);
        this.hoverColor = Color.ofArgb(hoverColor);
        this.selectedColor = Color.ofArgb(selectedColor);
        this.onPress = onPress;
    }

    public TransparentButton onPress(Consumer<TransparentButton> onPress) {
        this.onPress = onPress;
        return this;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        this.onPress.accept(this);
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

        return super.onMouseDown(click, doubled);
    }

    @Override
    public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.selected) {
            this.color(this.selectedColor);
        } else if (this.hovered) {
            this.color(this.hoverColor);
        } else {
            this.color(this.textColor);
        }

        super.draw(context, mouseX, mouseY, partialTicks, delta);
    }
}
