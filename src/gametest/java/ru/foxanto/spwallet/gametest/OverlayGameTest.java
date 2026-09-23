package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import ru.foxanto.spwallet.config.PanelSide;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.HudPositionScreen;

/**
 * Draws the card panels in a real world: the HUD balances, the inventory panel on every side and
 * the HUD position editor. Checked by eye from the screenshots; the dev run uses the debug cards and
 * the stubbed API, so nothing reaches SPWorlds.
 */
public class OverlayGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            // Lets the stubbed balance requests come back before anything is photographed.
            context.waitTicks(20);
            context.takeScreenshot("spwallet_hud");

            for (PanelSide side : PanelSide.values()) {
                context.runOnClient(client -> SPWalletConfig.get().inventoryPanelSide = side);
                context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
                context.waitTicks(2);
                context.takeScreenshot("spwallet_inventory_" + side.name().toLowerCase());
            }

            context.setScreen(() -> new HudPositionScreen(null));
            context.waitTicks(2);
            context.takeScreenshot("spwallet_hud_position");

            context.setScreen(() -> null);
        }
    }
}
