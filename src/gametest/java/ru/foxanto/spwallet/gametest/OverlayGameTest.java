package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.config.PanelSide;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.config.Theme;
import ru.foxanto.spwallet.gui.HudPositionScreen;
import ru.foxanto.spwallet.gui.overlay.IncomingNotifications;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.SPServer;

import java.util.List;

/**
 * Draws the card panels in a real world: the HUD balances, the inventory panel on every side and
 * the HUD position editor. Checked by eye from the screenshots; the dev run uses the debug cards and
 * the stubbed API, so nothing reaches SPWorlds.
 *
 * <p>The HUD panel is also checked against its rule: it shows the favourite cards, so it is not
 * there at all until one is starred.
 */
public class OverlayGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            // Lets the stubbed balance requests come back before anything is photographed.
            context.waitTicks(20);

            // Nothing starred yet: the HUD shows no panel, whatever cards are saved.
            checkFavourites(context, 0);
            context.takeScreenshot("spwallet_hud_without_favourites");

            for (PanelSide side : PanelSide.values()) {
                context.runOnClient(client -> SPWalletConfig.get().inventoryPanelSide = side);
                context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
                context.waitTicks(2);
                context.takeScreenshot("spwallet_inventory_" + side.name().toLowerCase());
            }

            // With effects on, the panel on the right moves out from over the effect list.
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().forEach(player -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 60 * 5));
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 5, 1));
            }));
            context.runOnClient(client -> SPWalletConfig.get().inventoryPanelSide = PanelSide.RIGHT);
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(5);
            context.takeScreenshot("spwallet_inventory_right_with_effects");
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers()
                    .forEach(player -> player.removeAllEffects()));

            // What a click on a row of the inventory panel does.
            context.runOnClient(client -> {
                List<Card> cards = SPWalletClient.cards().cards(SPServer.SP);
                SPWalletClient.cards().toggleFavourite(cards.getFirst().id());
            });

            checkFavourites(context, 1);
            context.setScreen(() -> null);
            context.waitTicks(20);
            context.takeScreenshot("spwallet_hud");

            checkIncoming(context);

            // The panels take their colours from the palette rather than from a texture, so the
            // light theme reaches them without anything being rebuilt.
            context.runOnClient(client -> SPWalletConfig.get().theme = Theme.LIGHT);
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(2);
            context.takeScreenshot("spwallet_light_inventory");
            context.setScreen(() -> null);
            context.waitTicks(2);
            context.takeScreenshot("spwallet_light_hud");
            context.runOnClient(client -> SPWalletConfig.get().theme = Theme.DARK);

            context.setScreen(() -> new HudPositionScreen(null));
            context.waitTicks(2);
            context.takeScreenshot("spwallet_hud_position");

            context.setScreen(() -> null);
        }
    }

    /**
     * The stopgap for incoming money: a balance that comes back higher than the one before is a
     * notification, the player's own transfer lowering it is not, and neither is an answer to a
     * request sent before that transfer, which still holds the old, higher balance.
     */
    private static void checkIncoming(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Card card = SPWalletClient.cards().cards(SPServer.SP).getFirst();
            long before = System.currentTimeMillis() - 1_000;

            // Whatever the debug API answered is the baseline; this is the transfer going out.
            CardInfoCache.balanceChanged(card, 1_000);
            // An answer to a request sent before the transfer: dropped, not "money came back".
            CardInfoCache.balanceFetched(card, 1_337, before);
            expectNotifications(0);

            CardInfoCache.balanceFetched(card, 1_064, System.currentTimeMillis() + 1);
            expectNotifications(1);

            IncomingNotifications.Entry entry = IncomingNotifications.shown().getFirst();

            if (entry.amount() != 64 || entry.balance() != 1_064) {
                throw new AssertionError("Expected +64 with 1064 left, got " + entry);
            }

            // Going down is never a notification.
            CardInfoCache.balanceFetched(card, 900, System.currentTimeMillis() + 2);
            expectNotifications(1);
        });

        // Past the slide in, so the screenshot shows it where it settles.
        context.waitTicks(10);
        context.takeScreenshot("spwallet_incoming_notification");

        context.setScreen(() -> new HudPositionScreen(null));
        context.waitTicks(2);
        context.takeScreenshot("spwallet_hud_position_with_notification");
        context.setScreen(() -> null);
    }

    private static void expectNotifications(int expected) {
        int shown = IncomingNotifications.shown().size();

        if (shown != expected) {
            throw new AssertionError("Expected " + expected + " incoming notifications, got " + shown);
        }
    }

    private static void checkFavourites(ClientGameTestContext context, int expected) {
        int count = context.computeOnClient(client -> CardInfoCache.favouriteRows(SPServer.SP).size());

        if (count != expected) {
            throw new AssertionError("The HUD has " + count + " cards on it, expected " + expected);
        }
    }
}
