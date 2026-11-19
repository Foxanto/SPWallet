package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.ButtonComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.gui.Shapes;

import java.util.function.Consumer;

/** A rounded, filled button — green «Отправить» under the transfer form, like a bank app's main action. */
public class PillButton extends ButtonComponent {
    public static final int GREEN = 0xFF2FA85A;
    public static final int BLUE = 0xFF2C7BE5;

    private final int color;
    private int radius = -1;
    private int textColor = 0xFFFFFFFF;

    public PillButton(Component message, int color, Consumer<ButtonComponent> onPress) {
        super(message, onPress);
        this.color = color;
    }

    /** Corner radius; by default half the height, which makes the ends round. */
    public PillButton radius(int radius) {
        this.radius = radius;
        return this;
    }

    public PillButton textColor(int color) {
        this.textColor = color;
        return this;
    }

    @Override
    public void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int fill = !this.active
                ? (EssentialColors.light() ? 0xFFB0B0B0 : 0xFF3A3A3A)
                : this.isHovered() ? Shapes.lighter(this.color, 0.12F) : this.color;
        Shapes.rounded(g, this.getX(), this.getY(), this.width, this.height,
                this.radius < 0 ? this.height / 2 : this.radius, fill);

        Font font = Minecraft.getInstance().font;
        Component message = this.getMessage();
        int text = this.active ? this.textColor : (EssentialColors.light() ? 0xFF6A6A6A : 0xFF8A8A8A);
        g.drawString(font, message, this.getX() + (this.width - font.width(message)) / 2,
                this.getY() + (this.height - 8) / 2, text, this.active && this.textColor == 0xFFFFFFFF);
    }
}
