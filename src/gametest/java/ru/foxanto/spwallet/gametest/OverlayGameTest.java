package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.config.PanelSide;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.config.Theme;
import ru.foxanto.spwallet.gui.HudPositionScreen;
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

            // What a click on a row of the inventory panel does.
            context.runOnClient(client -> {
                List<Card> cards = SPWalletClient.cards().cards(SPServer.SP);
                SPWalletClient.cards().toggleFavourite(cards.getFirst().id());
            });

            checkFavourites(context, 1);
            context.setScreen(() -> null);
            context.waitTicks(20);
            context.takeScreenshot("spwallet_hud");

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

    private static void checkFavourites(ClientGameTestContext context, int expected) {
        int count = context.computeOnClient(client -> CardInfoCache.favouriteRows(SPServer.SP).size());

        if (count != expected) {
            throw new AssertionError("The HUD has " + count + " cards on it, expected " + expected);
        }
    }
}
