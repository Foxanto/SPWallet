package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.input.MouseButtonEvent;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.mixin.client.ScrollContainerAccessor;

/** A scroll container with a permanently visible, thin flat scrollbar. */
public class EssentialScrollContainer extends ScrollContainer<UIComponent> {
    public EssentialScrollContainer(ScrollDirection direction, Sizing horizontalSizing, Sizing verticalSizing,
                                    UIComponent child) {
        super(direction, horizontalSizing, verticalSizing, child);

        this.scrollbar(Scrollbar.flat(Color.ofArgb(EssentialColors.scrollbar())));
        this.scrollbarThiccness(3);

        // Keeps the scrollbar drawn at full opacity instead of fading out when idle.
        ((ScrollContainerAccessor) this).spwallet$setScrollbaring(true);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        // Releasing the mouse must not reset the "always visible" flag set above.
        return true;
    }
}
