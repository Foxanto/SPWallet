package ru.foxanto.spwallet.api;

/**
 * A card belonging to some player, as {@code /accounts/{username}/cards} reports it.
 *
 * <p>Only the name and the number are public; this is what a transfer is addressed to.
 */
public record PlayerCard(String name, String number) {}
