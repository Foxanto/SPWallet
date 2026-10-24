package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.gui.WalletScreen;
import ru.foxanto.spwallet.storage.HistoryStorage;
import ru.foxanto.spwallet.storage.HistoryStorage.Entry;
import ru.foxanto.spwallet.storage.HistoryStorage.Kind;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.SPServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The journal: what it keeps on disk and in what order, that money coming in reaches it, and a
 * screenshot of the wallet's side column with a few operations in it.
 */
public class HistoryGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        checkStorage();

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.waitTicks(20);

            context.runOnClient(client -> checkIncomingRecorded());

            context.runOnClient(client -> {
                Card card = SPWalletClient.cards().cards(SPServer.SP).getFirst();
                long now = System.currentTimeMillis();
                HistoryStorage history = SPWalletClient.history();

                history.add(new Entry(now - 3 * 86_400_000L, "sp", card.id(), card.name(), Kind.IN, 64,
                        null, null, null, 1401, true));
                history.add(new Entry(now - 3_600_000L, "sp", card.id(), card.name(), Kind.OUT, 138,
                        "12345", "Foxanto", "аренда за октябрь", 1263, false));
                history.add(new Entry(now, "sp", card.id(), card.name(), Kind.OUT, 5,
                        "54321", null, null, 1258, false));
            });

            context.setScreen(() -> new WalletScreen(SPServer.SP));
            context.waitTicks(10);
            TemplateGameTest.click(context, TemplateGameTest.LOG_TAB_X, TemplateGameTest.TABS_Y);
            context.takeScreenshot("spwallet_wallet_history");
            context.setScreen(() -> null);
        }
    }

    private static void checkStorage() {
        Path file;

        try {
            file = Files.createTempFile("spwallet-history", ".json");
            Files.delete(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        HistoryStorage history = new HistoryStorage(file, true);
        history.add(entry("sp", Kind.OUT, 10, 1));
        history.add(entry("spm", Kind.IN, 20, 2));
        history.add(entry("sp", Kind.IN, 30, 3));
        history.balance("card", 1337);

        HistoryStorage reloaded = new HistoryStorage(file, true);
        List<Entry> sp = reloaded.entries(SPServer.SP);

        expect(sp.size() == 2, "expected 2 SP entries after a reload, got " + sp.size());
        expect(sp.get(0).amount() == 30 && sp.get(1).amount() == 10, "SP entries should be newest first: " + sp);
        expect(sp.get(0).equals(entry("sp", Kind.IN, 30, 3)), "an entry should come back as it was: " + sp.get(0));
        expect(reloaded.entries(SPServer.SPM).size() == 1, "the SPm entry should be kept apart");
        expect(Integer.valueOf(1337).equals(reloaded.lastBalance("card")), "the last balance should be kept");

        int version = reloaded.version();
        reloaded.clear(SPServer.SP);
        expect(reloaded.version() != version, "clearing should count as a change");
        expect(reloaded.entries(SPServer.SP).isEmpty() && reloaded.entries(SPServer.SPM).size() == 1,
                "clearing SP should leave SPm alone");

        for (int i = 0; i < HistoryStorage.MAX_ENTRIES + 5; i++) {
            reloaded.add(entry("sp", Kind.OUT, i, 100 + i));
        }

        List<Entry> capped = new HistoryStorage(file, true).entries(SPServer.SP);
        // The SPm entry is the oldest of all, so it is among the first to go.
        expect(capped.size() == HistoryStorage.MAX_ENTRIES, "expected " + HistoryStorage.MAX_ENTRIES
                + " entries at most, got " + capped.size());
        expect(capped.getFirst().amount() == HistoryStorage.MAX_ENTRIES + 4, "the newest entry should be kept");

        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A rise of the balance between two checks is in the journal; the player's own transfer is not money in. */
    private static void checkIncomingRecorded() {
        Card card = SPWalletClient.cards().cards(SPServer.SP).getFirst();
        HistoryStorage history = SPWalletClient.history();
        long now = System.currentTimeMillis();

        CardInfoCache.balanceChanged(card, 500);
        int before = history.entries(SPServer.SP).size();

        CardInfoCache.balanceFetched(card, 400, now + 1);
        expect(history.entries(SPServer.SP).size() == before, "a lower balance should not be in the journal");

        CardInfoCache.balanceFetched(card, 464, now + 2);
        List<Entry> entries = history.entries(SPServer.SP);
        expect(entries.size() == before + 1, "a rise should be in the journal");

        Entry entry = entries.getFirst();
        expect(entry.kind() == Kind.IN && entry.amount() == 64 && Integer.valueOf(464).equals(entry.balance())
                && !entry.away(), "expected +64 with 464 left, got " + entry);
    }

    private static Entry entry(String server, Kind kind, int amount, long time) {
        return new Entry(time, server, "card", "Main", kind, amount,
                kind == Kind.OUT ? "12345" : null, null, kind == Kind.OUT ? "comment" : null, amount * 10, false);
    }

    private static void expect(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }
}
