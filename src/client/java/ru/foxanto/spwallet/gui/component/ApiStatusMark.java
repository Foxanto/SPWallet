package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.api.ApiStatus;
import ru.foxanto.spwallet.gui.EssentialColors;

/**
 * A dot that is green while the API answers, amber while it is slow or rate limited and red while
 * it is down, with a tooltip saying which and when the next try is. Follows {@link ApiStatus} as it
 * changes, so it can stay up for as long as the wallet is open.
 */
public class ApiStatusMark extends LabelComponent {
    private Component shownTooltip = Component.empty();

    public ApiStatusMark() {
        super(Component.literal("●"));
        this.refresh();
    }

    @Override
    public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        this.refresh();
        super.draw(context, mouseX, mouseY, partialTicks, delta);
    }

    private void refresh() {
        this.color(Color.ofArgb(EssentialColors.apiStatus(ApiStatus.state())));

        Component tooltip = ApiStatus.describe(System.currentTimeMillis());

        // Rebuilding the tooltip every frame would lay it out every frame; only a new text needs it.
        if (!tooltip.equals(this.shownTooltip)) {
            this.shownTooltip = tooltip;
            this.tooltip(tooltip);
        }
    }
}
