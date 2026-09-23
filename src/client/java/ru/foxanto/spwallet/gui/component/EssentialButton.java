package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.mixin.ui.access.AbstractWidgetAccessor;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.util.NinePatchTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.gui.EssentialColors;

import java.util.function.Consumer;

/**
 * A nine-patch button in one of the Essential styles.
 *
 * <p>SPWorlds Pay had one class per style; they only differed in the texture directory and the
 * disabled text colour, so they are folded into {@link Style} here.
 */
public class EssentialButton extends ButtonComponent {
    private final Style style;

    public EssentialButton(Style style, Component message, Consumer<ButtonComponent> onPress) {
        super(message, onPress);
        this.style = style;
    }

    @Override
    public void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Identifier texture = this.style.texture(this.active, this.isHovered());
        NinePatchTexture.draw(texture, (OwoUIGraphics) context, this.getX(), this.getY(), this.width, this.height);

        var font = Minecraft.getInstance().font;
        int color = this.active ? EssentialColors.BUTTON_TEXT : this.style.disabledTextColor;

        if (this.textShadow) {
            context.drawCenteredString(font, this.getMessage(),
                    this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
        } else {
            context.drawString(font, this.getMessage(),
                    (int) (this.getX() + this.width / 2f - font.width(this.getMessage()) / 2f),
                    (int) (this.getY() + (this.height - 8) / 2f), color, false);
        }

        var tooltip = ((AbstractWidgetAccessor) this).owo$getTooltip();

        if (this.isHovered && tooltip.get() != null) {
            context.setTooltipForNextFrame(font, tooltip.get().toCharSequence(Minecraft.getInstance()),
                    DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, false);
        }
    }

    public enum Style {
        NEUTRAL("essential_button", EssentialColors.BUTTON_TEXT_DISABLED),
        BLUE("essential_blue_button", EssentialColors.BUTTON_TEXT_DISABLED),
        RED("essential_red_button", EssentialColors.BUTTON_TEXT_DISABLED),
        FLAT("essential_flat_button", EssentialColors.FLAT_BUTTON_TEXT_DISABLED);

        private final Identifier active;
        private final Identifier hovered;
        private final Identifier disabled;
        private final int disabledTextColor;

        Style(String directory, int disabledTextColor) {
            this.active = SPWallet.id(directory + "/active");
            this.hovered = SPWallet.id(directory + "/hovered");
            this.disabled = SPWallet.id(directory + "/disabled");
            this.disabledTextColor = disabledTextColor;
        }

        private Identifier texture(boolean active, boolean hovered) {
            if (!active) {
                return this.disabled;
            }

            return hovered ? this.hovered : this.active;
        }
    }
}
