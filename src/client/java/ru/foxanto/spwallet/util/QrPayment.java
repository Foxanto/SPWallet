package ru.foxanto.spwallet.util;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sorts the text of a QR code: a transfer, a link, or just text.
 *
 * <p>A transfer is written the way it would be on a payment sign, so the same rules read it:
 *
 * <pre>
 * Карта 79907                      label and target
 * Перевод Foxanto 100 АР за алмазы label, target, then an optional amount and comment
 * Карта: 79907                     on several lines, like a sign
 * 100 АР
 * </pre>
 *
 * <p>A link is never a transfer, whatever its address says; the caller offers to open it.
 */
public final class QrPayment {
    /**
     * One line that starts with a label and a target and carries the rest after them. A sign has to
     * put these on separate lines; a QR code usually does not, so the line is split before
     * {@link SignPayment} reads it. Whether the first word really is a label is left to it.
     */
    private static final Pattern ONE_LINE = Pattern.compile(
            "^(\\p{L}+\\s*:?\\s*[A-Za-z0-9_]{3,16})(?:[\\s,;]+(.*))?$",
            Pattern.UNICODE_CASE);

    private QrPayment() {}

    public static @Nullable SignPayment parse(String text) {
        String trimmed = text.trim();

        if (trimmed.isEmpty() || isLink(trimmed)) {
            return null;
        }

        String[] lines = trimmed.split("\\R");

        // More lines than a sign has is some other kind of text, not a transfer.
        if (lines.length > 4) {
            return null;
        }

        SignPayment payment = SignPayment.parse(lines);

        if (payment != null || lines.length > 1) {
            return payment;
        }

        Matcher line = ONE_LINE.matcher(trimmed);

        if (!line.matches() || line.group(2) == null) {
            return null;
        }

        return SignPayment.parse(new String[] {line.group(1), line.group(2)});
    }

    /** Whether the text is a web link, which is worth offering to open. */
    public static boolean isLink(String text) {
        String lower = text.trim().toLowerCase(Locale.ROOT);
        return (lower.startsWith("https://") || lower.startsWith("http://")) && !lower.contains(" ");
    }
}
