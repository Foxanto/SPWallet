package ru.foxanto.spwallet.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.config.SPWalletConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The mod's sounds - one when a transfer goes through, one when money comes in (see {@link Slot}) -
 * and the sounds the player can pick them from.
 *
 * <p>There are three places a sound can come from, and the config keeps the choice as a string that
 * says which:
 * <ul>
 *     <li>{@code event:<id>} - a sound event, either one of the {@link #BUILT_IN} ones or any event a
 *     resource pack declares in the {@code spwallet} namespace of its {@code sounds.json};</li>
 *     <li>{@code pack:<id>} - an {@code .ogg} a resource pack put in
 *     {@code assets/spwallet/sounds/custom/}, no {@code sounds.json} needed;</li>
 *     <li>{@code file:<name>} - an {@code .ogg} or {@code .wav} file in the {@link #folder() audios
 *     folder}.</li>
 * </ul>
 *
 * <p>The mod's own chime is {@code spwallet:payment_success}, so a resource pack can also simply
 * replace it under that name.
 *
 * <p>The events are deliberately not put in the sound registry. The sound manager looks sounds up by
 * id in {@code sounds.json} alone, and a registry entry from a client-only mod is one more thing
 * that could disagree with a Fabric server during registry sync.
 */
public final class PaymentSound {
    public static final String DEFAULT = "event:" + SPWallet.id("payment_success");
    public static final String DEFAULT_INCOMING = "event:" + SPWallet.id("notification");

    private static final String EVENT = "event:";
    private static final String PACK = "pack:";
    private static final String FILE = "file:";

    /** Where resource packs put loose {@code .ogg} files for the mod. */
    private static final FileToIdConverter PACK_SOUNDS = new FileToIdConverter("sounds/custom", ".ogg");

    private static final List<String> FILE_EXTENSIONS = List.of(".ogg", ".wav");

    /** The sounds on offer without anything added: the mod's chime and a few fitting vanilla ones. */
    private static final List<Built> BUILT_IN = List.of(
            new Built(SPWallet.id("payment_success"), "default"),
            new Built(SPWallet.id("notification"), "notification"),
            new Built(Identifier.withDefaultNamespace("entity.experience_orb.pickup"), "experience"),
            new Built(Identifier.withDefaultNamespace("entity.player.levelup"), "level_up"),
            new Built(Identifier.withDefaultNamespace("block.note_block.bell"), "bell"),
            new Built(Identifier.withDefaultNamespace("block.amethyst_block.chime"), "amethyst"),
            new Built(Identifier.withDefaultNamespace("entity.villager.yes"), "villager"));

    /** What a sound is played for; each has its own choice in the config. */
    public enum Slot {
        /** A transfer the player made went through. */
        PAYMENT,
        /** Money came in to one of the player's cards. */
        INCOMING;

        public Component title() {
            return Component.translatable("gui.spwallet.title.sound_picker." + this.name().toLowerCase(Locale.ROOT));
        }

        public String choice() {
            SPWalletConfig config = SPWalletConfig.get();
            return this == PAYMENT ? config.paymentSoundChoice : config.incomingSoundChoice;
        }

        public void choose(String id) {
            SPWalletConfig config = SPWalletConfig.get();

            if (this == PAYMENT) {
                config.paymentSoundChoice = id;
            } else {
                config.incomingSoundChoice = id;
            }
        }

        private boolean enabled() {
            SPWalletConfig config = SPWalletConfig.get();
            return this == PAYMENT ? config.paymentSound : config.incomingSound;
        }

        private String fallback() {
            return this == PAYMENT ? DEFAULT : DEFAULT_INCOMING;
        }
    }

    /** Where a sound on the list comes from, for the label beside its name. */
    public enum Origin {
        BUILT_IN, RESOURCE_PACK, FOLDER;

        public Component label() {
            return Component.translatable("gui.spwallet.sound.origin." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    /** A sound the player can pick, with the string it is saved in the config as. */
    public record Option(String id, Component name, Origin origin) {}

    private record Built(Identifier event, String key) {}

    /** The preview playing in the picker, stopped when another one starts. */
    private static @Nullable SoundInstance preview;

    private PaymentSound() {}

    /** The folder the player drops their own sounds into: {@code config/spwallet/audios}. */
    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("spwallet").resolve("audios");
    }

    /** Creates the audios folder with a note on what goes in it, so the player can find it. */
    public static void createFolder() {
        Path folder = folder();

        try {
            Files.createDirectories(folder);
            Path readme = folder.resolve("README.txt");

            if (Files.notExists(readme)) {
                Files.writeString(readme, """
                        SPWallet: свои звуки / your own sounds

                        RU: Положите сюда файлы .ogg или .wav (моно или стерео) и выберите их в настройках
                        мода: "Звук оплаты". Список обновляется кнопкой "Обновить", перезапуск не нужен.

                        Звуки можно добавить и ресурспаком: положите .ogg в
                        assets/spwallet/sounds/custom/ - они тоже появятся в списке (после F3+T).

                        EN: Put .ogg or .wav files (mono or stereo) here and pick them in the mod's settings
                        under "Payment sound". The "Refresh" button rescans the folder, no restart needed.

                        Resource packs can add sounds too: put .ogg files in
                        assets/spwallet/sounds/custom/ and they show up in the list as well (after F3+T).
                        """, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            SPWallet.LOGGER.warn("Could not create the audios folder {}", folder, e);
        }
    }

    /** Every sound the player can pick right now, the built-in ones first. */
    public static List<Option> options() {
        List<Option> options = new ArrayList<>();

        for (Built built : BUILT_IN) {
            options.add(new Option(EVENT + built.event(),
                    Component.translatable("gui.spwallet.sound.built_in." + built.key()), Origin.BUILT_IN));
        }

        Minecraft client = Minecraft.getInstance();
        SoundManager sounds = client.getSoundManager();

        // Events a resource pack added to the mod's sounds.json, other than the ones already listed.
        sounds.getAvailableSounds().stream()
                .filter(id -> id.getNamespace().equals(SPWallet.MOD_ID))
                .filter(id -> !id.getPath().equals("custom"))
                .filter(id -> BUILT_IN.stream().noneMatch(built -> built.event().equals(id)))
                .sorted(Comparator.comparing(Identifier::getPath))
                .forEach(id -> options.add(new Option(EVENT + id, Component.literal(id.getPath()), Origin.RESOURCE_PACK)));

        // Loose .ogg files in assets/spwallet/sounds/custom/ of any resource pack.
        Map<Identifier, Resource> files = PACK_SOUNDS.listMatchingResources(client.getResourceManager());
        files.keySet().stream()
                .filter(file -> file.getNamespace().equals(SPWallet.MOD_ID))
                .sorted(Comparator.comparing(Identifier::getPath))
                .forEach(file -> {
                    Identifier location = Sound.SOUND_LISTER.fileToId(file);
                    options.add(new Option(PACK + location,
                            Component.literal(PACK_SOUNDS.fileToId(file).getPath()), Origin.RESOURCE_PACK));
                });

        for (Path file : folderFiles()) {
            String name = file.getFileName().toString();
            options.add(new Option(FILE + name, Component.literal(name), Origin.FOLDER));
        }

        return options;
    }

    /** The sound the config names, or {@code null} when it is gone: a deleted file, a removed pack. */
    public static @Nullable Option find(String id) {
        return options().stream().filter(option -> option.id().equals(id)).findFirst().orElse(null);
    }

    /** Plays the chosen sound for a transfer that went through, if the player wants one at all. */
    public static void playSuccess() {
        play(Slot.PAYMENT);
    }

    /** Plays the chosen sound for money that came in, if the player wants one at all. */
    public static void playIncoming() {
        play(Slot.INCOMING);
    }

    private static void play(Slot slot) {
        if (!slot.enabled()) {
            return;
        }

        float volume = SPWalletConfig.get().paymentSoundVolume;
        SoundInstance sound = instance(slot.choice(), volume);

        if (sound == null) {
            SPWallet.LOGGER.warn("The sound {} is gone, playing the default one", slot.choice());
            sound = instance(slot.fallback(), volume);
        }

        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    /**
     * Plays a sound from the picker, cutting off the one played before it.
     *
     * @return whether the sound engine took it, which it does not for a sound that is gone
     */
    public static boolean preview(String id) {
        stopPreview();
        preview = instance(id, SPWalletConfig.get().paymentSoundVolume);

        return preview != null
                && Minecraft.getInstance().getSoundManager().play(preview) != SoundEngine.PlayResult.NOT_STARTED;
    }

    public static void stopPreview() {
        if (preview != null) {
            Minecraft.getInstance().getSoundManager().stop(preview);
            preview = null;
        }
    }

    private static @Nullable SoundInstance instance(String id, float volume) {
        Minecraft client = Minecraft.getInstance();

        if (id.startsWith(EVENT)) {
            Identifier event = Identifier.tryParse(id.substring(EVENT.length()));

            if (event == null || client.getSoundManager().getSoundEvent(event) == null) {
                return null;
            }

            return SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(event), 1.0F, volume);
        }

        if (id.startsWith(PACK)) {
            Identifier location = Identifier.tryParse(id.substring(PACK.length()));

            if (location == null
                    || client.getResourceManager().getResource(Sound.SOUND_LISTER.idToFile(location)).isEmpty()) {
                return null;
            }

            return CustomSoundInstance.ofResource(location, volume);
        }

        if (id.startsWith(FILE)) {
            Path folder = folder().toAbsolutePath().normalize();
            Path file = folder.resolve(id.substring(FILE.length())).normalize();

            // The name comes from the config file, which is not to be trusted to stay in the folder.
            if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
                return null;
            }

            return CustomSoundInstance.ofFile(file, volume);
        }

        return null;
    }

    private static List<Path> folderFiles() {
        Path folder = folder();

        if (!Files.isDirectory(folder)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        return FILE_EXTENSIONS.stream().anyMatch(name::endsWith);
                    })
                    .sorted(Comparator.comparing(file -> file.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            SPWallet.LOGGER.warn("Could not list the audios folder {}", folder, e);
            return List.of();
        }
    }
}
