package ru.foxanto.spwallet.util;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.SPWallet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * A sound the game knows no event for: a file from the audios folder, or an {@code .ogg} a resource
 * pack put in {@code assets/spwallet/sounds/custom/} without a {@code sounds.json} entry.
 *
 * <p>It plays as {@code spwallet:custom}, which {@code sounds.json} declares as an empty streamed
 * sound, and hands the sound engine its own audio through Fabric's
 * {@link net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance#getAudioStream}.
 */
final class CustomSoundInstance extends AbstractSoundInstance {
    private static final Identifier EVENT = SPWallet.id("custom");

    private final @Nullable Path file;
    private final @Nullable Identifier resource;

    private CustomSoundInstance(@Nullable Path file, @Nullable Identifier resource, float volume) {
        super(EVENT, SoundSource.UI, SoundInstance.createUnseededRandom());
        this.file = file;
        this.resource = resource;
        this.volume = volume;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    static CustomSoundInstance ofFile(Path file, float volume) {
        return new CustomSoundInstance(file, null, volume);
    }

    /** @param location the sound's id as {@code sounds.json} would name it, e.g. {@code spwallet:custom/coin} */
    static CustomSoundInstance ofResource(Identifier location, float volume) {
        return new CustomSoundInstance(null, location, volume);
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
        if (this.resource != null) {
            return loader.getStream(Sound.SOUND_LISTER.idToFile(this.resource), false);
        }

        Path path = this.file;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return open(path);
            } catch (IOException e) {
                // The engine drops a failed stream without a word, so this is the only trace of it.
                SPWallet.LOGGER.warn("Could not play the sound {}", path, e);
                throw new RuntimeException(e);
            }
        });
    }

    private static AudioStream open(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".wav")) {
            return new WavAudioStream(path);
        }

        return new JOrbisAudioStream(new BufferedInputStream(Files.newInputStream(path)));
    }
}
