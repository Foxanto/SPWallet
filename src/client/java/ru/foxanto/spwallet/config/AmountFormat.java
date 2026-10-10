package ru.foxanto.spwallet.config;

import net.minecraft.network.chat.Component;

/**
 * How an amount of АР is written out: in stacks of 64 and what is left over, the way ore is carried
 * ({@code 1 ст. 30 АР}), or as one number ({@code 94 АР}).
 *
 * <p>Card balances and the ore count each have their own setting, so a player can, say, read the
 * ore in stacks and the balances as plain numbers.
 */
public enum AmountFormat {
    STACKS("stacks"),
    TOTAL("total");

    public static final int STACK = 64;

    private final String key;

    AmountFormat(String key) {
        this.key = key;
    }

    public Component label() {
        return Component.translatable("config.spwallet.amount_format." + this.key);
    }

    /** {@code amount} in this format, with the АР unit; a whole number of stacks needs no unit. */
    public Component format(int amount) {
        // A negative amount in stacks reads oddly, and nothing the mod shows should be one anyway.
        if (this == TOTAL || amount < STACK) {
            return Component.translatable("gui.spwallet.amount", amount);
        }

        int stacks = amount / STACK;
        int rest = amount % STACK;

        return rest == 0
                ? Component.translatable("gui.spwallet.amount.stacks", stacks)
                : Component.translatable("gui.spwallet.amount.stacks_rest", stacks, rest);
    }

    /** A card balance or transfer amount, in the format picked for cards. */
    public static Component card(int amount) {
        return SPWalletConfig.get().cardAmountFormat.format(amount);
    }

    /** An amount of counted ore, in the format picked for the ore count. */
    public static Component ore(int amount) {
        return SPWalletConfig.get().oreAmountFormat.format(amount);
    }
}
