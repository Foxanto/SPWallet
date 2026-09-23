package ru.foxanto.spwallet.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Finds a QR code in a picture and returns what it says. Knows nothing about Minecraft. */
public final class QrDecoder {
    /**
     * A map is only 128 pixels across, so a QR code on it has modules one or two pixels wide and
     * often runs right up to the edge. ZXing wants a quiet zone around the code and a few pixels per
     * module, so a map is scaled up and framed in white before it is read.
     */
    private static final int MAP_SCALE = 4;
    private static final int MAP_BORDER = 8 * MAP_SCALE;

    private static final List<Function<LuminanceSource, Binarizer>> BINARIZERS =
            List.of(HybridBinarizer::new, GlobalHistogramBinarizer::new);

    private QrDecoder() {}

    /**
     * Reads a picture taken from the screen.
     *
     * @param argb the pixels, row by row, as {@code 0xAARRGGBB}
     */
    public static @Nullable String decode(int[] argb, int width, int height) {
        return decode(new RGBLuminanceSource(width, height, argb));
    }

    /**
     * Reads the pixels of a single map, enlarged and framed in white.
     *
     * @param argb the map's pixels, row by row, as {@code 0xAARRGGBB}; transparent pixels are
     *             read as white, which is how an empty map area looks in an item frame on a wall
     */
    public static @Nullable String decodeMap(int[] argb, int size) {
        int scaledSize = size * MAP_SCALE + MAP_BORDER * 2;
        int[] scaled = new int[scaledSize * scaledSize];
        Arrays.fill(scaled, 0xFFFFFFFF);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int pixel = argb[y * size + x];
                int opaque = (pixel >>> 24) == 0 ? 0xFFFFFFFF : pixel | 0xFF000000;

                for (int dy = 0; dy < MAP_SCALE; dy++) {
                    int row = (MAP_BORDER + y * MAP_SCALE + dy) * scaledSize + MAP_BORDER + x * MAP_SCALE;
                    Arrays.fill(scaled, row, row + MAP_SCALE, opaque);
                }
            }
        }

        return decode(new RGBLuminanceSource(scaledSize, scaledSize, scaled));
    }

    private static @Nullable String decode(LuminanceSource source) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        // Map art is as often light on dark as dark on light.
        hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);

        String text = tryBinarizers(source, hints);

        if (text == null) {
            // A picture that is nothing but the code, edge to edge, reads better this way.
            hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
            text = tryBinarizers(source, hints);
        }

        return text;
    }

    private static @Nullable String tryBinarizers(LuminanceSource source, Map<DecodeHintType, Object> hints) {
        MultiFormatReader reader = new MultiFormatReader();

        for (Function<LuminanceSource, Binarizer> binarizer : BINARIZERS) {
            try {
                return reader.decode(new BinaryBitmap(binarizer.apply(source)), hints).getText();
            } catch (ReaderException ignored) {
                // Not found with this binarizer; try the next one.
            } finally {
                reader.reset();
            }
        }

        return null;
    }
}
