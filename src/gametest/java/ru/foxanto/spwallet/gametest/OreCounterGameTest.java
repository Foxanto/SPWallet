package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import ru.foxanto.spwallet.config.AmountFormat;
import ru.foxanto.spwallet.config.PanelSide;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.util.OreCounter;

import java.util.List;

/**
 * Counts the АР in an inventory that holds both kinds of ore loose and in a shulker box, next to
 * things that are not АР (diamonds, other ore), and photographs its panel on every side. Then checks
 * the АР line in a shulker box tooltip and the panel in an opened chest.
 */
public class OreCounterGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();

            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().forEach(player -> {
                ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
                shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                        new ItemStack(Items.DIAMOND_ORE, 64),
                        new ItemStack(Items.DEEPSLATE_DIAMOND_ORE, 10),
                        new ItemStack(Items.DIAMOND, 30))));

                player.getInventory().add(new ItemStack(Items.DIAMOND_ORE, 5));
                player.getInventory().add(new ItemStack(Items.DEEPSLATE_DIAMOND_ORE, 3));
                player.getInventory().add(new ItemStack(Items.DIAMOND, 12));
                player.getInventory().add(new ItemStack(Items.EMERALD_ORE, 7));
                player.getInventory().add(shulker);
            }));
            context.waitTicks(5);

            OreCounter.Count count = context.computeOnClient(client -> OreCounter.count(client.player));

            if (count.ore() != 69 || count.deepslate() != 13 || count.inShulkers() != 74) {
                throw new AssertionError("Expected 69 ore, 13 deepslate, 74 in shulkers, got " + count);
            }

            context.runOnClient(client -> {
                expectFormat(AmountFormat.STACKS, 94, "1 st. 30 AR");
                expectFormat(AmountFormat.STACKS, 128, "2 st.");
                expectFormat(AmountFormat.STACKS, 30, "30 AR");
                expectFormat(AmountFormat.STACKS, 0, "0 AR");
                expectFormat(AmountFormat.TOTAL, 94, "94 AR");

                // Cards and the ore count are set apart: changing one leaves the other alone.
                SPWalletConfig config = SPWalletConfig.get();
                config.cardAmountFormat = AmountFormat.TOTAL;
                config.oreAmountFormat = AmountFormat.STACKS;
                expectText("card", AmountFormat.card(94), "94 AR");
                expectText("ore", AmountFormat.ore(94), "1 st. 30 AR");

                config.cardAmountFormat = AmountFormat.STACKS;
                config.oreAmountFormat = AmountFormat.TOTAL;
                expectText("card", AmountFormat.card(94), "1 st. 30 AR");
                expectText("ore", AmountFormat.ore(94), "94 AR");

                // The rest of the test, the tooltip check included, runs on the defaults.
                config.cardAmountFormat = AmountFormat.STACKS;
                config.oreAmountFormat = AmountFormat.STACKS;
            });

            // Every side, and the right with effects on, where it has to clear both the card panel
            // above it and the effect list.
            context.waitTicks(20);

            for (PanelSide side : PanelSide.values()) {
                context.runOnClient(client -> SPWalletConfig.get().orePanelSide = side);
                context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
                context.waitTicks(2);
                context.takeScreenshot("spwallet_ore_panel_" + side.name().toLowerCase());
            }

            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().forEach(player ->
                    player.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 60 * 5))));
            context.runOnClient(client -> SPWalletConfig.get().orePanelSide = PanelSide.RIGHT);
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(5);
            context.takeScreenshot("spwallet_ore_panel_right_with_effects");
            context.setScreen(() -> null);

            // The shulker box's tooltip carries its АР under its name.
            boolean tooltip = context.computeOnClient(client -> client.player.getInventory().getNonEquipmentItems()
                    .stream()
                    .filter(stack -> stack.is(Items.SHULKER_BOX))
                    .findFirst()
                    .map(stack -> stack.getTooltipLines(Item.TooltipContext.EMPTY, client.player, TooltipFlag.NORMAL))
                    .map(lines -> lines.size() > 1 && lines.get(1).getString().endsWith("1 st. 10 AR"))
                    .orElse(false));

            if (!tooltip) {
                throw new AssertionError("The shulker box tooltip has no line with its 74 AR");
            }

            // A chest with ore in it, loose and in a shulker box: the panel counts it beside the inventory.
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().forEach(player -> {
                BlockPos pos = player.blockPosition().above(3);
                player.level().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());

                if (player.level().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                    ItemStack box = new ItemStack(Items.SHULKER_BOX);
                    box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                            new ItemStack(Items.DEEPSLATE_DIAMOND_ORE, 20))));

                    chest.setItem(0, new ItemStack(Items.DIAMOND_ORE, 40));
                    chest.setItem(1, box);
                    player.openMenu(chest);
                }
            }));
            context.waitTicks(10);

            int stored = context.computeOnClient(client -> client.screen instanceof ContainerScreen screen
                    ? OreCounter.container(screen.getMenu(), client.player).total()
                    : -1);

            if (stored != 60) {
                throw new AssertionError("Expected 60 AR in the chest, got " + stored);
            }

            context.takeScreenshot("spwallet_ore_panel_chest");
            context.setScreen(() -> null);
        }
    }

    private static void expectFormat(AmountFormat format, int amount, String expected) {
        expectText(format + " " + amount, format.format(amount), expected);
    }

    private static void expectText(String what, Component text, String expected) {
        String actual = text.getString();

        if (!actual.equals(expected)) {
            throw new AssertionError(what + ": expected \"" + expected + "\", got \"" + actual + "\"");
        }
    }
}
