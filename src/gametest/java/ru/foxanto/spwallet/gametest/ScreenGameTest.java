package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.api.Transaction;
import ru.foxanto.spwallet.gui.AddCardScreen;
import ru.foxanto.spwallet.gui.EssentialScreen;
import ru.foxanto.spwallet.gui.MessageScreen;
import ru.foxanto.spwallet.gui.WalletScreen;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.config.Theme;
import ru.foxanto.spwallet.util.SPServer;
import ru.foxanto.spwallet.util.TransferMode;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Opens every screen of the mod and checks that owo managed to build it.
 *
 * <p>owo catches exceptions thrown from {@code build} and only shows a toast, so a broken layout
 * looks like an empty screen rather than a crash. This test is what turns that into a failure.
 *
 * <p>No card is saved beforehand on purpose: a card in the list would make the balance labels call
 * the live SPWorlds API.
 */
public class ScreenGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        SignPaymentGameTest.run();
        HangingSignGameTest.run(context);

        openAndCheck(context, "wallet", () -> new WalletScreen(SPServer.SP));
        openAndCheck(context, "wallet_from_sign",
                () -> new WalletScreen(SPServer.SPM, new Transaction("12345", 64, "Test")));
        openAndCheck(context, "wallet_by_nickname", () -> new WalletScreen(SPServer.SP, "Foxanto"));
        openAndCheck(context, "add_card",
                () -> new AddCardScreen(SPServer.SP, new Card("Test card", "0000", "token")));
        openAndCheck(context, "message", () -> new MessageScreen(
                Component.translatable("gui.spwallet.title.success"),
                Component.translatable("gui.spwallet.description.balance")));

        checkAmbiguousTarget(context);

        // Every screen again in the light theme, for the same "owo could build it" check and for a
        // screenshot of each to look over.
        context.runOnClient(client -> SPWalletConfig.get().theme = Theme.LIGHT);
        openAndCheck(context, "light_wallet", () -> new WalletScreen(SPServer.SP));
        openAndCheck(context, "light_add_card",
                () -> new AddCardScreen(SPServer.SP, new Card("Test card", "0000", "token")));
        openAndCheck(context, "light_message", () -> new MessageScreen(
                Component.translatable("gui.spwallet.title.success"),
                Component.translatable("gui.spwallet.description.balance")));
        context.runOnClient(client -> SPWalletConfig.get().theme = Theme.DARK);

        checkKeyBindings(context);

        context.setScreen(() -> null);
        context.restoreDefaultGameOptions();
    }

    /**
     * Both key bindings have to reach the vanilla controls screen, which is what makes them
     * rebindable. Checked against the options rather than a screenshot, because the list has to be
     * scrolled to reach the mod's own category.
     */
    private static void checkKeyBindings(ClientGameTestContext context) {
        String problem = context.computeOnClient(client -> {
            for (String name : List.of("key.spwallet.open_wallet_screen", "key.spwallet.interact")) {
                KeyMapping mapping = Arrays.stream(client.options.keyMappings)
                        .filter(candidate -> candidate.getName().equals(name))
                        .findFirst()
                        .orElse(null);

                if (mapping == null) {
                    return name + " is missing from the controls screen";
                }

                String category = mapping.getCategory().id().toString();

                if (!category.equals("spwallet:wallet")) {
                    return name + " sits in category " + category + " instead of spwallet:wallet";
                }
            }

            return null;
        });

        if (problem != null) {
            throw new AssertionError("Key bindings are wrong: " + problem);
        }

        context.setScreen(() -> new KeyBindsScreen(null, Minecraft.getInstance().options));
        context.waitTicks(5);
        context.takeScreenshot("key_binds");
    }

    /**
     * A target that is both a card number and a possible name has to end up on the by-nickname
     * form, because a player of that name exists. {@code DebugData} answers every lookup with a
     * card list, so in a dev environment the player always exists.
     */
    private static void checkAmbiguousTarget(ClientGameTestContext context) {
        context.setScreen(() -> new WalletScreen(SPServer.SP, "FURRY", 0, ""));

        // The lookup hands its answer back to the client thread, so it lands a tick or two later.
        context.waitTicks(10);

        String problem = context.computeOnClient(client -> {
            if (!(client.screen instanceof WalletScreen wallet)) {
                return "the wallet is not open any more";
            }

            return wallet.mode() == TransferMode.NICKNAME
                    ? null
                    : "it stayed on the " + wallet.mode() + " form";
        });

        if (problem != null) {
            throw new AssertionError("FURRY should have been read as a nickname: " + problem);
        }

        context.takeScreenshot("wallet_ambiguous_target");
    }

    private static void openAndCheck(ClientGameTestContext context, String name,
                                     Supplier<? extends Screen> screen) {
        context.setScreen(screen::get);
        context.waitTicks(5);

        // A screen owo failed to build closes itself on the first render, so "is it still open" has
        // to be asked before "did it fail" - otherwise a broken screen looks like a passing test.
        String problem = context.computeOnClient(client -> {
            if (!(client.screen instanceof EssentialScreen essential)) {
                return "it closed itself, which is what owo does after build() threw";
            }

            return essential.failedToBuild() ? "owo could not build it" : null;
        });

        if (problem != null) {
            throw new AssertionError("The " + name + " screen is broken: " + problem);
        }

        context.takeScreenshot(name);
    }
}
