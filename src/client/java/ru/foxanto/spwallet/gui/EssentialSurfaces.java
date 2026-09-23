package ru.foxanto.spwallet.gui;

import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.util.NinePatchTexture;
import ru.foxanto.spwallet.SPWallet;

/** Nine-patch backgrounds for the panels the wallet screen is built from. */
public final class EssentialSurfaces {
    public static final Surface NAV_LEFT = panel("essential_nav_left");
    public static final Surface NAV_RIGHT = panel("essential_nav_right");
    public static final Surface PANEL_LEFT = panel("essential_panel_left");
    public static final Surface PANEL_RIGHT = panel("essential_panel_right");
    public static final Surface PANEL_RIGHT_TOP = panel("essential_panel_right_top");

    private EssentialSurfaces() {}

    private static Surface panel(String name) {
        var texture = SPWallet.id("panel/" + name);
        return (context, component) -> NinePatchTexture.draw(texture, context, component);
    }
}
