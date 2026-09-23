package ru.foxanto.spwallet.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the inventory window is, so the card panel can sit beside it. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int spwallet$leftPos();

    @Accessor("topPos")
    int spwallet$topPos();

    @Accessor("imageWidth")
    int spwallet$imageWidth();

    @Accessor("imageHeight")
    int spwallet$imageHeight();
}
