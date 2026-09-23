package ru.foxanto.spwallet.api;

import org.jetbrains.annotations.Nullable;

/**
 * What {@code /accounts/me} says about one of the owner's cards beyond what is saved in the mod.
 *
 * @param number the card number
 * @param color the card's colour as {@code 0xAARRGGBB}, or {@code null} when the API gave none
 */
public record AccountCard(String number, @Nullable Integer color) {}
