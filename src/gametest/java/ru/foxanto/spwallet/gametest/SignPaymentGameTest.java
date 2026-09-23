package ru.foxanto.spwallet.gametest;

import ru.foxanto.spwallet.util.SignPayment;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks {@link SignPayment} against the sign layouts the mod is supposed to understand, and against
 * ordinary signs it must keep its hands off.
 *
 * <p>The parser is pure string handling, so this needs no world and no client; it is called from
 * {@link ScreenGameTest} simply because that is where the mod's tests run.
 */
final class SignPaymentGameTest {
    private SignPaymentGameTest() {}

    static void run() {
        List<String> failures = new ArrayList<>();

        // Marked, everything on the first line. The amount and the comment are optional.
        expect(failures, "#SPW Foxanto 100 АР за алмазы", "Foxanto", 100, "за алмазы",
                "#SPW Foxanto 100 АР за алмазы", "", "", "");
        expect(failures, "amount without the AP suffix", "Foxanto", 100, "за алмазы",
                "#SPW Foxanto 100 за алмазы", "", "", "");
        expect(failures, "no comment", "Foxanto", 100, "",
                "#SPWP Foxanto 100 АР", "", "", "");
        expect(failures, "no amount and no comment", "Foxanto", 0, "",
                "#SPW Foxanto", "", "", "");
        expect(failures, "card number instead of a nickname", "79907", 64, "",
                "#SPWALLET 79907 64 АР", "", "", "");

        // The SPWorlds Pay layout: marker alone, then the payment below it.
        expect(failures, "SPWorlds Pay layout", "79907", 64, "за алмазы",
                "#SPWP", "79907", "64", "за алмазы");
        expect(failures, "SPWorlds Pay layout without an amount", "79907", 0, "",
                "#SPWP", "79907", "", "");

        // Bordered.
        expect(failures, "bordered", "Foxanto", 1000, "Алмазы",
                "==-Шоп-==", "Алмазы : 1000 АР", "Foxanto", "==-Шоп-==");
        expect(failures, "bordered, no space before the suffix", "79907", 1000, "Алмазы",
                "==-Шоп-==", "Алмазы : 1000АР", "79907", "==-Шоп-==");

        // Plain, amount last.
        expect(failures, "plain", "Foxanto", 64, "Алмазы",
                "Foxanto", "Алмазы", "64 АР", "");
        expect(failures, "plain, no comment", "79907", 64, "",
                "79907", "", "64 AP", "");

        // Amount above, labelled target last.
        expect(failures, "amount above, label last", "79907", 100, "За алмазы",
                "==-Шоп-==", "За алмазы", "100 АР", "Карта: 79907");

        // Labelled target above, amount below.
        expect(failures, "label above, amount below", "Foxanto", 100, "Алмазы",
                "==-Рамка-==", "Перевод Foxanto", "Алмазы : 100 АР", "==-Рамка-==");

        // Comment split over the first two lines, amount tacked onto the second.
        expect(failures, "comment over two lines", "Foxanto", 100, "За алмазы спасибо",
                "За алмазы", "спасибо - 100 АР", "", "Foxanto");
        expect(failures, "target on the third line instead", "79907", 100, "За алмазы",
                "За алмазы", "100 АР", "Карта 79907", "");
        expectNothing(failures, "comment and price but nowhere to send it",
                "За алмазы", "спасибо - 100 АР", "", "");

        // Labelled target on any line, no amount at all.
        expect(failures, "label only, last line", "79907", 0, "",
                "", "", "", "Карта: 79907");
        expect(failures, "label only, first line", "Foxanto", 0, "",
                "Перевод: Foxanto", "", "", "");
        expect(failures, "label without a colon", "Foxanto", 0, "",
                "", "Перевод Foxanto", "", "");

        // Label on one line, target on another, which is how a sign fits the words in.
        expect(failures, "Оплата картой (со скриншота)", "70701", 0, "",
                "Оплата", "картой:", "70701", "");
        expect(failures, "label line, nickname below", "Foxanto", 0, "",
                "Карта:", "Foxanto", "", "");
        expect(failures, "label last, target first", "79907", 0, "",
                "79907", "", "", "Переводом:");
        expect(failures, "label line plus a price", "Foxanto", 100, "За алмазы",
                "За алмазы", "Карточка", "Foxanto", "100 АР");
        expect(failures, "inflected label with a target", "79907", 0, "",
                "", "Картой 79907", "", "");

        // Card numbers that carry letters, which a card with a custom number has.
        expect(failures, "letters instead of digits", "FURRY", 0, "",
                "Карта: FURRY", "", "", "");
        expect(failures, "digits in the middle of the letters", "M0BRO", 250, "За шкуры",
                "За шкуры", "250 АР", "Карта M0BRO", "");
        expect(failures, "a lettered number as a bare target", "SH0P1", 64, "",
                "SH0P1", "", "64 АР", "");

        // Upper case is what tells a card number apart from a five character nickname.
        expectCardNumber(failures, "letters are a number", true, "Карта: FURRY", "", "", "");
        expectCardNumber(failures, "digits are a number", true, "Карта: 70701", "", "", "");
        expectCardNumber(failures, "lower case is a nickname", false, "Перевод: furry", "", "", "");
        expectCardNumber(failures, "a longer name is a nickname", false, "Перевод: Foxanto", "", "", "");

        // Signs that are none of the mod's business.
        expectNothing(failures, "ordinary shop sign", "Магазин Стива", "Алмазы", "64", "");
        expectNothing(failures, "warp sign", "Спавн", "", "100", "");
        expectNothing(failures, "empty sign", "", "", "", "");
        expectNothing(failures, "prices but no payable target", "Алмазы за дёшево", "", "64 АР", "");
        expectNothing(failures, "marker with an unusable target", "#SPW Магазин Стива", "", "", "");
        expectNothing(failures, "a name and nothing else", "Foxanto", "", "", "");
        expectNothing(failures, "a word that looks like a label", "Картофель", "", "64", "");
        expectNothing(failures, "a label but nothing payable", "Оплата", "картой:", "", "");
        expectNothing(failures, "a map of the world", "Карта мира", "Foxanto", "", "");

        if (!failures.isEmpty()) {
            throw new AssertionError("Sign parsing is wrong:\n  " + String.join("\n  ", failures));
        }
    }

    private static void expect(List<String> failures, String what, String target, int amount,
                               String comment, String... lines) {
        SignPayment payment = SignPayment.parse(lines);

        if (payment == null) {
            failures.add(what + ": not recognised as a payment sign");
            return;
        }

        if (!payment.target().equals(target) || payment.amount() != amount
                || !payment.comment().equals(comment)) {
            failures.add(what + ": got " + payment + ", wanted target=" + target
                    + ", amount=" + amount + ", comment=" + comment);
        }
    }

    /** Checks how the target of a sign is classified: a card number, or a nickname to look up. */
    private static void expectCardNumber(List<String> failures, String what, boolean number,
                                         String... lines) {
        SignPayment payment = SignPayment.parse(lines);

        if (payment == null) {
            failures.add(what + ": not recognised as a payment sign");
            return;
        }

        if (payment.targetIsCardNumber() != number) {
            failures.add(what + ": " + payment.target() + " was read as "
                    + (number ? "a nickname, wanted a card number" : "a card number, wanted a nickname"));
        }
    }

    private static void expectNothing(List<String> failures, String what, String... lines) {
        SignPayment payment = SignPayment.parse(lines);

        if (payment != null) {
            failures.add(what + ": should have been left alone, but parsed as " + payment);
        }
    }
}
