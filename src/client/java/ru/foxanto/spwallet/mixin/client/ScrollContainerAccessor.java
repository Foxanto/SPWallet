package ru.foxanto.spwallet.mixin.client;

import io.wispforest.owo.ui.container.ScrollContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the wallet keep owo's scrollbar permanently visible. */
@Mixin(ScrollContainer.class)
public interface ScrollContainerAccessor {
    @Accessor("scrollbaring")
    void spwallet$setScrollbaring(boolean scrollbaring);
}
