package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.WalletScreen;
import ru.foxanto.spwallet.gui.overlay.IncomingNotifications;
import ru.foxanto.spwallet.util.SPServer;

/**
 * Hiding and showing the wallet's side column with the arrow in the transfer header, and the
 * notification a transfer of the player's own gets, with its comment cut short.
 */
public class SideColumnGameTest implements FabricClientGameTest {
    // Where the arrow is in the test window, read off the screenshots.
    private static final double HIDE_X = 558;
    private static final double SHOW_X = 760;
    private static final double TOGGLE_Y = 36;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> SPWalletConfig.get().sidePanelCollapsed = false);

            context.setScreen(() -> new WalletScreen(SPServer.SP));
            context.waitTicks(10);
            TemplateGameTest.click(context, HIDE_X, TOGGLE_Y);
            expect(context.computeOnClient(client -> SPWalletConfig.get().sidePanelCollapsed),
                    "the arrow should have hidden the side column");
            context.takeScreenshot("spwallet_wallet_side_hidden");

            // Remembered: a new wallet opens without it too.
            context.setScreen(() -> new WalletScreen(SPServer.SP));
            context.waitTicks(10);
            TemplateGameTest.click(context, SHOW_X, TOGGLE_Y);
            expect(!context.computeOnClient(client -> SPWalletConfig.get().sidePanelCollapsed),
                    "the arrow should have shown the side column again");
            context.takeScreenshot("spwallet_wallet_side_shown");
            context.setScreen(() -> null);

            context.runOnClient(client -> {
                IncomingNotifications.clear();
                IncomingNotifications.pushOutgoing("Основная", null, 138, 1199, "12345 (Foxanto)",
                        "аренда за октябрь");
                IncomingNotifications.pushOutgoing("Копилка", null, 5, 59, "54321", "налог");
            });
            context.waitTicks(10);
            context.takeScreenshot("spwallet_outgoing_notification");
            context.runOnClient(client -> IncomingNotifications.clear());
        }
    }

    private static void expect(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }
}
