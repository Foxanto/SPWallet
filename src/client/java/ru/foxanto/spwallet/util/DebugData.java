package ru.foxanto.spwallet.util;

import net.fabricmc.loader.api.FabricLoader;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.api.CardColor;
import ru.foxanto.spwallet.api.PlayerCard;
import ru.foxanto.spwallet.config.SPWalletConfig;

import java.util.List;
import java.util.Map;

/**
 * Stand-in data for working on the mod without an SPWorlds account.
 *
 * <p>While this is active the wallet opens on any server and the API is never called: card lists,
 * balances and card numbers all come from here. Both the storage and {@code SPWorldsApi} read the
 * same constants, so a debug card always has the same number.
 */
public final class DebugData {
    private static final Card SP_MAIN = new Card("Основная", "debug-sp-main", "debug-token");
    private static final Card SP_SAVINGS = new Card("Копилка", "debug-sp-savings", "debug-token");
    private static final Card SPM_SHOP = new Card("Магазин", "debug-spm-shop", "debug-token");

    private static final Map<SPServer, List<Card>> CARDS = Map.of(
            SPServer.SP, List.of(SP_MAIN, SP_SAVINGS),
            SPServer.SPM, List.of(SPM_SHOP));

    private static final Map<String, String> NUMBERS = Map.of(
            SP_MAIN.id(), "10001",
            SP_SAVINGS.id(), "10002",
            SPM_SHOP.id(), "SH0P1");

    private static final Map<String, CardColor> COLORS = Map.of(
            SP_MAIN.id(), CardColor.BLUE,
            SP_SAVINGS.id(), CardColor.YELLOW,
            SPM_SHOP.id(), CardColor.GREEN);

    /** The balance every debug card reports. */
    public static final int BALANCE = 1337;

    /** What any nickname resolves to while debugging. */
    private static final List<PlayerCard> PLAYER_CARDS = List.of(
            new PlayerCard("Кошелёк", "31337"),
            new PlayerCard("Донаты", "FURRY"));

    private DebugData() {}

    public static boolean enabled() {
        return SPWalletConfig.get().isDebuging || FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    /** The cards to pretend the player has on {@code server}. */
    public static List<Card> cards(SPServer server) {
        return CARDS.getOrDefault(server, List.of());
    }

    /** Card id to card number, for every debug card. */
    public static Map<String, String> numbers() {
        return NUMBERS;
    }

    /** The colour of the debug card {@code id}, sent as an index the way the real API does. */
    public static CardColor color(String id) {
        return COLORS.getOrDefault(id, CardColor.BLUE);
    }

    /** The cards any looked-up nickname is pretended to own. */
    public static List<PlayerCard> playerCards() {
        return PLAYER_CARDS;
    }
}
