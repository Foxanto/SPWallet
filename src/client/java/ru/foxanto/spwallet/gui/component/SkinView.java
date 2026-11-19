package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.gui.PlayerSkins;

import java.util.function.Supplier;

/**
 * The player being paid, standing and turning after the mouse — drawn through the real player
 * renderer, so Ears features show when the mod is installed. Empty while no name is entered.
 */
public class SkinView extends BaseUIComponent {
    private final Supplier<@Nullable String> name;

    public SkinView(Sizing horizontal, Sizing vertical, Supplier<@Nullable String> name) {
        this.name = name;
        this.sizing(horizontal, vertical);
    }

    @Override
    public void draw(OwoUIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
        String player = this.name.get();
        if (player == null || player.isEmpty()) return;

        int scale = Math.max(10, Math.round(this.height * 0.45F));
        PlayerSkins.drawModel(g, player, this.x, this.y, this.x + this.width, this.y + this.height,
                scale, mouseX, mouseY);
    }
}
