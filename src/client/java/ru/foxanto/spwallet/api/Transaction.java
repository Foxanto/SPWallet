package ru.foxanto.spwallet.api;

/** A transfer request body as the SPWorlds public API expects it. */
public record Transaction(String receiver, int amount, String comment) {}
