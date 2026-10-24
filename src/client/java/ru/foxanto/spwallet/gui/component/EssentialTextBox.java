package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.gui.EssentialColors;

import java.util.function.Supplier;

/** A borderless text box on a flat panel, with a greyed out placeholder. */
public class EssentialTextBox extends FlowLayout {
    /** Room kept between what was typed and the suffix; with less the suffix is left out. */
    private static final int SUFFIX_GAP = 6;

    /** A note drawn at the right end of the box, such as the amount in the other format. */
    public record Suffix(Component text, int color) {}

    public final TextBoxComponent textBox;
    private final MutableComponent placeholder;
    private Supplier<@Nullable Suffix> suffix = () -> null;

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
     * Shows what {@code suffix} gives at the right end of the box, asked again every frame so it
     * follows what is typed; {@code null} shows nothing.
     */
    public EssentialTextBox suffix(Supplier<@Nullable Suffix> suffix) {
        this.suffix = suffix;
        return this;
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

        Suffix suffix = this.suffix.get();

        if (suffix != null) {
            Font font = Minecraft.getInstance().font;
            int x = this.textBox.getX() + this.textBox.width() - font.width(suffix.text());

            // Over the text it would be unreadable, and the text matters more.
            if (this.textBox.getX() + font.width(this.textBox.getValue()) + SUFFIX_GAP <= x) {
                context.drawString(font, suffix.text(), x, this.textBox.getY(),
                        suffix.color(), EssentialColors.textShadow());
            }
        }
    }
}
