package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.network.chat.MutableComponent;
import ru.foxanto.spwallet.gui.EssentialColors;

/** A borderless text box on a flat panel, with a greyed out placeholder. */
public class EssentialTextBox extends FlowLayout {
    public final TextBoxComponent textBox;

    public EssentialTextBox(Sizing horizontalSizing, MutableComponent placeholder) {
        super(horizontalSizing, Sizing.content(), Algorithm.VERTICAL);

        this.textBox = UIComponents.textBox(horizontalSizing);
        this.textBox.setBordered(false);
        this.textBox.setHint(placeholder.withStyle(style -> style.withColor(EssentialColors.INPUT_PLACEHOLDER)));
        this.textBox.setTextColor(EssentialColors.INPUT_TEXT);
        this.textBox.margins(Insets.of(10, 9, 9, 9));

        this.surface(Surface.flat(EssentialColors.BORDER));
        this.child(this.textBox);
    }

    public String value() {
        return this.textBox.getValue();
    }
}
