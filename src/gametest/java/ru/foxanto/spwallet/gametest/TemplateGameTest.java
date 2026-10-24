package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;
import ru.foxanto.spwallet.storage.HistoryStorage;
import ru.foxanto.spwallet.storage.HistoryStorage.Entry;
import ru.foxanto.spwallet.storage.HistoryStorage.Kind;
import ru.foxanto.spwallet.storage.HistoryStorage.Recent;
import ru.foxanto.spwallet.storage.TemplateStorage;
import ru.foxanto.spwallet.storage.TemplateStorage.Template;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.gui.WalletScreen;
import ru.foxanto.spwallet.util.SPServer;
import ru.foxanto.spwallet.util.TransferMode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Templates and recent receivers: what is kept on disk, how the receivers are picked out of the
 * journal, and screenshots of the quick list in the wallet's side column.
 */
public class TemplateGameTest implements FabricClientGameTest {
    // Where things are in the test window, read off the screenshots.
    static final double QUICK_TAB_X = 633;
    static final double LOG_TAB_X = 703;
    static final double TABS_Y = 36;
    private static final double RENT_TEMPLATE_X = 680;
    private static final double RENT_TEMPLATE_Y = 190;
    private static final double NICKNAME_BOX_X = 380;
    private static final double NICKNAME_BOX_Y = 151;

    @Override
    public void runTest(ClientGameTestContext context) {
        checkTemplates();
        checkRecent();

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();

            context.runOnClient(client -> {
                TemplateStorage templates = SPWalletClient.templates();
                templates.add(new Template("Аренда", "sp", TransferMode.NICKNAME, "Foxanto", "10001", 128,
                        "аренда за октябрь"));
                templates.add(new Template("Налог", "sp", TransferMode.NUMBER, "54321", null, 0, null));

                long now = System.currentTimeMillis();
                SPWalletClient.history().add(new Entry(now, "sp", "card", "Основная", Kind.OUT, 30,
                        "77777", "Steve", null, 1000, false));
            });

            context.setScreen(() -> new WalletScreen(SPServer.SP));
            context.waitTicks(10);
            // The side column opens on whichever tab was open last, which the journal test left on
            // the log.
            click(context, QUICK_TAB_X, TABS_Y);
            context.takeScreenshot("spwallet_wallet_quick");

            // One click on a template fills in the whole form, the player's card looked up included.
            click(context, RENT_TEMPLATE_X, RENT_TEMPLATE_Y);
            context.waitTicks(10);
            context.takeScreenshot("spwallet_wallet_template_filled");

            // Tab completes the nickname from the tab list: typed "Pl", the test player is "Player…".
            context.setScreen(() -> new WalletScreen(SPServer.SP, "Pl"));
            context.waitTicks(10);
            click(context, NICKNAME_BOX_X, NICKNAME_BOX_Y);
            context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
            context.waitTicks(2);
            context.takeScreenshot("spwallet_wallet_nickname_completed");
            context.setScreen(() -> null);
        }
    }

    private static void checkTemplates() {
        Path file = tempFile("spwallet-templates");
        TemplateStorage templates = new TemplateStorage(file, true);
        Template rent = new Template("Rent", "sp", TransferMode.NICKNAME, "Foxanto", "10001", 128, "October");

        templates.add(rent);
        templates.add(new Template("Tax", "spm", TransferMode.NUMBER, "54321", null, 0, null));
        // Same name on the same server, whatever the case: replaces rather than adds.
        templates.add(new Template("rent", "sp", TransferMode.NICKNAME, "Foxanto", "10001", 192, "November"));

        TemplateStorage reloaded = new TemplateStorage(file, true);
        List<Template> sp = reloaded.templates(SPServer.SP);

        expect(sp.size() == 1 && sp.getFirst().amount() == 192, "the template should have been replaced: " + sp);
        expect(reloaded.templates(SPServer.SPM).size() == 1, "the SPm template should be kept apart");

        reloaded.remove(sp.getFirst());
        expect(new TemplateStorage(file, true).templates(SPServer.SP).isEmpty(), "a removed template should stay removed");

        delete(file);
    }

    private static void checkRecent() {
        HistoryStorage history = new HistoryStorage(tempFile("spwallet-history"), false);
        history.add(out("11111", null, 10, 1));
        history.add(out("22222", "Foxanto", 20, 2));
        history.add(new Entry(3, "sp", "card", "Main", Kind.IN, 99, null, null, null, null, false));
        // Another card of the same player: still the same receiver.
        history.add(out("33333", "foxanto", 30, 4));
        history.add(out("11111", null, 40, 5));

        List<Recent> recent = history.recent(SPServer.SP, 8);

        expect(recent.size() == 2, "expected two receivers, got " + recent);
        expect(recent.get(0).receiver().equals("11111") && recent.get(0).amount() == 40,
                "the newest transfer should come first with its amount: " + recent.get(0));
        expect(recent.get(1).receiver().equals("33333"),
                "a player should be listed by their latest card: " + recent.get(1));
        expect(history.recent(SPServer.SP, 1).size() == 1, "the limit should hold");
    }

    static void click(ClientGameTestContext context, double x, double y) {
        context.getInput().setCursorPos(x, y);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static Entry out(String receiver, String name, int amount, long time) {
        return new Entry(time, "sp", "card", "Main", Kind.OUT, amount, receiver, name, null, null, false);
    }

    private static Path tempFile(String prefix) {
        try {
            Path file = Files.createTempFile(prefix, ".json");
            Files.delete(file);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void expect(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }
}
