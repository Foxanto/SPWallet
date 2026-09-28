package ru.foxanto.spwallet.util;

import net.minecraft.client.sounds.AudioStream;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@code .wav} file from the audios folder, for the sound engine, which only reads Ogg Vorbis by
 * itself. Java decodes the file and converts it to the 16 bit PCM that OpenAL takes.
 */
final class WavAudioStream implements AudioStream {
    private final AudioInputStream stream;
    private final int frameSize;

    WavAudioStream(Path path) throws IOException {
        AudioInputStream source;

        try {
            source = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Not a WAV file Java can read: " + path.getFileName(), e);
        }

        AudioFormat format = source.getFormat();

        if (format.getChannels() > 2) {
            source.close();
            throw new IOException("Only mono and stereo sounds can be played: " + path.getFileName());
        }

        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), 16,
                format.getChannels(), format.getChannels() * 2, format.getSampleRate(), false);

        try {
            this.stream = format.matches(target) ? source : AudioSystem.getAudioInputStream(target, source);
        } catch (IllegalArgumentException e) {
            source.close();
            throw new IOException("Cannot convert " + path.getFileName() + " to 16 bit PCM", e);
        }

        this.frameSize = target.getFrameSize();
    }

    @Override
    public AudioFormat getFormat() {
        return this.stream.getFormat();
    }

    @Override
    public @Nullable ByteBuffer read(int size) throws IOException {
        byte[] bytes = new byte[Math.max(this.frameSize, size - size % this.frameSize)];
        int total = 0;

        while (total < bytes.length) {
            int read = this.stream.read(bytes, total, bytes.length - total);

            if (read < 0) {
                break;
            }

            total += read;
        }

        // Whole frames only: half a sample would shift every sample after it.
        total -= total % this.frameSize;

        if (total == 0) {
            return null;
        }

        // OpenAL takes direct buffers only.
        ByteBuffer buffer = BufferUtils.createByteBuffer(total);
        buffer.put(bytes, 0, total).flip();
        return buffer;
    }

    @Override
    public void close() throws IOException {
        this.stream.close();
    }
}
