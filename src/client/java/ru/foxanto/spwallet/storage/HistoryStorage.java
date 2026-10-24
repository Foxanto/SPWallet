package ru.foxanto.spwallet.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.util.DebugData;
import ru.foxanto.spwallet.util.SPServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The operations the mod has seen, kept in {@code config/spwallet-history.json}: the player's own
 * transfers and the money it noticed coming in.
 *
 * <p>The SPWorlds API cannot list transactions, so this is all there is. A transfer is recorded
 * exactly, but money coming in is only a rise of the balance between two checks (see
 * {@link ru.foxanto.spwallet.util.CardInfoCache}): its sender is unknown, and two transfers between
 * checks are one entry.
 *
 * <p>The last balance of every card is saved too, so a rise while the game was closed is still
 * noticed on the first check after it starts, and recorded as having come in while away.
 *
 * <p>Only the newest {@link #MAX_ENTRIES} entries are kept. While debugging nothing is written, so
 * the stubbed transfers never end up in the real history.
 */
public class HistoryStorage {
    public static final int MAX_ENTRIES = 500;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Kind {
        /** A transfer the player sent from the wallet. */
        OUT,
        /** A rise of the balance. */
        IN
    }

    /**
     * One operation.
     *
     * @param time when it happened, in {@link System#currentTimeMillis()} terms; for money that came
     *        in while away, when it was noticed
     * @param server the key of the server the card is on, as {@link SPServer#key()}
     * @param cardName the card's name at the time, which a later rename does not change
     * @param receiver the card number a transfer went to, {@code null} for money coming in
     * @param receiverName the nickname it was sent by, when it was sent that way
     * @param comment what the player wrote, without the signature the mod adds
     * @param balance the card's balance afterwards, when it is known
     * @param away whether the money came in while the game was closed
     */
    public record Entry(long time, String server, String cardId, String cardName, Kind kind, int amount,
                        @Nullable String receiver, @Nullable String receiverName, @Nullable String comment,
                        @Nullable Integer balance, boolean away) {}

    /**
     * Someone the player sent money to, for the list of recent receivers.
     *
     * @param receiverName the nickname it went by, if it was sent that way
     * @param amount how much went the last time
     */
    public record Recent(String receiver, @Nullable String receiverName, int amount, long time) {}

    private final Path file;
    /** Whether the file is read and written at all. */
    private final boolean persistent;
    /** Oldest first, the order they are written in. */
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Integer> lastBalances = new HashMap<>();
    private int version;

    public HistoryStorage() {
        this(FabricLoader.getInstance().getConfigDir().resolve("spwallet-history.json"), !DebugData.enabled());
    }

    /** @param persistent whether {@code file} is read and written, or everything stays in memory */
    public HistoryStorage(Path file, boolean persistent) {
        this.file = file;
        this.persistent = persistent;

        if (persistent) {
            this.load();
        }
    }

    /** The entries of {@code server}, newest first. */
    public List<Entry> entries(SPServer server) {
        List<Entry> result = new ArrayList<>();

        for (int i = this.entries.size() - 1; i >= 0; i--) {
            Entry entry = this.entries.get(i);

            if (entry.server().equals(server.key())) {
                result.add(entry);
            }
        }

        return result;
    }

    /**
     * The receivers of the player's last transfers on {@code server}, newest first, each once: by
     * nickname where one was used, so a player is one entry whichever of their cards was paid.
     */
    public List<Recent> recent(SPServer server, int limit) {
        List<Recent> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Entry entry : this.entries(server)) {
            if (entry.kind() != Kind.OUT || entry.receiver() == null) {
                continue;
            }

            String key = entry.receiverName() == null
                    ? "#" + entry.receiver()
                    : entry.receiverName().toLowerCase(Locale.ROOT);

            if (seen.add(key)) {
                result.add(new Recent(entry.receiver(), entry.receiverName(), entry.amount(), entry.time()));

                if (result.size() == limit) {
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Goes up by one with every change, so a list on screen can tell it has to be built again
     * without comparing the entries.
     */
    public int version() {
        return this.version;
    }

    public void add(Entry entry) {
        this.entries.add(entry);

        while (this.entries.size() > MAX_ENTRIES) {
            this.entries.removeFirst();
        }

        this.changed();
    }

    /** Forgets every entry of {@code server}; the saved balances stay, so nothing is noticed twice. */
    public void clear(SPServer server) {
        this.entries.removeIf(entry -> entry.server().equals(server.key()));
        this.changed();
    }

    /** The balance the card had when it was last checked, in this game or an earlier one. */
    public @Nullable Integer lastBalance(String cardId) {
        return this.lastBalances.get(cardId);
    }

    /**
     * Remembers the card's balance for the next game. Only written out when it changed, which is
     * rarely: the checks run every few seconds and mostly find the same balance.
     */
    public void balance(String cardId, int balance) {
        Integer previous = this.lastBalances.put(cardId, balance);

        if (previous == null || previous != balance) {
            this.save();
        }
    }

    private void changed() {
        this.version++;
        this.save();
    }

    private void load() {
        if (!Files.exists(this.file)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            StoredHistory stored = GSON.fromJson(reader, StoredHistory.class);

            if (stored == null) {
                return;
            }

            if (stored.entries != null) {
                stored.entries.stream()
                        .filter(entry -> entry != null && entry.server() != null && entry.cardId() != null
                                && entry.kind() != null)
                        .forEach(this.entries::add);
            }

            if (stored.lastBalances != null) {
                stored.lastBalances.forEach((id, balance) -> {
                    if (id != null && balance != null) {
                        this.lastBalances.put(id, balance);
                    }
                });
            }
        } catch (IOException | JsonParseException e) {
            SPWallet.LOGGER.error("Could not read {}, starting with an empty history", this.file, e);
        }
    }

    private void save() {
        if (!this.persistent) {
            return;
        }

        try {
            Files.createDirectories(this.file.getParent());

            try (Writer writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8)) {
                GSON.toJson(new StoredHistory(this.entries, this.lastBalances), writer);
            }
        } catch (IOException e) {
            SPWallet.LOGGER.error("Could not write {}", this.file, e);
        }
    }

    /** On-disk shape of the history file. */
    private static final class StoredHistory {
        private List<Entry> entries;
        private Map<String, Integer> lastBalances;

        StoredHistory(List<Entry> entries, Map<String, Integer> lastBalances) {
            this.entries = Objects.requireNonNull(entries);
            this.lastBalances = Objects.requireNonNull(lastBalances);
        }
    }
}
