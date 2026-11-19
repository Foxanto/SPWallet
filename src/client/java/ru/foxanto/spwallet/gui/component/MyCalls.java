package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.connect.ConnectApi;
import ru.foxanto.spwallet.connect.ConnectDto;
import ru.foxanto.spwallet.connect.SPConnect;
import ru.foxanto.spwallet.gui.EssentialColors;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * «Мои вызовы» at the top of the call tab: the player's own calls of the last day, with what
 * happened to each, a button to cancel or finish it, and the stars to rate whoever came. Nothing
 * is shown while there are none.
 */
public class MyCalls extends FlowLayout {
    private static final int MAX_REVIEW_LENGTH = 300;

    private final LabelComponent status = CallList.text(Component.empty(), EssentialColors.cardBalance());
    private ConnectDto.@Nullable ServerInfo server;
    private List<ConnectDto.MyCall> calls = List.of();
    /** The call whose stars are open, and how many of them are lit. */
    private @Nullable String rating;
    private int stars;
    private @Nullable EssentialTextBox reviewText;
    private boolean busy;

    public MyCalls() {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.gap(3);
    }

    public void load(ConnectDto.ServerInfo server) {
        this.server = server;
        SPConnect.myCalls(server).whenComplete((list, error) -> Minecraft.getInstance().execute(() -> {
            if (error == null) {
                this.calls = list;
                this.rebuild();
            }
        }));
    }

    private void rebuild() {
        this.clearChildren();

        // A call staff finished: its stars wait here, whether or not the list still has it.
        ConnectDto.CallInfo pending = SPConnect.pendingRating();
        if (pending != null) {
            this.child(CallList.heading(Component.translatable("gui.spwallet.connect.rate_heading")));
            this.child(this.pendingRow(pending));
        }

        // Finished and called off calls go: the list is only for what is still going on.
        List<ConnectDto.MyCall> live = this.calls.stream()
                .filter(call -> "OPEN".equals(call.status()) || "ACCEPTED".equals(call.status()))
                .filter(call -> pending == null || !call.id().equals(pending.id()))
                .toList();

        if (live.isEmpty() && pending == null) {
            return;
        }

        if (!live.isEmpty()) {
            this.child(CallList.heading(Component.translatable("gui.spwallet.connect.mine")));
            for (ConnectDto.MyCall call : live) {
                this.child(this.row(call));
            }
        }

        this.child(this.status);
    }

    /** The finished call to rate: who helped, the stars, and «Пропустить». */
    private FlowLayout pendingRow(ConnectDto.CallInfo call) {
        FlowLayout row = CallList.row(call.org() == null ? null : call.org().color());
        row.child(CallList.text(Component.literal(call.org() == null ? "?" : call.org().name()),
                        EssentialColors.modalText()))
                .child(CallList.text(Component.translatable("gui.spwallet.connect.rate_who", call.acceptedBy()),
                        EssentialColors.cardBalance()))
                .child(this.ratingForm(call.id(), false));
        return row;
    }

    private FlowLayout row(ConnectDto.MyCall call) {
        FlowLayout row = CallList.row(call.org() == null ? null : call.org().color());

        // The status goes under the name: side by side, a long name would wrap mid-word.
        row.child(CallList.text(Component.literal(call.org() == null ? "?" : call.org().name()),
                        EssentialColors.modalText()))
                .child(CallList.text(statusText(call), statusColor(call.status())));

        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.gap(4).verticalAlignment(VerticalAlignment.CENTER);

        boolean finishing = call.id().equals(this.rating);

        switch (call.status()) {
            case "OPEN" -> actions.child(this.button(EssentialButton.Style.NEUTRAL, "gui.spwallet.connect.cancel",
                    () -> this.act(SPConnect.close(call.id()))));
            // Finishing a taken call asks for the stars first; «Пропустить» just finishes it.
            case "ACCEPTED" -> {
                if (!finishing) {
                    actions.child(this.button(EssentialButton.Style.BLUE, "gui.spwallet.connect.finish", () -> {
                        this.rating = call.id();
                        this.stars = 5;
                        this.rebuild();
                    }));
                }
            }
            default -> {
            }
        }

        if (!actions.children().isEmpty()) {
            row.child(actions);
        }

        if (finishing) {
            row.child(this.ratingForm(call.id(), true));
        }

        return row;
    }

    /**
     * Five stars to click, an optional line of text, «Отправить» and «Пропустить».
     *
     * @param close whether the call is finished along with it (the player's own «Завершить»), or
     *              was finished already by staff
     */
    private FlowLayout ratingForm(String callId, boolean close) {
        FlowLayout starRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        starRow.gap(3);

        for (int i = 1; i <= 5; i++) {
            int value = i;
            TransparentButton star = new TransparentButton(Component.literal(i <= this.stars ? "★" : "☆"),
                    i <= this.stars ? EssentialColors.warning() : EssentialColors.cardBalance(),
                    EssentialColors.warning(),
                    EssentialColors.warning(),
                    pressed -> {
                        this.stars = value;
                        String typed = this.reviewText == null ? "" : this.reviewText.value();
                        this.rebuild();
                        if (this.reviewText != null) this.reviewText.textBox.text(typed);
                    });
            if (this.stars == 0) this.stars = 5;
            star.shadow(EssentialColors.textShadow());
            star.tooltip(Component.translatable("gui.spwallet.connect.stars." + i));
            starRow.child(star);
        }

        this.reviewText = new EssentialTextBox(Sizing.fill(100), Component.translatable("gui.spwallet.connect.review_text"));
        this.reviewText.textBox.setMaxLength(MAX_REVIEW_LENGTH);

        EssentialTextBox text = this.reviewText;
        FlowLayout buttons = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        buttons.gap(4)
                .child(this.button(EssentialButton.Style.BLUE, "gui.spwallet.connect.send_review", () -> {
                    this.rating = null;
                    int stars = this.stars;
                    if (close) SPConnect.closedByMe(callId);
                    // The review first, while the call is still taken; then it is finished.
                    this.act(SPConnect.review(callId, stars, text.value().trim())
                            .thenCompose(ignored -> close ? SPConnect.close(callId) : CompletableFuture.completedFuture(null))
                            .whenComplete((ignored, error) -> {
                                if (!close) SPConnect.ratingDone();
                            }));
                }))
                .child(this.button(EssentialButton.Style.NEUTRAL, "gui.spwallet.connect.skip", () -> {
                    this.rating = null;
                    if (close) {
                        SPConnect.closedByMe(callId);
                        this.act(SPConnect.close(callId));
                    } else {
                        SPConnect.ratingDone();
                        this.rebuild();
                    }
                }));

        FlowLayout form = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        form.gap(3).child(starRow).child(text).child(buttons);
        return form;
    }

    private EssentialButton button(EssentialButton.Style style, String key, Runnable action) {
        EssentialButton button = new EssentialButton(style, Component.translatable(key), pressed -> action.run());
        button.horizontalSizing(Sizing.content(6));
        button.verticalSizing(Sizing.fixed(16));
        button.active(!this.busy);
        return button;
    }

    /** Runs a request, then loads the calls again so the row shows what came of it. */
    private void act(CompletableFuture<Void> request) {
        this.busy = true;
        this.rebuild();
        request.whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            this.busy = false;
            if (error != null) {
                this.status.text(Component.literal(ConnectApi.errorMessage(error)));
                this.status.color(Color.ofArgb(EssentialColors.error()));
            } else {
                this.status.text(Component.empty());
            }
            if (this.server != null) this.load(this.server);
        }));
    }

    private static Component statusText(ConnectDto.MyCall call) {
        return switch (call.status()) {
            case "OPEN" -> Component.translatable("gui.spwallet.connect.status.open");
            case "ACCEPTED" -> Component.translatable("gui.spwallet.connect.status.accepted", call.acceptedBy());
            case "CLOSED" -> Component.translatable("gui.spwallet.connect.status.closed");
            default -> Component.translatable("gui.spwallet.connect.status.cancelled");
        };
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "OPEN" -> EssentialColors.warning();
            case "ACCEPTED" -> EssentialColors.success();
            default -> EssentialColors.cardBalance();
        };
    }

    static String stars(int rating) {
        int filled = Math.max(0, Math.min(5, rating));
        return "★".repeat(filled) + "☆".repeat(5 - filled);
    }
}
