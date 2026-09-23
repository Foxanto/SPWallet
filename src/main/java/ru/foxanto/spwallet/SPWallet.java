package ru.foxanto.spwallet;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants of the mod.
 *
 * <p>SPWallet continues SPWorlds Pay by MeiNanziiii (Miracle-Studio), which was released under the
 * MIT license. See NOTICE.md for the original copyright notice.
 */
public final class SPWallet {
    public static final String MOD_ID = "spwallet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private SPWallet() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
