package ru.foxanto.spwallet.util;

import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.api.CardNumber;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a payment sign asks for: who to pay, how much, and why.
 *
 * <p>A sign whose first line carries a marker is read as a whole:
 *
 * <pre>
 * #SPW Ник 100 АР комментарий      marker, then everything on one line
 * #SPWP                            marker alone, then the SPWorlds Pay layout below it
 * 79907
 * 100 АР
 * комментарий
 * </pre>
 *
 * <p>Every other sign is scanned line by line instead of being matched against a fixed layout, so
 * the order of the lines does not matter. Each line is one of:
 *
 * <ul>
 *   <li>a labelled target, {@code Карта: 79907} or {@code Перевод Foxanto};
 *   <li>an amount, recognised by its {@code АР} suffix and picked out of whatever else is on the
 *       line, so {@code за алмазы - 100 АР} gives both an amount and a comment;
 *   <li>a bare card number or nickname, taken as the target when nothing is labelled;
 *   <li>decoration, which is dropped;
 *   <li>anything else, which becomes part of the comment.
 * </ul>
 *
 * <p>A sign only counts as a payment when it names a payable target <em>and</em> carries at least
 * one of: a marker, a labelled target, an amount in AP. Without that rule an ordinary shop sign
 * would be swallowed — the click is cancelled and never reaches the server.
 *
 * @param target a card number or a player nickname
 * @param amount the amount in AP, or {@code 0} when the sign does not say
 * @param comment the comment, possibly empty
 */
public record SignPayment(String target, int amount, String comment) {
    private static final int LINES = 4;

    /** The markers, longest first, so that {@code #SPWP} never matches as {@code #SPW}. */
    private static final Pattern MARKER = Pattern.compile(
            "^#(?:SPWALLET|SPWP|SPW)\\b\\s*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Everything after the marker: target, then an optional amount, then an optional comment. */
    private static final Pattern MARKED_REST = Pattern.compile(
            "^(\\S+)(?:\\s+(\\d{1,7})\\s*(?:АР|AP|AR)?)?(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * The words that announce a target, in the Russian cases people actually write on signs:
     * "карта", "картой", "переводом" and so on.
     */
    private static final String LABELS =
            "карт(?:а|ы|е|у|ой|ою|очка|очки|очке|очку|очкой)?"
                    + "|сч[её]т(?:а|у|е|ом)?"
                    + "|перевод(?:а|у|е|ом)?"
                    + "|оплат(?:а|ы|е|у|ой)"
                    + "|ник(?:а|у|е|ом)?"
                    + "|card|account|transfer|nick|pay(?:ment)?";

    /** {@code Карта: 79907}, {@code Перевод Foxanto} — label and target on one line. */
    private static final Pattern LABELLED_TARGET = Pattern.compile(
            "^(?:" + LABELS + ")(?:\\s*:\\s*|\\s+)(\\S+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * A line that is nothing but a label, such as {@code Оплата картой:}. The target is then on
     * another line of its own, which is how a sign fits the words in at all.
     */
    private static final Pattern LABEL_ONLY = Pattern.compile(
            "^(?:.*\\s)?(?:" + LABELS + ")\\s*:?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** An amount anywhere in a line. The currency suffix is what marks it as one. */
    private static final Pattern AMOUNT = Pattern.compile(
            "(\\d{1,7})\\s*(?:АР|AP|AR)(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** A bare amount, for the line below a lone marker where the suffix is not needed. */
    private static final Pattern BARE_AMOUNT = Pattern.compile(
            "^(\\d{1,7})\\s*(?:АР|AP|AR)?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** A Minecraft name, which is also all the API will take inside a URL. */
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /** A line with nothing but punctuation, or one fenced by it, such as {@code ==-Шоп-==}. */
    private static final Pattern DECORATION = Pattern.compile(
            "^[^\\p{L}\\p{N}]*$|^[=\\-~*_<>|+#]{2,}.*[=\\-~*_<>|+#]{2,}$",
            Pattern.UNICODE_CASE);

    /** Leftovers of a line once the amount has been taken out of it. */
    private static final Pattern TRAILING_SEPARATORS = Pattern.compile("^[\\s:\\-—–,.]+|[\\s:\\-—–,.]+$");

    /** Whether {@code target} names a card rather than a player. */
    public boolean targetIsCardNumber() {
        return CardNumber.is(this.target);
    }

    /**
     * Reads a sign, or returns {@code null} when it is not a payment sign.
     *
     * @param lines the four lines of the sign, already stripped of formatting
     */
    public static @Nullable SignPayment parse(String[] lines) {
        String[] text = new String[LINES];

        for (int i = 0; i < LINES; i++) {
            text[i] = i < lines.length && lines[i] != null ? lines[i].trim() : "";
        }

        SignPayment marked = parseMarked(text);

        return marked != null ? marked : scan(text);
    }

    private static @Nullable SignPayment parseMarked(String[] text) {
        Matcher marker = MARKER.matcher(text[0]);

        if (!marker.matches()) {
            return null;
        }

        String rest = marker.group(1).trim();

        if (!rest.isEmpty()) {
            Matcher parts = MARKED_REST.matcher(rest);

            if (!parts.matches()) {
                return null;
            }

            int amount = parts.group(2) == null ? 0 : Integer.parseInt(parts.group(2));
            return of(parts.group(1), amount, parts.group(3));
        }

        // Marker alone: the SPWorlds Pay layout, where the rest of the sign carries the payment.
        Matcher amount = BARE_AMOUNT.matcher(text[2]);
        return of(text[1], amount.matches() ? Integer.parseInt(amount.group(1)) : 0, text[3]);
    }

    /** Reads an unmarked sign by what each line looks like rather than by where it sits. */
    private static @Nullable SignPayment scan(String[] text) {
        String labelledTarget = null;
        String bareTarget = null;
        boolean labelSeen = false;
        int amount = 0;
        List<String> comment = new ArrayList<>();

        for (String line : text) {
            if (line.isEmpty() || DECORATION.matcher(line).matches()) {
                continue;
            }

            Matcher labelled = LABELLED_TARGET.matcher(line);

            if (labelled.matches() && isPayable(labelled.group(1))) {
                if (labelledTarget == null) {
                    labelledTarget = labelled.group(1);
                }

                labelSeen = true;
                continue;
            }

            // A label with no target on it; whichever line does hold the target is the one meant.
            if (LABEL_ONLY.matcher(line).matches()) {
                labelSeen = true;
                continue;
            }

            Matcher found = AMOUNT.matcher(line);

            if (found.find()) {
                if (amount == 0) {
                    amount = Integer.parseInt(found.group(1));
                }

                String rest = strip(line.substring(0, found.start()) + " " + line.substring(found.end()));

                if (!rest.isEmpty()) {
                    comment.add(rest);
                }

                continue;
            }

            if (isPayable(line)) {
                if (bareTarget == null) {
                    bareTarget = line;
                }

                continue;
            }

            comment.add(line);
        }

        String target = labelledTarget != null ? labelledTarget : bareTarget;

        // Without a label or a price this is just a sign that happens to hold a word.
        if (target == null || (!labelSeen && amount == 0)) {
            return null;
        }

        return of(target, amount, String.join(" ", comment));
    }

    private static boolean isPayable(String value) {
        return CardNumber.is(value) || NICKNAME.matcher(value).matches();
    }

    private static String strip(String value) {
        return TRAILING_SEPARATORS.matcher(value.trim()).replaceAll("").trim();
    }

    /**
     * Builds a payment, or {@code null} when the target is something nobody could be paid: a
     * transfer needs either a card number or a name the API will accept.
     */
    private static @Nullable SignPayment of(@Nullable String target, int amount, @Nullable String comment) {
        String trimmed = target == null ? "" : target.trim();

        if (!isPayable(trimmed)) {
            return null;
        }

        return new SignPayment(trimmed, Math.max(amount, 0), comment == null ? "" : comment.trim());
    }
}
