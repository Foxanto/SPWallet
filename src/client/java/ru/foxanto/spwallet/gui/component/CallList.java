package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.connect.ConnectApi;
import ru.foxanto.spwallet.connect.ConnectDto;
import ru.foxanto.spwallet.connect.ConnectException;
import ru.foxanto.spwallet.connect.SPConnect;
import ru.foxanto.spwallet.gui.EssentialColors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The side column's call tab, «SPConnect от SPWallet»: an optional message, then the organisations
 * of this server, each with a button that calls its staff to where the player stands. An
 * organisation called lately shows how long until it can be called again instead.
 */
public class CallList extends FlowLayout {
    private static final int MAX_MESSAGE_LENGTH = 256;

    private final EssentialTextBox message;
    private final LabelComponent status;
    private final FlowLayout orgs;
    private final MyCalls mine = new MyCalls();
    private final FlowLayout aboutMe = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    /** When each organisation can be called again, in {@link System#currentTimeMillis()} terms. */
    private final Map<String, Long> cooldownEnds = new HashMap<>();
    private final Map<String, EssentialButton> buttons = new HashMap<>();

    private @Nullable ConnectDto.ServerInfo server;
    private boolean loaded;
    private boolean sending;
    private long lastSecond;
    private int seenVersion = SPConnect.version();

    public CallList() {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.gap(4);

        this.message = new EssentialTextBox(Sizing.fill(100), Component.translatable("gui.spwallet.connect.message"));
        this.message.textBox.setMaxLength(MAX_MESSAGE_LENGTH);

        this.status = text(Component.empty(), EssentialColors.cardBalance());
        this.orgs = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.orgs.gap(3);
        this.aboutMe.gap(3);
    }

    /** Loads the organisations the first time the tab is shown; later shows keep what is there. */
    public void show() {
        if (this.loaded) {
            return;
        }

        this.loaded = true;
        this.server = SPConnect.server();
        this.clearChildren();

        if (this.server == null) {
            this.child(text(Component.translatable("gui.spwallet.connect.not_sp"), EssentialColors.cardBalance()));
            return;
        }

        this.child(this.mine)
                .child(heading(Component.translatable("gui.spwallet.connect.call_heading")))
                .child(this.message)
                .child(this.status)
                .child(this.orgs)
                .child(text(Component.translatable("gui.spwallet.connect.privacy"), EssentialColors.cardBalance())
                        .margins(Insets.top(3)))
                .child(this.aboutMe);

        this.setStatus(Component.translatable("gui.spwallet.connect.loading"), EssentialColors.cardBalance());
        this.load();
        this.mine.load(this.server);
        this.loadAboutMe();
    }

    private void load() {
        ConnectDto.ServerInfo server = this.server;

        SPConnect.orgs(server).whenComplete((list, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                this.setStatus(Component.literal(ConnectApi.errorMessage(error)), EssentialColors.error());
                return;
            }

            this.showOrgs(list);
        }));
    }

    /** «Отзывы обо мне»: only for staff, who have a rating to show. */
    private void loadAboutMe() {
        SPConnect.reviewsAboutMe().whenComplete((about, error) -> Minecraft.getInstance().execute(() -> {
            this.aboutMe.clearChildren();

            if (error != null || about == null || about.total() == null || about.total().count() == 0) {
                return;
            }

            this.aboutMe.child(heading(Component.translatable("gui.spwallet.connect.about_me")).margins(Insets.top(6)));
            this.aboutMe.child(text(Component.literal("★ " + String.format(java.util.Locale.ROOT, "%.1f", about.total().average()))
                            .withColor(EssentialColors.warning())
                            .append(Component.translatable("gui.spwallet.connect.review_count", about.total().count())
                                    .withColor(EssentialColors.cardBalance())),
                    EssentialColors.warning()));

            about.reviews().stream().limit(5).forEach(review -> {
                FlowLayout row = row(review.org() == null ? null : review.org().color());
                row.child(text(Component.literal(MyCalls.stars(review.rating())).withColor(EssentialColors.warning())
                                .append(Component.literal(review.callerName() == null ? "" : "  " + review.callerName())
                                        .withColor(EssentialColors.tabText())),
                        EssentialColors.warning()));
                if (review.text() != null && !review.text().isBlank()) {
                    row.child(text(Component.literal("«" + review.text() + "»"), EssentialColors.cardBalance()));
                }
                this.aboutMe.child(row);
            });
        }));
    }

    private void showOrgs(List<ConnectDto.OrgInfo> list) {
        this.orgs.clearChildren();
        this.buttons.clear();
        this.cooldownEnds.clear();

        if (list.isEmpty()) {
            this.setStatus(Component.translatable("gui.spwallet.connect.no_orgs"), EssentialColors.cardBalance());
            return;
        }

        if (this.status.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents loading
                && loading.getKey().equals("gui.spwallet.connect.loading")) {
            this.setStatus(Component.empty(), EssentialColors.cardBalance());
        }

        long now = System.currentTimeMillis();

        for (ConnectDto.OrgInfo org : list) {
            if (org.cooldownRemaining() > 0) {
                this.cooldownEnds.put(org.id(), now + org.cooldownRemaining() * 1000L);
            }

            this.orgs.child(this.orgRow(org));
        }

        this.updateButtons();
    }

    private FlowLayout orgRow(ConnectDto.OrgInfo org) {
        EssentialButton button = new EssentialButton(EssentialButton.Style.BLUE,
                Component.translatable("gui.spwallet.connect.call"), pressed -> this.call(org));
        button.horizontalSizing(Sizing.fixed(48));
        button.verticalSizing(Sizing.fixed(16));
        this.buttons.put(org.id(), button);

        LabelComponent name = label(Component.literal(org.name()), EssentialColors.modalText());
        name.horizontalSizing(Sizing.expand());

        // The name and the button on one line, the description under them across the whole row.
        FlowLayout row = row(org.color());
        row.child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .child(name)
                .child(button)
                .gap(4)
                .verticalAlignment(VerticalAlignment.CENTER));

        if (org.description() != null && !org.description().isBlank()) {
            row.child(text(Component.literal(org.description()), EssentialColors.cardBalance()));
        }

        return row;
    }

    private void call(ConnectDto.OrgInfo org) {
        if (this.sending || this.server == null) {
            return;
        }

        this.sending = true;
        this.updateButtons();
        this.setStatus(Component.translatable("gui.spwallet.connect.sending"), EssentialColors.cardBalance());

        SPConnect.call(this.server, org, this.message.value().trim())
                .whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                    this.sending = false;

                    if (error != null) {
                        if (ConnectApi.unwrap(error) instanceof ConnectException api && "cooldown".equals(api.code)) {
                            this.cooldownEnds.put(org.id(), System.currentTimeMillis() + api.retryAfter * 1000L);
                            this.setStatus(Component.translatable("gui.spwallet.connect.cooldown", time(api.retryAfter)),
                                    EssentialColors.warning());
                        } else {
                            this.setStatus(Component.literal(ConnectApi.errorMessage(error)), EssentialColors.error());
                        }

                        this.updateButtons();
                        return;
                    }

                    this.message.textBox.text("");
                    this.setStatus(result.employeesOnline() > 0
                            ? Component.translatable("gui.spwallet.connect.sent_online", org.name(), result.employeesOnline())
                            : Component.translatable("gui.spwallet.connect.sent_discord", org.name()),
                            EssentialColors.success());
                    this.cooldownEnds.put(org.id(), System.currentTimeMillis() + org.cooldownMinutes() * 60_000L);
                    this.updateButtons();
                    this.mine.load(this.server);
                }));
    }

    /** Puts the countdown on the buttons of organisations that cannot be called yet. */
    private void updateButtons() {
        long now = System.currentTimeMillis();

        this.buttons.forEach((id, button) -> {
            Long end = this.cooldownEnds.get(id);
            int left = end == null ? 0 : (int) Math.ceil((end - now) / 1000.0);

            if (left > 0) {
                button.setMessage(Component.literal(time(left)));
                button.active(false);
                button.tooltip(Component.translatable("gui.spwallet.connect.cooldown", time(left)));
            } else {
                button.setMessage(Component.translatable("gui.spwallet.connect.call"));
                button.active(!this.sending);
                button.tooltip((java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>) null);
            }
        });
    }

    @Override
    public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        this.tickCountdown();

        // Something came over the realtime channel: a call of ours was taken or closed.
        if (this.loaded && this.server != null && this.seenVersion != SPConnect.version()) {
            this.seenVersion = SPConnect.version();
            this.mine.load(this.server);
            this.loadAboutMe();
        }

        super.draw(context, mouseX, mouseY, partialTicks, delta);
    }

    /** Once a second while anything is cooling down, so the countdown on the buttons runs. */
    private void tickCountdown() {
        long second = System.currentTimeMillis() / 1000;

        if (second != this.lastSecond && !this.cooldownEnds.isEmpty()) {
            this.lastSecond = second;
            this.updateButtons();
        }
    }

    private void setStatus(Component text, int color) {
        this.status.text(text);
        this.status.color(Color.ofArgb(color));
    }

    static LabelComponent label(Component text, int color) {
        LabelComponent label = UIComponents.label(text);
        label.color(Color.ofArgb(color)).shadow(EssentialColors.textShadow());
        return label;
    }

    /** A label as wide as its row, which wraps rather than running past the edge. */
    static LabelComponent text(Component text, int color) {
        LabelComponent label = label(text, color);
        label.horizontalSizing(Sizing.fill(100));
        return label;
    }

    static LabelComponent heading(Component text) {
        return label(text, EssentialColors.screenTitle());
    }

    /** A row of the tab, with the organisation's colour as a strip down its left edge. */
    static FlowLayout row(@Nullable String color) {
        int strip = 0xFF000000 | color(color);
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        row.gap(2)
                .padding(Insets.of(3, 4, 7, 3))
                .surface(Surface.flat(EssentialColors.border()).and((context, component) -> context.fill(
                        component.x(), component.y(), component.x() + 2, component.y() + component.height(), strip)));
        return row;
    }

    private static int color(@Nullable String hex) {
        if (hex == null) {
            return 0x5865F2;
        }

        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (RuntimeException e) {
            return 0x5865F2;
        }
    }

    private static String time(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
