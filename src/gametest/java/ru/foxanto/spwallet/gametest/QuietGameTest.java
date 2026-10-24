package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.overlay.IncomingNotifications;
import ru.foxanto.spwallet.gui.overlay.IncomingNotifications.Entry;
import ru.foxanto.spwallet.gui.overlay.IncomingNotifications.Source;

import java.util.List;

/**
 * What holds incoming money notifications back: the minimum amount, and do not disturb with the
 * summary it shows when it is turned off. Ends on a screenshot of that summary, copied to the chat.
 */
public class QuietGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();

            context.runOnClient(client -> {
                SPWalletConfig config = SPWalletConfig.get();
                config.incomingNotifications = true;
                config.incomingMinAmount = 0;
                config.incomingToChat = false;
                IncomingNotifications.doNotDisturb(false);
                IncomingNotifications.clear();

                IncomingNotifications.push("Main", null, 10, 100);
                expectShown(1);

                // Below the minimum: nothing; at it: shown.
                config.incomingMinAmount = 64;
                IncomingNotifications.push("Main", null, 10, 110);
                expectShown(1);
                IncomingNotifications.push("Main", null, 64, 174);
                expectShown(2);
                IncomingNotifications.clear();

                // Do not disturb holds everything back, small amounts included, summed per card.
                IncomingNotifications.doNotDisturb(true);
                expect(config.doNotDisturb, "do not disturb should be on");
                IncomingNotifications.push("Main", null, 30, 204);
                IncomingNotifications.push("Main", null, 40, 244);
                IncomingNotifications.push("Piggy bank", null, 70, 70);
                IncomingNotifications.pushAway("Piggy bank", null, 5, 75);
                expectShown(0);
                expect(IncomingNotifications.held() == 145, "expected 145 held, got " + IncomingNotifications.held());

                config.incomingToChat = true;
                IncomingNotifications.doNotDisturb(false);
                List<Entry> shown = IncomingNotifications.shown();
                expect(shown.size() == 2, "expected one summary a card, got " + shown);

                Entry main = shown.get(0);
                Entry piggy = shown.get(1);
                expect(main.cardName().equals("Main") && main.amount() == 70 && main.count() == 2
                        && main.balance() == 244 && main.source() == Source.QUIET, "wrong summary for Main: " + main);
                expect(piggy.amount() == 75 && piggy.count() == 2 && piggy.balance() == 75,
                        "wrong summary for the piggy bank: " + piggy);
                expect(IncomingNotifications.held() == 0, "nothing should be held once it is shown");
            });

            context.waitTicks(10);
            context.takeScreenshot("spwallet_quiet_summary");

            context.runOnClient(client -> {
                SPWalletConfig config = SPWalletConfig.get();
                config.incomingMinAmount = 0;
                config.incomingToChat = false;
                IncomingNotifications.clear();
            });
        }
    }

    private static void expectShown(int expected) {
        int shown = IncomingNotifications.shown().size();
        expect(shown == expected, "expected " + expected + " notifications, got " + shown);
    }

    private static void expect(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }
}
