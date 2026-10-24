package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import ru.foxanto.spwallet.api.ApiStatus;
import ru.foxanto.spwallet.api.ApiStatus.State;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.gui.WalletScreen;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.SPServer;

/**
 * The API status and its back-off: what each kind of answer does to them, that the background
 * checks really do hold off while it lasts, and screenshots of the HUD and the wallet showing it.
 *
 * <p>Runs on the debug cards and the stubbed API like the other tests. A stubbed request reports a
 * quick 200, so a status set by hand surviving a few seconds of play is what shows that no check
 * went out meanwhile.
 */
public class ApiStatusGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> checkTransitions());

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.waitTicks(20);

            String favourite = context.computeOnClient(client -> {
                Card card = SPWalletClient.cards().cards(SPServer.SP).getFirst();

                if (!SPWalletClient.cards().isFavourite(card.id())) {
                    SPWalletClient.cards().toggleFavourite(card.id());
                    return card.id();
                }

                return null;
            });

            context.runOnClient(client -> ApiStatus.answered(503, 50, 0, System.currentTimeMillis()));
            // Six seconds of play, short of the ten the second failure would wait but past the five
            // the checks run every: without the back-off a check would have asked by now. The
            // first failure only waits five, so it is reported twice.
            context.runOnClient(client -> ApiStatus.answered(503, 50, 0, System.currentTimeMillis()));
            context.waitTicks(120);

            State state = context.computeOnClient(client -> ApiStatus.state());
            expect(state == State.DOWN, "a check went out during the back-off and set the status to " + state);
            context.takeScreenshot("spwallet_hud_api_down");

            context.runOnClient(client -> ApiStatus.answered(429, 50, 30_000, System.currentTimeMillis()));
            context.waitTicks(2);
            context.takeScreenshot("spwallet_hud_api_rate_limited");

            // The wallet asks for its balances whatever the back-off says, which is also what
            // notices the API is back.
            context.setScreen(() -> new WalletScreen(SPServer.SP));
            context.waitTicks(10);
            state = context.computeOnClient(client -> ApiStatus.state());
            expect(state == State.OK, "the wallet's requests did not bring the status back, it is " + state);
            context.takeScreenshot("spwallet_wallet_api_ok");
            context.setScreen(() -> null);

            context.runOnClient(client -> {
                if (favourite != null) {
                    SPWalletClient.cards().toggleFavourite(favourite);
                }

                ApiStatus.reset();
                // Anything the panels skipped is fetched again on the next frame.
                CardInfoCache.favouriteRows(SPServer.SP);
            });
        }
    }

    private static void checkTransitions() {
        long now = 1_000_000;
        ApiStatus.reset();
        expect(ApiStatus.state() == State.UNKNOWN, "a fresh status should be unknown");
        expect(ApiStatus.problem() == null, "nothing asked yet is no problem to show");

        ApiStatus.answered(200, 100, 0, now);
        expect(ApiStatus.state() == State.OK, "a quick 200 should be OK");

        ApiStatus.answered(200, 3_000, 0, now);
        expect(ApiStatus.state() == State.SLOW, "a 200 after three seconds should be slow");
        expect(!ApiStatus.backingOff(now), "slow is no reason to stop asking");

        // A card turned away is the card's trouble, not the API's.
        ApiStatus.answered(401, 100, 0, now);
        expect(ApiStatus.state() == State.OK, "a 401 should leave the API OK");

        ApiStatus.unreachable(now);
        expect(ApiStatus.state() == State.DOWN, "no answer should be down");
        expect(ApiStatus.secondsToRetry(now) == 5, "the first back-off should be 5 s, got " + ApiStatus.secondsToRetry(now));
        expect(ApiStatus.backingOff(now + 4_999), "still backing off just before 5 s");
        expect(!ApiStatus.backingOff(now + 5_000), "done backing off at 5 s");

        ApiStatus.answered(502, 100, 0, now);
        expect(ApiStatus.secondsToRetry(now) == 10, "the second back-off should double to 10 s, got " + ApiStatus.secondsToRetry(now));

        // Retry-After wins when it asks for longer.
        ApiStatus.answered(429, 100, 60_000, now);
        expect(ApiStatus.state() == State.SLOW, "a 429 should be slow, not down");
        expect(ApiStatus.secondsToRetry(now) == 60, "Retry-After of 60 s should be honoured, got " + ApiStatus.secondsToRetry(now));

        for (int i = 0; i < 20; i++) {
            ApiStatus.answered(500, 100, 0, now);
        }

        expect(ApiStatus.secondsToRetry(now) == 300, "the back-off should stop at 5 minutes, got " + ApiStatus.secondsToRetry(now));
        expect(ApiStatus.problem() != null, "a down API should have a line to show");

        ApiStatus.answered(404, 100, 0, now);
        expect(ApiStatus.state() == State.OK && !ApiStatus.backingOff(now), "any real answer should end the back-off");
        expect(ApiStatus.problem() == null, "a working API takes no room on the panels");

        ApiStatus.reset();
    }

    private static void expect(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }
}
