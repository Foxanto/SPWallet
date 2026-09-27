package ru.foxanto.spwallet.gui;

import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.config.Theme;

/**
 * The Essential-styled palette SPWorlds Pay used, kept as ARGB, in a dark and a light variant.
 *
 * <p>The original stored the text colours with a zero alpha channel and relied on the font renderer
 * filling it in; that fallback is gone, so they are fully opaque here.
 *
 * <p>Every colour is read through a method rather than a constant, because the theme can be changed
 * while the game runs: anything drawn each frame follows along at once, and the few components that
 * take their colour when they are built follow when their screen is opened again.
 *
 * <p>The light values are the counterparts of the greys in
 * {@code tools/light_theme_textures.py}, which builds the light textures out of the dark ones.
 * A colour changed here belongs in that table too, or the panels stop matching what is drawn on
 * them.
 */
public final class EssentialColors {
    /**
     * One theme's colours.
     *
     * @param hudBackground the panel drawn over the world, which is see-through on purpose
     * @param rowHover laid over the row of a panel that the mouse is on
     * @param accentButtonText text of the blue and red buttons, whose colour does not change with
     *        the theme, so the text on them cannot either
     */
    private record Palette(int background, int border, int modalOutline, int overlayDim,
                           int hudBackground, int rowHover,
                           int screenTitle, int modalText,
                           int tabText, int tabTextHovered, int tabTextSelected,
                           int inputText, int inputPlaceholder,
                           int cardBalance, int scrollbar, int error,
                           int buttonText, int accentButtonText,
                           int buttonTextDisabled, int flatButtonTextDisabled) {}

    private static final Palette DARK = new Palette(
            0xFF181818, 0xFF232323, 0xFF474747, 0x33000000,
            0xD8181818, 0x26FFFFFF,
            0xFFE5E5E5, 0xFFE2E2E2,
            0xFFBDBDBD, 0xFFE2E2E2, 0xFF2995FC,
            0xFFE2E2E2, 0xFFBFBFBF,
            0xFF747474, 0xFF5C5C5C, 0xFFFF3333,
            0xFFE2E2E2, 0xFFE2E2E2,
            0xFFA0A0A0, 0xFFBFBFBF);

    // Grey rather than white, which glared; the text on it is black, or near enough to still tell
    // an unselected tab from a hovered one.
    private static final Palette LIGHT = new Palette(
            0xFFD5D5D5, 0xFFC5C5C5, 0xFF9A9A9A, 0x33000000,
            0xD8D5D5D5, 0x1F000000,
            0xFF000000, 0xFF000000,
            0xFF2E2E2E, 0xFF000000, 0xFF0D5FB3,
            0xFF000000, 0xFF505050,
            0xFF383838, 0xFF8A8A8A, 0xFFB71C1C,
            0xFF000000, 0xFFF2F2F2,
            0xFF666666, 0xFF5E5E5E);

    private EssentialColors() {}

    /** Whether the light theme is the one in use, for the two places that pick a texture. */
    public static boolean light() {
        return SPWalletConfig.get().theme == Theme.LIGHT;
    }

    /**
     * Whether text is drawn with a shadow. Minecraft's shadow is a darker copy of the glyph, which
     * under dark text on a light panel only smears it, so the light theme goes without.
     */
    public static boolean textShadow() {
        return !light();
    }

    public static int background() {
        return palette().background();
    }

    public static int border() {
        return palette().border();
    }

    public static int modalOutline() {
        return palette().modalOutline();
    }

    public static int overlayDim() {
        return palette().overlayDim();
    }

    public static int hudBackground() {
        return palette().hudBackground();
    }

    public static int rowHover() {
        return palette().rowHover();
    }

    public static int screenTitle() {
        return palette().screenTitle();
    }

    public static int modalText() {
        return palette().modalText();
    }

    public static int tabText() {
        return palette().tabText();
    }

    public static int tabTextHovered() {
        return palette().tabTextHovered();
    }

    public static int tabTextSelected() {
        return palette().tabTextSelected();
    }

    public static int inputText() {
        return palette().inputText();
    }

    public static int inputPlaceholder() {
        return palette().inputPlaceholder();
    }

    public static int cardBalance() {
        return palette().cardBalance();
    }

    public static int scrollbar() {
        return palette().scrollbar();
    }

    public static int error() {
        return palette().error();
    }

    public static int buttonText() {
        return palette().buttonText();
    }

    public static int accentButtonText() {
        return palette().accentButtonText();
    }

    public static int buttonTextDisabled() {
        return palette().buttonTextDisabled();
    }

    public static int flatButtonTextDisabled() {
        return palette().flatButtonTextDisabled();
    }

    private static Palette palette() {
        return light() ? LIGHT : DARK;
    }
}
