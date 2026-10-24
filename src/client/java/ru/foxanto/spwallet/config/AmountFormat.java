package ru.foxanto.spwallet.config;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** The largest amount {@link #parse} gives; no card holds anywhere near it. */
    public static final int MAX_AMOUNT = 999_999;

    /** A plain number, optionally with the unit: {@code 130}, {@code 130 АР}. */
    private static final Pattern TOTAL_INPUT = Pattern.compile("(\\d{1,6})\\s*(?:ар|ar)?");
    /**
     * Stacks and what is left over, the way {@link #format} writes them and the ways people shorten
     * that: {@code 2ст 10}, {@code 2 ст. 10 АР}, {@code 2с10}, {@code 2st 10}, {@code 2:10}, {@code 2ст}.
     */
    private static final Pattern STACKS_INPUT = Pattern.compile(
            "(\\d{1,5})\\s*(?:ст|с|st|s|:)\\.?\\s*(?:(\\d{1,2})\\s*(?:ар|ar)?)?");

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

    /**
     * Reads an amount typed into the transfer form, in either format, whatever the setting.
     *
     * @return the amount in АР, or {@code null} if {@code input} is not one: also for a leftover
     *         of 64 or more after the stacks, which is far likelier a typo than meant
     */
    public static @Nullable Integer parse(String input) {
        String value = input.trim().toLowerCase(Locale.ROOT);
        Matcher total = TOTAL_INPUT.matcher(value);

        if (total.matches()) {
            return Integer.parseInt(total.group(1));
        }

        Matcher stacks = STACKS_INPUT.matcher(value);

        if (!stacks.matches()) {
            return null;
        }

        int rest = stacks.group(2) == null ? 0 : Integer.parseInt(stacks.group(2));

        if (rest >= STACK) {
            return null;
        }

        long amount = Long.parseLong(stacks.group(1)) * STACK + rest;
        return amount > MAX_AMOUNT ? null : (int) amount;
    }

    /** Whether {@code input} could be the start of something {@link #parse} reads, for the field's filter. */
    public static boolean isBeingTyped(String input) {
        return input.toLowerCase(Locale.ROOT).chars().allMatch(c ->
                Character.isDigit(c) || " .:старstar".indexOf(c) >= 0);
    }

    /** Whether {@code input} was written in stacks, so that the hint under it gives the plain number instead. */
    public static boolean isStacks(String input) {
        return !TOTAL_INPUT.matcher(input.trim().toLowerCase(Locale.ROOT)).matches();
    }
}
