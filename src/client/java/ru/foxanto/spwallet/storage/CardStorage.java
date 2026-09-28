package ru.foxanto.spwallet.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.util.DebugData;
import ru.foxanto.spwallet.util.SPServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps the player's cards in {@code config/spwallet-cards.json}.
 *
 * <p>SPWorlds Pay used an SQLite database for this. A player owns a handful of cards at most, so
 * plain JSON keeps the same data without a database driver on the client.
 *
 * <p>The same file records which cards are favourites, by card id and across both servers: those
 * are the ones the HUD panel shows. It also records the cards whose incoming money notifications
 * the player turned off.
 */
public class CardStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, List<Card>> cards = new LinkedHashMap<>();

    /** Ids of the cards shown on the HUD, in the order they were picked. */
    private final Set<String> favourites = new LinkedHashSet<>();

    /** Ids of the cards that give no incoming money notification. Every other card does. */
    private final Set<String> muted = new LinkedHashSet<>();

    public CardStorage() {
        this(FabricLoader.getInstance().getConfigDir().resolve("spwallet-cards.json"));
    }

    public CardStorage(Path file) {
        this.file = file;
        this.load();

        if (DebugData.enabled() && this.cards.isEmpty()) {
            // Seeded into the real map rather than returned from cards(), so that selecting,
            // renaming and deleting a debug card all behave like the real thing.
            for (SPServer server : SPServer.values()) {
                if (server.key() != null) {
                    this.cards.put(server.key(), new ArrayList<>(DebugData.cards(server)));
                }
            }
        }
    }

    /** The cards saved for {@code server}, in insertion order. Never modifiable. */
    public List<Card> cards(SPServer server) {
        if (server.key() == null) {
            return List.of();
        }

        return Collections.unmodifiableList(this.cards.computeIfAbsent(server.key(), key -> new ArrayList<>()));
    }

    public void add(SPServer server, Card card) {
        if (server.key() == null) {
            return;
        }

        this.cards.computeIfAbsent(server.key(), key -> new ArrayList<>()).add(card);
        this.save();
    }

    public void remove(SPServer server, Card card) {
        if (server.key() == null) {
            return;
        }

        List<Card> serverCards = this.cards.get(server.key());

        if (serverCards != null && serverCards.remove(card)) {
            this.favourites.remove(card.id());
            this.muted.remove(card.id());
            this.save();
        }
    }

    /** Whether the card {@code cardId} is shown on the HUD. */
    public boolean isFavourite(String cardId) {
        return this.favourites.contains(cardId);
    }

    /** Adds the card to the HUD or takes it off it, and returns which of the two happened. */
    public boolean toggleFavourite(String cardId) {
        boolean favourite = !this.favourites.remove(cardId);

        if (favourite) {
            this.favourites.add(cardId);
        }

        this.save();
        return favourite;
    }

    /** Whether money coming in to the card {@code cardId} is announced. */
    public boolean notifiesIncoming(String cardId) {
        return !this.muted.contains(cardId);
    }

    /** Turns the card's incoming money notifications on or off, and returns which it is now. */
    public boolean toggleIncoming(String cardId) {
        boolean notifies = this.muted.remove(cardId);

        if (!notifies) {
            this.muted.add(cardId);
        }

        this.save();
        return notifies;
    }

    /** Renames {@code card}, keeping its position in the list. */
    public void rename(SPServer server, Card card, String newName) {
        if (server.key() == null) {
            return;
        }

        List<Card> serverCards = this.cards.get(server.key());

        if (serverCards == null) {
            return;
        }

        int index = serverCards.indexOf(card);

        if (index >= 0) {
            serverCards.set(index, new Card(newName, card.id(), card.token()));
            this.save();
        }
    }

    /** Whether a card with the same id is already stored for {@code server}. */
    public boolean contains(SPServer server, String cardId) {
        return this.cards(server).stream().anyMatch(card -> card.id().equals(cardId));
    }

    public final void load() {
        this.cards.clear();
        this.favourites.clear();
        this.muted.clear();

        if (!Files.exists(this.file)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            StoredCards stored = GSON.fromJson(reader, StoredCards.class);

            if (stored != null && stored.favourites != null) {
                stored.favourites.stream().filter(Objects::nonNull).forEach(this.favourites::add);
            }

            if (stored != null && stored.mutedIncoming != null) {
                stored.mutedIncoming.stream().filter(Objects::nonNull).forEach(this.muted::add);
            }

            if (stored != null && stored.cards != null) {
                stored.cards.forEach((key, list) -> {
                    if (list != null) {
                        List<Card> valid = new ArrayList<>();

                        for (Card card : list) {
                            if (card != null && card.id() != null && card.token() != null) {
                                valid.add(card.name() == null
                                        ? new Card(card.id(), card.id(), card.token())
                                        : card);
                            }
                        }

                        this.cards.put(key, valid);
                    }
                });
            }
        } catch (IOException | JsonSyntaxException e) {
            SPWallet.LOGGER.error("Could not read {}, starting with an empty card list", this.file, e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(this.file.getParent());

            try (Writer writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8)) {
                GSON.toJson(new StoredCards(this.cards, new ArrayList<>(this.favourites), new ArrayList<>(this.muted)), writer);
            }
        } catch (IOException e) {
            SPWallet.LOGGER.error("Could not write {}", this.file, e);
        }
    }

    /**
     * On-disk shape of the card file. A file written before favourites or muted cards existed simply
     * has none of them.
     */
    private static final class StoredCards {
        private Map<String, List<Card>> cards;
        private List<String> favourites;
        private List<String> mutedIncoming;

        StoredCards(Map<String, List<Card>> cards, List<String> favourites, List<String> mutedIncoming) {
            this.cards = cards;
            this.favourites = favourites;
            this.mutedIncoming = mutedIncoming;
        }
    }
}
