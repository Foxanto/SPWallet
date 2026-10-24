package ru.foxanto.spwallet.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.util.DebugData;
import ru.foxanto.spwallet.util.SPServer;
import ru.foxanto.spwallet.util.TransferMode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saved payments, such as "rent: Foxanto, 2 stacks, October", kept in
 * {@code config/spwallet-templates.json}. One click on a template fills the transfer form in; it is
 * still sent the usual way, so a template never moves money on its own.
 *
 * <p>Like the history, nothing is written while debugging.
 */
public class TemplateStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * One saved payment.
     *
     * @param server the key of the server it is for, as {@link SPServer#key()}
     * @param mode which form it fills in
     * @param target the card number, or the nickname for {@link TransferMode#NICKNAME}
     * @param number for a nickname, the card of theirs it went to, picked again when their cards
     *        are looked up
     * @param amount in АР, or {@code 0} to leave the amount to be typed each time
     */
    public record Template(String name, String server, TransferMode mode, String target, @Nullable String number,
                           int amount, @Nullable String comment) {}

    private final Path file;
    private final boolean persistent;
    private final List<Template> templates = new ArrayList<>();
    private int version;

    public TemplateStorage() {
        this(FabricLoader.getInstance().getConfigDir().resolve("spwallet-templates.json"), !DebugData.enabled());
    }

    /** @param persistent whether {@code file} is read and written, or everything stays in memory */
    public TemplateStorage(Path file, boolean persistent) {
        this.file = file;
        this.persistent = persistent;

        if (persistent) {
            this.load();
        }
    }

    /** The templates of {@code server}, in the order they were saved. */
    public List<Template> templates(SPServer server) {
        return this.templates.stream().filter(template -> template.server().equals(server.key())).toList();
    }

    /** Goes up by one with every change, like {@link HistoryStorage#version()}. */
    public int version() {
        return this.version;
    }

    /** Saves {@code template}, replacing the one of the same name on the same server. */
    public void add(Template template) {
        int index = this.indexOf(template.server(), template.name());

        if (index >= 0) {
            this.templates.set(index, template);
        } else {
            this.templates.add(template);
        }

        this.changed();
    }

    public void remove(Template template) {
        if (this.templates.remove(template)) {
            this.changed();
        }
    }

    private int indexOf(String server, String name) {
        for (int i = 0; i < this.templates.size(); i++) {
            Template template = this.templates.get(i);

            if (template.server().equals(server) && template.name().equalsIgnoreCase(name)) {
                return i;
            }
        }

        return -1;
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
            StoredTemplates stored = GSON.fromJson(reader, StoredTemplates.class);

            if (stored != null && stored.templates != null) {
                stored.templates.stream()
                        .filter(template -> template != null && template.name() != null && template.server() != null
                                && template.mode() != null && template.target() != null)
                        .forEach(this.templates::add);
            }
        } catch (IOException | JsonParseException e) {
            SPWallet.LOGGER.error("Could not read {}, starting with no templates", this.file, e);
        }
    }

    private void save() {
        if (!this.persistent) {
            return;
        }

        try {
            Files.createDirectories(this.file.getParent());

            try (Writer writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8)) {
                GSON.toJson(new StoredTemplates(this.templates), writer);
            }
        } catch (IOException e) {
            SPWallet.LOGGER.error("Could not write {}", this.file, e);
        }
    }

    /** On-disk shape of the templates file. */
    private static final class StoredTemplates {
        private List<Template> templates;

        StoredTemplates(List<Template> templates) {
            this.templates = templates;
        }
    }
}
