package ru.foxanto.spwallet.api;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a card number looks like.
 *
 * <p>A number used to be five digits. A card can now carry letters instead, so {@code 70701},
 * {@code FURRY} and {@code M0BRO} are all numbers: still exactly five characters, but any mix of
 * digits and capital letters. Lower case never appears in one, which is what tells a number apart
 * from a nickname that happens to be five characters long.
 */
public final class CardNumber {
    /** How many characters a card number has, always. */
    public static final int LENGTH = 5;

    /** A card number as the API writes it. */
    private static final Pattern PATTERN = Pattern.compile("[A-Z0-9]{" + LENGTH + "}");

    /**
     * What a player is allowed to type into a card number field. Lower case is accepted there and
     * upper cased as it is typed, because nobody wants to hold shift for five characters.
     */
    private static final Pattern TYPED = Pattern.compile("[A-Za-z0-9]{0," + LENGTH + "}");

    private CardNumber() {}

    /** Whether {@code value} is a card number rather than a nickname or anything else. */
    public static boolean is(String value) {
        return PATTERN.matcher(value).matches();
    }

    /** Whether {@code value} is worth keeping in a card number field while it is being typed. */
    public static boolean isBeingTyped(String value) {
        return TYPED.matcher(value).matches();
    }

    /** The upper case form of a typed number, which is the only form the API knows. */
    public static String normalize(String value) {
        return value.toUpperCase(Locale.ROOT);
    }
}
