package ru.foxanto.spwallet.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** Shared plumbing of the mod's owo screens. */
public abstract class EssentialScreen extends BaseOwoScreen<FlowLayout> {
    protected EssentialScreen(Component title) {
        super(title);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    /**
     * Whether owo gave up on {@link #build}. It swallows the exception and only shows a toast, so
     * this is what tells a caller that the screen is not actually usable.
     */
    public boolean failedToBuild() {
        return this.invalid;
    }
}
