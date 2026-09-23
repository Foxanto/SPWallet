package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.gui.EssentialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A row of text tabs with exactly one of them selected, used both for the SP/SPm switch and for the
 * transfer mode switch.
 *
 * @param <T> what a tab stands for, usually an enum constant
 */
public class TabBar<T> extends FlowLayout {
    private final List<TransparentButton> tabs = new ArrayList<>();

    public TabBar(List<T> values, T selected, Function<T, Component> label, Consumer<T> onSelect) {
        super(Sizing.content(), Sizing.content(), Algorithm.HORIZONTAL);

        this.gap(13);

        for (T value : values) {
            TransparentButton tab = new TransparentButton(label.apply(value),
                    EssentialColors.TAB_TEXT,
                    EssentialColors.TAB_TEXT_HOVERED,
                    EssentialColors.TAB_TEXT_SELECTED,
                    button -> {});

            tab.shadow(true);
            tab.selected = value.equals(selected);

            tab.onPress(button -> {
                for (TransparentButton other : this.tabs) {
                    other.selected = false;
                }

                tab.selected = true;
                onSelect.accept(value);
            });

            this.tabs.add(tab);
            this.child(tab);
        }
    }
}
