package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import ru.foxanto.spwallet.gui.EssentialColors;

/** A borderless text box on a flat panel, with a greyed out placeholder. */
public class EssentialTextBox extends FlowLayout {
    public final TextBoxComponent textBox;
    private final MutableComponent placeholder;

    public EssentialTextBox(Sizing horizontalSizing, MutableComponent placeholder) {
        super(horizontalSizing, Sizing.content(), Algorithm.VERTICAL);

        this.placeholder = placeholder;
        this.textBox = UIComponents.textBox(horizontalSizing);
        this.textBox.setBordered(false);
        this.textBox.setTextColor(EssentialColors.inputText());
        this.textBox.setTextShadow(EssentialColors.textShadow());
        this.textBox.margins(Insets.of(10, 9, 9, 9));

        this.surface(Surface.flat(EssentialColors.border()));
        this.child(this.textBox);
    }

    public String value() {
        return this.textBox.getValue();
    }

    /**
     * Draws the placeholder where the box's own hint would go. The hint is not used because the
     * game always draws it with a shadow, which smears it on the light theme.
     */
    @Override
    public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(context, mouseX, mouseY, partialTicks, delta);

        if (this.textBox.getValue().isEmpty() && !this.textBox.isFocused()) {
            context.drawString(Minecraft.getInstance().font, this.placeholder,
                    this.textBox.getX(), this.textBox.getY(),
                    EssentialColors.inputPlaceholder(), EssentialColors.textShadow());
        }
    }
}
