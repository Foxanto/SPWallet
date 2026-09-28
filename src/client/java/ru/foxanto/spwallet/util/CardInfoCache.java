package ru.foxanto.spwallet.util;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.api.AccountCard;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.api.SPWorldsApi;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.overlay.IncomingNotifications;
import ru.foxanto.spwallet.storage.CardStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Balances, numbers and colours of the saved cards, for the panels that show them outside the
 * wallet: the HUD and the inventory.
 *
 * <p>Those panels are drawn every frame, so they read from here and never call the API themselves.
 * A value is fetched when it is first asked for and again once it is older than its TTL. With the
 * whole API capped at 200 requests a minute, a balance a minute per card leaves plenty of room.
 *
 * <p>It is also where incoming money is noticed, for as long as the API cannot list transactions:
 * a balance that comes back higher than the one before means money came in, and
 * {@link IncomingNotifications} says so. For that the balances of all the cards on the current server
 * are kept fresh by {@link #pollBalances()}, not only those a panel shows.
 *
 * <p>Only touched from the render thread: every answer is handed back to it before it is stored.
 */
public final class CardInfoCache {
    private static final long BALANCE_TTL_MS = 60_000;
    private static final long ACCOUNT_TTL_MS = 5 * 60_000;

    private static final Map<String, Integer> BALANCES = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final Map<String, Long> BALANCE_FETCHED = new HashMap<>();
    private static final Set<String> BALANCE_LOADING = new HashSet<>();
    /** When a balance was last learned from a transfer, which beats any request sent before it. */
    private static final Map<String, Long> BALANCE_CHANGED = new HashMap<>();

    private static final Map<String, AccountCard> ACCOUNT_CARDS = new HashMap<>();
    private static final Map<SPServer, Long> ACCOUNT_FETCHED = new HashMap<>();
    private static final Set<SPServer> ACCOUNT_LOADING = new HashSet<>();

    /**
     * One card as a panel shows it.
     *
     * @param id the card id, which is what a favourite is remembered by
     * @param balance the balance, or {@code null} while it is loading or when it failed
     * @param number the card number, or {@code null} until {@code /accounts/me} has answered
     * @param color the card's colour, or {@code null} when it is not known
     * @param favourite whether the card is one of those the HUD shows
     */
    public record Row(String id, String name, @Nullable Integer balance, boolean failed,
                      @Nullable String number, @Nullable Integer color, boolean favourite) {}

    private CardInfoCache() {}

    /**
     * The server whose cards are shown, or {@code null} when there is none: off SPWorlds the panels
     * stay hidden, except while debugging, where they show the SP cards like the wallet does.
     */
    public static @Nullable SPServer server() {
        SPServer server = SPServer.current();

        if (server != SPServer.OTHER) {
            return server;
        }

        return DebugData.enabled() ? SPServer.SP : null;
    }

    /** The saved cards of {@code server}, fetching whatever is missing or stale on the way. */
    public static List<Row> rows(SPServer server) {
        return rows(server, false);
    }

    /**
     * Only the cards the player put on the HUD, which is all the HUD panel draws. None of them
     * means no panel at all, which is how the HUD is turned on in the first place.
     */
    public static List<Row> favouriteRows(SPServer server) {
        return rows(server, true);
    }

    private static List<Row> rows(SPServer server, boolean favouritesOnly) {
        CardStorage storage = SPWalletClient.cards();

        if (storage == null) {
            return List.of();
        }

        List<Card> cards = storage.cards(server);
        long now = System.currentTimeMillis();

        // Filtered before the balances are refreshed, so a card that is not on the HUD costs no
        // request while the player is simply walking around.
        if (favouritesOnly) {
            cards = cards.stream().filter(card -> storage.isFavourite(card.id())).toList();
        }

        if (!cards.isEmpty()) {
            refreshAccount(server, cards.get(0), now);
        }

        List<Row> rows = new ArrayList<>(cards.size());

        for (Card card : cards) {
            refreshBalance(card, now);

            AccountCard account = ACCOUNT_CARDS.get(card.id());
            rows.add(new Row(card.id(), card.name(), BALANCES.get(card.id()), FAILED.contains(card.id()),
                    account == null ? null : account.number(),
                    account == null ? null : account.color(),
                    storage.isFavourite(card.id())));
        }

        return rows;
    }

    /**
     * Records a balance learned elsewhere, such as the one a transfer answers with. Never a
     * notification: the player's own transfer only ever lowers the balance.
     */
    public static void balanceChanged(Card card, int balance) {
        long now = System.currentTimeMillis();
        BALANCES.put(card.id(), balance);
        FAILED.remove(card.id());
        BALANCE_FETCHED.put(card.id(), now);
        BALANCE_CHANGED.put(card.id(), now);
    }

    /**
     * Keeps every card of the current server checked once a minute while incoming notifications
     * are on, whether or not a panel shows it. Called every tick; the TTL decides when a request
     * actually goes out, so it costs one request per card a minute.
     */
    public static void pollBalances() {
        CardStorage storage = SPWalletClient.cards();

        if (!SPWalletConfig.get().incomingNotifications || storage == null || Minecraft.getInstance().player == null) {
            return;
        }

        SPServer server = server();

        if (server == null) {
            return;
        }

        long now = System.currentTimeMillis();

        for (Card card : storage.cards(server)) {
            refreshBalance(card, now);
        }
    }

    /**
     * Stores a balance the API answered with for a request sent at {@code requestedAt}, and reports
     * a rise over the balance known before as incoming money.
     *
     * <p>An answer to a request sent before the player's own transfer went through is dropped: it
     * holds the balance from before the transfer, and taking it would look like the money coming
     * back. Public for the game test, which has no API to get a rise from.
     */
    public static void balanceFetched(Card card, int balance, long requestedAt) {
        String id = card.id();
        Long changed = BALANCE_CHANGED.get(id);

        if (changed != null && changed >= requestedAt) {
            return;
        }

        Integer previous = BALANCES.put(id, balance);
        FAILED.remove(id);

        // Nothing to compare with on the first answer after the game starts.
        if (previous != null && balance > previous) {
            AccountCard account = ACCOUNT_CARDS.get(id);
            IncomingNotifications.push(card.name(), account == null ? null : account.color(),
                    balance - previous, balance);
        }
    }

    private static void refreshBalance(Card card, long now) {
        String id = card.id();
        Long fetched = BALANCE_FETCHED.get(id);

        if (BALANCE_LOADING.contains(id) || (fetched != null && now - fetched < BALANCE_TTL_MS)) {
            return;
        }

        BALANCE_LOADING.add(id);
        Minecraft client = Minecraft.getInstance();

        SPWorldsApi.balance(card).whenCompleteAsync((balance, error) -> {
            BALANCE_LOADING.remove(id);
            // A failure waits out the TTL too, so a bad token is not retried every frame.
            BALANCE_FETCHED.put(id, System.currentTimeMillis());

            if (error != null) {
                SPWallet.LOGGER.warn("Could not read the balance of card {}", id, error);
                FAILED.add(id);
            } else {
                balanceFetched(card, balance, now);
            }
        }, client);
    }

    private static void refreshAccount(SPServer server, Card anyCard, long now) {
        Long fetched = ACCOUNT_FETCHED.get(server);

        if (ACCOUNT_LOADING.contains(server) || (fetched != null && now - fetched < ACCOUNT_TTL_MS)) {
            return;
        }

        ACCOUNT_LOADING.add(server);
        Minecraft client = Minecraft.getInstance();

        SPWorldsApi.accountCards(anyCard).whenCompleteAsync((cards, error) -> {
            ACCOUNT_LOADING.remove(server);
            ACCOUNT_FETCHED.put(server, System.currentTimeMillis());

            if (error != null) {
                SPWallet.LOGGER.warn("Could not read the cards of the account", error);
            } else {
                ACCOUNT_CARDS.putAll(cards);
            }
        }, client);
    }
}
