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
import java.util.function.IntSupplier;

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
        int color = this.active ? this.style.textColor() : this.style.disabledTextColor();
        // The field rather than getMessage(): an inactive button answers that with a copy of its
        // text dyed a fixed grey, which would win over the theme's disabled colour.
        Component message = this.message;

        if (this.textShadow && EssentialColors.textShadow()) {
            context.drawCenteredString(font, message,
                    this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
        } else {
            context.drawString(font, message,
                    (int) (this.getX() + this.width / 2f - font.width(message) / 2f),
                    (int) (this.getY() + (this.height - 8) / 2f), color, false);
        }

        var tooltip = ((AbstractWidgetAccessor) this).owo$getTooltip();

        if (this.isHovered && tooltip.get() != null) {
            context.setTooltipForNextFrame(font, tooltip.get().toCharSequence(Minecraft.getInstance()),
                    DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, false);
        }
    }

    /**
     * The four button styles.
     *
     * <p>The colours are looked up rather than stored: an enum constant is built once, and the
     * theme can change afterwards. Blue and red keep their colour in both themes, so the text on
     * them stays light while the grey button's text follows the background.
     */
    public enum Style {
        NEUTRAL("essential_button", EssentialColors::buttonText, EssentialColors::buttonTextDisabled),
        BLUE("essential_blue_button", EssentialColors::accentButtonText, EssentialColors::buttonTextDisabled),
        RED("essential_red_button", EssentialColors::accentButtonText, EssentialColors::buttonTextDisabled),
        FLAT("essential_flat_button", EssentialColors::accentButtonText, EssentialColors::flatButtonTextDisabled);

        private final Textures dark;
        private final Textures light;
        private final IntSupplier textColor;
        private final IntSupplier disabledTextColor;

        Style(String directory, IntSupplier textColor, IntSupplier disabledTextColor) {
            this.dark = Textures.of("", directory);
            this.light = Textures.of("light/", directory);
            this.textColor = textColor;
            this.disabledTextColor = disabledTextColor;
        }

        private int textColor() {
            return this.textColor.getAsInt();
        }

        private int disabledTextColor() {
            return this.disabledTextColor.getAsInt();
        }

        private Identifier texture(boolean active, boolean hovered) {
            Textures textures = EssentialColors.light() ? this.light : this.dark;

            if (!active) {
                return textures.disabled();
            }

            return hovered ? textures.hovered() : textures.active();
        }

        /** One theme's three states of a style, which differ only in the directory they live in. */
        private record Textures(Identifier active, Identifier hovered, Identifier disabled) {
            static Textures of(String prefix, String directory) {
                return new Textures(
                        SPWallet.id(prefix + directory + "/active"),
                        SPWallet.id(prefix + directory + "/hovered"),
                        SPWallet.id(prefix + directory + "/disabled"));
            }
        }
    }
}
