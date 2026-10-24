package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.config.AmountFormat;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.storage.HistoryStorage.Entry;
import ru.foxanto.spwallet.storage.HistoryStorage.Kind;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * The journal: one block per operation, newest on top, with the amount and when on the first line,
 * the card and the receiver on the second and the comment, if any, under them.
 */
public class HistoryList extends FlowLayout {
    public HistoryList() {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.gap(3);
    }

    /** Replaces the contents with {@code entries}, or with {@code empty} when there are none. */
    public void show(List<Entry> entries, Component empty) {
        this.clearChildren();

        if (entries.isEmpty()) {
            this.child(text(empty, EssentialColors.cardBalance()));
            return;
        }

        for (Entry entry : entries) {
            this.child(row(entry));
        }
    }

    private static FlowLayout row(Entry entry) {
        boolean in = entry.kind() == Kind.IN;

        // One label rather than the time pinned to the right: a long amount and a date would run
        // into each other in a narrow column, where this just wraps the time onto the next line.
        Component first = Component.literal(in ? "+" : "−").append(AmountFormat.card(entry.amount()))
                .withColor(in ? EssentialColors.success() : EssentialColors.modalText())
                .append(Component.literal("  " + time(entry.time())).withColor(EssentialColors.cardBalance()));

        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        row.child(text(first, EssentialColors.modalText()))
                .child(text(where(entry), EssentialColors.tabText()))
                .gap(1)
                .padding(Insets.of(3, 4, 4, 4))
                .surface(Surface.flat(EssentialColors.border()));

        if (entry.comment() != null && !entry.comment().isEmpty()) {
            row.child(text(Component.literal("«" + entry.comment() + "»"), EssentialColors.cardBalance()));
        }

        return row;
    }

    /** "Main → 12345 (Foxanto)" for a transfer, "Main" and how it was noticed for money in. */
    private static Component where(Entry entry) {
        if (entry.kind() == Kind.IN) {
            return Component.translatable(entry.away()
                    ? "gui.spwallet.history.incoming_away"
                    : "gui.spwallet.history.incoming", entry.cardName());
        }

        String receiver = entry.receiverName() == null
                ? entry.receiver()
                : entry.receiver() + " (" + entry.receiverName() + ")";

        return Component.translatable("gui.spwallet.history.outgoing", entry.cardName(), receiver);
    }

    /** Just the time for today, the date and time for anything older. */
    private static String time(long millis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        Calendar now = Calendar.getInstance();

        boolean today = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);

        return new SimpleDateFormat(today ? "HH:mm" : "dd.MM HH:mm").format(new Date(millis));
    }

    private static LabelComponent label(Component text, int color) {
        LabelComponent label = UIComponents.label(text);
        label.color(Color.ofArgb(color)).shadow(EssentialColors.textShadow());
        return label;
    }

    /** A label as wide as the row, which wraps rather than running past its edge. */
    private static LabelComponent text(Component text, int color) {
        LabelComponent label = label(text, color);
        label.horizontalSizing(Sizing.fill(100));
        return label;
    }
}
