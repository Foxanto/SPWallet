package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import ru.foxanto.spwallet.api.Transaction;
import ru.foxanto.spwallet.config.AmountFormat;
import ru.foxanto.spwallet.gui.WalletScreen;
import ru.foxanto.spwallet.util.DebugData;
import ru.foxanto.spwallet.util.SPServer;

/**
 * The ways the transfer form takes an amount, and screenshots of the hint at the end of the field:
 * the amount in stacks, and the transfer held back for being more than the card holds.
 */
public class AmountGameTest implements FabricClientGameTest {
    /** Where the transfer button is in the test window, read off the screenshots. */
    private static final double TRANSFER_BUTTON_X = 452;
    private static final double TRANSFER_BUTTON_Y = 400;

    @Override
    public void runTest(ClientGameTestContext context) {
        checkParse();

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();

            context.setScreen(() -> new WalletScreen(SPServer.SP, new Transaction("12345", 138, "Test")));
            context.waitTicks(10);
            context.takeScreenshot("spwallet_amount_hint_stacks");

            context.setScreen(() -> new WalletScreen(SPServer.SP,
                    new Transaction("12345", DebugData.BALANCE + 1, "Test")));
            context.waitTicks(10);
            context.takeScreenshot("spwallet_amount_hint_over_balance");

            // Over the confirmation threshold but within the balance: the button asks first. The
            // stubbed transfer only goes out on the overlay's own button, which is not pressed.
            context.setScreen(() -> new WalletScreen(SPServer.SP, new Transaction("12345", 500, "Test")));
            context.waitTicks(10);
            context.getInput().setCursorPos(TRANSFER_BUTTON_X, TRANSFER_BUTTON_Y);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.takeScreenshot("spwallet_confirm_transfer");

            context.setScreen(() -> null);
        }
    }

    private static void checkParse() {
        expect("130", 130);
        expect(" 130 ", 130);
        expect("130 АР", 130);
        expect("130ar", 130);
        expect("2ст", 128);
        expect("2 ст.", 128);
        expect("2ст 10", 138);
        expect("2ст10", 138);
        expect("2 ст. 10 АР", 138);
        expect("2 СТ 10 ар", 138);
        expect("2с 10", 138);
        expect("2st 10", 138);
        expect("2 st. 10 AR", 138);
        expect("2:10", 138);
        expect("2 : 10", 138);
        expect("2:", 128);
        expect("0:63", 63);

        // A leftover of a stack or more is a typo, not a way of writing it.
        expect("2:64", null);
        expect("2ст 100", null);
        expect("", null);
        expect("ст", null);
        expect("2:10:5", null);
        expect("-5", null);
        expect("1.5", null);
        expect("99999ст", null);

        if (AmountFormat.isStacks("138") || !AmountFormat.isStacks("2:10")) {
            throw new AssertionError("isStacks should tell a plain number from one in stacks");
        }

        if (!AmountFormat.isBeingTyped("2 ст. 10 АР") || !AmountFormat.isBeingTyped("2st 10 ar")
                || AmountFormat.isBeingTyped("2x") || AmountFormat.isBeingTyped("-1")) {
            throw new AssertionError("The amount filter lets through the wrong characters");
        }
    }

    private static void expect(String input, @Nullable Integer expected) {
        Integer parsed = AmountFormat.parse(input);

        if (expected == null ? parsed != null : !expected.equals(parsed)) {
            throw new AssertionError("\"" + input + "\" should read as " + expected + ", got " + parsed);
        }
    }
}
