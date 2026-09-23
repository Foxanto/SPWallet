package ru.foxanto.spwallet.gui;

/**
 * The Essential-styled palette SPWorlds Pay used, kept as ARGB.
 *
 * <p>The original stored the text colours with a zero alpha channel and relied on the font renderer
 * filling it in; that fallback is gone, so they are fully opaque here.
 */
public final class EssentialColors {
    public static final int BACKGROUND = 0xFF181818;
    public static final int BORDER = 0xFF232323;
    public static final int MODAL_OUTLINE = 0xFF474747;
    public static final int OVERLAY_DIM = 0x33000000;

    public static final int SCREEN_TITLE = 0xFFE5E5E5;
    public static final int MODAL_TEXT = 0xFFE2E2E2;

    public static final int TAB_TEXT = 0xFFBDBDBD;
    public static final int TAB_TEXT_HOVERED = 0xFFE2E2E2;
    public static final int TAB_TEXT_SELECTED = 0xFF2995FC;

    public static final int INPUT_TEXT = 0xFFE2E2E2;
    public static final int INPUT_PLACEHOLDER = 0xFFBFBFBF;

    public static final int CARD_BALANCE = 0xFF747474;
    public static final int SCROLLBAR = 0xFF5C5C5C;
    public static final int ERROR = 0xFFFF3333;

    public static final int BUTTON_TEXT = 0xFFE2E2E2;
    public static final int BUTTON_TEXT_DISABLED = 0xFFA0A0A0;
    public static final int FLAT_BUTTON_TEXT_DISABLED = 0xFFBFBFBF;

    private EssentialColors() {}
}
