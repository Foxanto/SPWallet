package ru.foxanto.spwallet.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Counts the diamond ore a player carries, which is the cash currency (АР) on SPWorlds.
 *
 * <p>Only the ore blocks count, plain and deepslate: diamonds themselves are not АР. Shulker boxes
 * in the inventory are looked into through their {@link DataComponents#CONTAINER} component, which the
 * client has for every item it can see, so no request to the server is needed.
 */
public final class OreCounter {
    /**
     * @param ore plain diamond ore
     * @param deepslate deepslate diamond ore
     * @param inShulkers how much of the total lies inside shulker boxes
     */
    public record Count(int ore, int deepslate, int inShulkers) {
        public int total() {
            return this.ore + this.deepslate;
        }

        public int loose() {
            return this.total() - this.inShulkers;
        }

        public Count plus(Count other) {
            return new Count(this.ore + other.ore, this.deepslate + other.deepslate,
                    this.inShulkers + other.inShulkers);
        }
    }

    private OreCounter() {}

    public static Count count(Player player) {
        Tally tally = new Tally();
        Inventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            tally.add(inventory.getItem(slot));
        }

        // The stack held on the cursor in an open inventory is in no slot, but is still the player's.
        tally.add(player.containerMenu.getCarried());

        return tally.count();
    }

    /**
     * The ore in the open container's own slots — a chest, a barrel, a shulker box — leaving out the
     * player's inventory, which the menu shows under it.
     */
    public static Count container(AbstractContainerMenu menu, Player player) {
        Tally tally = new Tally();

        for (Slot slot : menu.slots) {
            if (slot.container != player.getInventory()) {
                tally.add(slot.getItem());
            }
        }

        return tally.count();
    }

    /** The ore inside one item that holds others, a shulker box usually. */
    public static Count inside(ItemStack stack) {
        Tally tally = new Tally();
        tally.add(stack);
        return tally.count();
    }

    private static final class Tally {
        private int ore;
        private int deepslate;
        private int inShulkers;

        Count count() {
            return new Count(this.ore, this.deepslate, this.inShulkers);
        }

        void add(ItemStack stack) {
            addOre(stack, false);

            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);

            if (contents == null) {
                return;
            }

            for (ItemStack inside : contents.nonEmptyItems()) {
                addOre(inside, true);
            }
        }

        private void addOre(ItemStack stack, boolean inShulker) {
            int count = stack.getCount();

            if (stack.is(Items.DIAMOND_ORE)) {
                this.ore += count;
            } else if (stack.is(Items.DEEPSLATE_DIAMOND_ORE)) {
                this.deepslate += count;
            } else {
                return;
            }

            if (inShulker) {
                this.inShulkers += count;
            }
        }
    }
}
