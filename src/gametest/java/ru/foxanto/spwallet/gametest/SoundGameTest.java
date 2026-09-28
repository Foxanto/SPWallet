package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.gui.SoundPickerScreen;
import ru.foxanto.spwallet.util.PaymentSound;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * The payment sound from each place it can come from: the built-in chime, an {@code .ogg} the test
 * mod carries in {@code assets/spwallet/sounds/custom/} as a resource pack would, and an
 * {@code .ogg} and a {@code .wav} dropped into the audios folder.
 *
 * <p>Whether the files decode is only visible in the log: the sound engine opens streams off the
 * render thread and a failure there leaves a "Could not play the sound" warning behind.
 */
public class SoundGameTest implements FabricClientGameTest {
    private static final String OGG = "test_copy.ogg";
    private static final String WAV = "test_tone.wav";

    @Override
    public void runTest(ClientGameTestContext context) {
        Path folder = PaymentSound.folder();
        writeTestFiles(folder);

        List<String> expected = List.of(
                PaymentSound.DEFAULT,
                PaymentSound.DEFAULT_INCOMING,
                "pack:" + SPWallet.id("custom/test_pack_sound"),
                "file:" + OGG,
                "file:" + WAV);

        for (String id : expected) {
            boolean started = context.computeOnClient(client -> {
                if (PaymentSound.find(id) == null) {
                    throw new AssertionError("The sound " + id + " is not on the list: " + PaymentSound.options());
                }

                return PaymentSound.preview(id);
            });

            if (!started) {
                throw new AssertionError("The sound engine did not start " + id);
            }

            context.waitTicks(10);
        }

        // A name in the config that points out of the audios folder is never played.
        boolean escaped = context.computeOnClient(client -> PaymentSound.preview("file:../spwallet.json5"));

        if (escaped) {
            throw new AssertionError("A path outside the audios folder was played");
        }

        context.setScreen(() -> new SoundPickerScreen(null, PaymentSound.Slot.INCOMING));
        context.waitTicks(2);
        context.takeScreenshot("spwallet_sound_picker");
        context.setScreen(() -> null);
        context.runOnClient(client -> PaymentSound.stopPreview());
    }

    private static void writeTestFiles(Path folder) {
        try {
            Files.createDirectories(folder);

            try (InputStream ogg = Minecraft.class.getClassLoader()
                    .getResourceAsStream("assets/spwallet/sounds/payment_success.ogg")) {
                if (ogg == null) {
                    throw new AssertionError("The mod's own chime is missing from the classpath");
                }

                Files.copy(ogg, folder.resolve(OGG), StandardCopyOption.REPLACE_EXISTING);
            }

            // Half a second of a quiet 880 Hz tone, mono 16 bit.
            float rate = 44100;
            byte[] samples = new byte[(int) rate];

            for (int i = 0; i < samples.length / 2; i++) {
                short value = (short) (Math.sin(2 * Math.PI * 880 * i / rate) * 4000);
                samples[i * 2] = (byte) value;
                samples[i * 2 + 1] = (byte) (value >> 8);
            }

            AudioFormat format = new AudioFormat(rate, 16, 1, true, false);
            AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(samples), format, samples.length / 2),
                    AudioFileFormat.Type.WAVE, folder.resolve(WAV).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
