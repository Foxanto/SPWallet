package ru.foxanto.spwallet.gui;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import ru.foxanto.spwallet.config.AmountFormat;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.api.ApiException;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.api.CardNumber;
import ru.foxanto.spwallet.api.PlayerCard;
import ru.foxanto.spwallet.api.SPWorldsApi;
import ru.foxanto.spwallet.api.Transaction;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.storage.HistoryStorage;
import ru.foxanto.spwallet.storage.TemplateStorage;
import ru.foxanto.spwallet.config.ConfigScreen;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.component.CardList;
import ru.foxanto.spwallet.gui.component.EssentialButton;
import ru.foxanto.spwallet.gui.component.EssentialScrollContainer;
import ru.foxanto.spwallet.gui.component.EssentialTextBox;
import ru.foxanto.spwallet.gui.component.HistoryList;
import ru.foxanto.spwallet.gui.component.ApiStatusMark;
import ru.foxanto.spwallet.gui.component.PlayerCardList;
import ru.foxanto.spwallet.gui.component.QuickList;
import ru.foxanto.spwallet.gui.component.TabBar;
import ru.foxanto.spwallet.gui.component.TransparentButton;
import ru.foxanto.spwallet.gui.overlay.IncomingNotifications;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.PaymentSound;
import ru.foxanto.spwallet.util.SPServer;
import ru.foxanto.spwallet.util.TransferMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** The wallet: the saved cards on the left, the transfer form on the right. */
public class WalletScreen extends EssentialScreen {
    /** The GUI scale this screen is laid out for. */
    private static final int WALLET_GUI_SCALE = 2;

    /**
     * Widths of the card list and the transfer panel, as percentages; the side column with the
     * journal takes the rest. The card rows fit a name, a number and the delete button, so the
     * first cannot go much below 30.
     */
    private static final int CARD_PANEL_PERCENT = 36;
    private static final int TRANSFER_PANEL_PERCENT = 34;
    private static final int SIDE_PANEL_PERCENT = 100 - CARD_PANEL_PERCENT - TRANSFER_PANEL_PERCENT;
    /** The card list's width with the side column hidden, which is what it was before there was one. */
    private static final int COLLAPSED_CARD_PANEL_PERCENT = 40;

    /** Room for the longest way of writing an amount the form takes, such as {@code 15624 ст. 63 АР}. */
    private static final int MAX_AMOUNT_LENGTH = 16;
    private static final int MAX_COMMENT_LENGTH = 45;
    private static final int MAX_USERNAME_LENGTH = 16;
    private static final int MAX_TEMPLATE_NAME_LENGTH = 24;

    /** What the API accepts inside a URL path; anything else comes back as a bare 400. */
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** How tall the looked-up player's card list is; it scrolls when there are more. */
    private static final int PLAYER_CARDS_HEIGHT = 34;

    private final int previousGuiScale;
    private final @Nullable String prefilledTarget;
    private final int prefilledAmount;
    private final @Nullable String prefilledComment;

    private SPServer server;
    private TransferMode mode;

    private @Nullable Card selectedCard;
    private @Nullable PlayerCard selectedTarget;

    private FlowLayout root;
    private EssentialScrollContainer cardList;
    private FlowLayout form;
    private EssentialTextBox numberBox;
    private EssentialTextBox nicknameBox;
    private EssentialTextBox amountBox;
    private EssentialTextBox commentBox;
    private EssentialButton findButton;
    private TabBar<TransferMode> modeTabs;
    private EssentialButton transferButton;
    private PlayerCardList playerCards;
    private EssentialScrollContainer playerCardsScroll;
    private Runnable revalidate = () -> {};

    /** Which part of the side column was open last, which a new wallet opens on again. */
    private static SideTab lastSideTab = SideTab.QUICK;

    /** How many receivers the quick list offers. */
    private static final int RECENT_RECEIVERS = 8;

    private SideTab sideTab = lastSideTab;

    /** The whole wallet and its three rows, which the side column is taken out of when hidden. */
    private FlowLayout frame;
    private FlowLayout navRow;
    private FlowLayout tabRow;
    private FlowLayout bodyRow;
    /** The card column's and the transfer column's piece of each row, resized with the side column. */
    private final List<FlowLayout> cardColumns = new ArrayList<>();
    private final List<FlowLayout> transferColumns = new ArrayList<>();
    private FlowLayout transferNav;
    private FlowLayout sideNav;
    private FlowLayout sideBody;
    private UIComponent statusMark;
    private UIComponent settingsGear;
    private TransparentButton sideToggle;
    /** Set by the arrow, done on the next tick: the click is still going round the columns it moves. */
    private boolean sideLayoutPending;
    private FlowLayout sideBar;
    private FlowLayout sideContent;
    private UIComponent historyBar;
    private UIComponent quickBar;
    private EssentialScrollContainer historyScroll;
    private EssentialScrollContainer quickScroll;
    private QuickList quickList;
    private String shownQuick = "";

    /** Names Tab cycles through for what was typed before the first press, and where it is. */
    private @Nullable List<String> completions;
    private int completionIndex;
    private @Nullable String lastCompletion;

    private HistoryList historyList;
    private HistoryFilter historyFilter = HistoryFilter.ALL;
    /** What the journal was last built from, so it is only built again when that changes. */
    private String shownHistory = "";

    /** Set while this screen closes itself, so the mixin does not restore the scale twice. */
    public boolean closing = false;

    public WalletScreen(SPServer server) {
        this(server, null, 0, null);
    }

    /** Opens with {@code target} filled in and nothing else. */
    public WalletScreen(SPServer server, String target) {
        this(server, target, 0, null);
    }

    /** Opens on the by-number form, filled in from {@code transaction}. */
    public WalletScreen(SPServer server, Transaction transaction) {
        this(server, transaction.receiver(), transaction.amount(), transaction.comment());
    }

    /**
     * Opens the transfer form filled in from a payment sign or a clicked player.
     *
     * <p>A five digit target is taken as a card number and goes to the by-number form; anything else
     * is a nickname, which opens the by-nickname form and starts looking it up. An amount of zero or
     * less leaves the amount blank rather than writing a nonsense number into it.
     */
    public WalletScreen(SPServer server, @Nullable String target, int amount, @Nullable String comment) {
        super(Component.translatable("gui.spwallet.title.cards"));

        this.server = server;
        this.prefilledTarget = target == null || target.isEmpty() ? null : target;
        this.prefilledAmount = amount;
        this.prefilledComment = comment;
        this.mode = this.prefilledTarget != null && !CardNumber.is(this.prefilledTarget)
                ? TransferMode.NICKNAME
                : TransferMode.NUMBER;
        this.previousGuiScale = Minecraft.getInstance().options.guiScale().get();
    }

    /**
     * Checks the form again every tick, not only when something is typed: the balance a transfer is
     * held against arrives after the screen opens.
     */
    @Override
    public void tick() {
        super.tick();
        this.revalidate.run();
        this.refreshHistory();
        this.refreshQuick();

        if (this.sideLayoutPending) {
            this.sideLayoutPending = false;
            this.layOutSideColumn();
        }
    }

    /** Tab in the nickname field completes the name from the players online and the recent receivers. */
    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_TAB && this.mode == TransferMode.NICKNAME
                && this.nicknameBox != null && this.nicknameBox.textBox.isFocused()) {
            this.completeNickname(input.hasShiftDown());
            return true;
        }

        return super.keyPressed(input);
    }

    /** The two parts of the side column. */
    private enum SideTab {
        /** Saved payments and recent receivers. */
        QUICK("quick"),
        HISTORY("history");

        private final String key;

        SideTab(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable("gui.spwallet.side." + this.key);
        }
    }

    /** Which operations the journal lists. */
    private enum HistoryFilter {
        ALL("all"),
        /** Only those of the card selected on the left. */
        CARD("card");

        private final String key;

        HistoryFilter(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable("gui.spwallet.history.filter." + this.key);
        }
    }

    /** Which of the two transfer forms the screen is showing. */
    public TransferMode mode() {
        return this.mode;
    }

    /** The GUI scale that was active before this screen forced its own. */
    public int previousGuiScale() {
        return this.previousGuiScale;
    }

    @Override
    public void added() {
        super.added();

        if (SPWalletConfig.get().forceGuiScale) {
            applyGuiScale(WALLET_GUI_SCALE);
        }
    }

    @Override
    public void onClose() {
        this.closing = true;

        if (SPWalletConfig.get().forceGuiScale) {
            applyGuiScale(this.previousGuiScale);
        }

        super.onClose();
    }

    public static void applyGuiScale(int scale) {
        Minecraft client = Minecraft.getInstance();

        if (client.options.guiScale().get() != scale) {
            client.options.guiScale().set(scale);
            client.resizeDisplay();
        }
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.root = rootComponent;
        this.numberBox = new EssentialTextBox(Sizing.fill(100),
                Component.translatable("gui.spwallet.input.transfer.card_number"));
        this.numberBox.textBox.setMaxLength(CardNumber.LENGTH);
        this.numberBox.textBox.setFilter(CardNumber::isBeingTyped);

        this.nicknameBox = new EssentialTextBox(Sizing.fill(100),
                Component.translatable("gui.spwallet.input.transfer.nickname"));
        this.nicknameBox.textBox.setMaxLength(MAX_USERNAME_LENGTH);

        this.amountBox = new EssentialTextBox(Sizing.fill(100),
                Component.translatable("gui.spwallet.input.transfer.amount"));
        this.amountBox.textBox.setMaxLength(MAX_AMOUNT_LENGTH);
        this.amountBox.textBox.setFilter(AmountFormat::isBeingTyped);
        this.amountBox.suffix(this::amountHint);

        this.commentBox = new EssentialTextBox(Sizing.fill(100),
                Component.translatable("gui.spwallet.input.transfer.comment"));
        this.commentBox.textBox.setMaxLength(MAX_COMMENT_LENGTH);

        if (this.prefilledTarget != null) {
            if (this.mode == TransferMode.NUMBER) {
                this.numberBox.textBox.text(this.prefilledTarget);
            } else {
                this.nicknameBox.textBox.text(this.prefilledTarget);
            }
        }

        if (this.prefilledAmount > 0) {
            this.amountBox.textBox.text(String.valueOf(this.prefilledAmount));
        }

        if (this.prefilledComment != null && !this.prefilledComment.isEmpty()) {
            this.commentBox.textBox.text(this.prefilledComment);
        }

        this.playerCards = new PlayerCardList(target -> {
            this.selectedTarget = target;
            this.revalidate.run();
        });

        this.findButton = new EssentialButton(EssentialButton.Style.NEUTRAL,
                Component.translatable("gui.spwallet.button.find_cards"),
                button -> this.findPlayerCards());
        this.findButton.horizontalSizing(Sizing.fill(100));
        this.findButton.verticalSizing(Sizing.fixed(18));

        // Fixed height: however many cards the player turns out to have, the form below stays put.
        this.playerCardsScroll = new EssentialScrollContainer(
                ScrollContainer.ScrollDirection.VERTICAL, Sizing.fill(100), Sizing.fixed(PLAYER_CARDS_HEIGHT),
                this.playerCards);

        this.transferButton = new EssentialButton(EssentialButton.Style.FLAT,
                Component.translatable("gui.spwallet.button.transfer"),
                button -> this.transfer());
        this.transferButton.horizontalSizing(Sizing.fill(100));
        this.transferButton.verticalSizing(Sizing.fixed(18));

        this.revalidate = () -> {
            this.transferButton.active(this.canTransfer());
            this.findButton.active(this.canLookUp());
        };

        this.numberBox.textBox.onChanged().subscribe(value -> {
            String number = CardNumber.normalize(value);

            // A number is always upper case, so typing it in lower case fixes itself. setValue()
            // rather than text(): it leaves the caret at the end, where the next character goes.
            if (!number.equals(value)) {
                this.numberBox.textBox.setValue(number);
                return;
            }

            this.revalidate.run();
        });
        this.amountBox.textBox.onChanged().subscribe(value -> this.revalidate.run());
        this.nicknameBox.textBox.onChanged().subscribe(value -> {
            this.playerCards.clear();
            this.revalidate.run();
        });

        this.form = UIContainers.verticalFlow(Sizing.fill(88), Sizing.content());
        this.rebuildForm();

        this.cardList = new EssentialScrollContainer(
                ScrollContainer.ScrollDirection.VERTICAL, Sizing.fill(100), Sizing.fill(100),
                this.buildCardList(rootComponent));

        TabBar<SPServer> serverTabs = new TabBar<>(List.of(SPServer.SP, SPServer.SPM), this.server,
                SPServer::label, selected -> {
            this.server = selected;
            this.reloadCards(rootComponent);
        });
        serverTabs.padding(Insets.of(10, 10, 12, 12));

        this.modeTabs = new TabBar<>(List.of(TransferMode.values()), this.mode,
                TransferMode::label, selected -> {
            this.mode = selected;
            this.rebuildForm();
            this.revalidate.run();
        });
        this.modeTabs.padding(Insets.of(10, 10, 12, 12));

        this.historyList = new HistoryList();
        this.historyScroll = new EssentialScrollContainer(
                ScrollContainer.ScrollDirection.VERTICAL, Sizing.fill(100), Sizing.fill(100), this.historyList);

        this.quickList = new QuickList(
                this::fill,
                template -> rootComponent.child(confirm(
                        Component.translatable("modal.spwallet.delete_template.description", template.name()),
                        24, Component.translatable("gui.spwallet.button.delete"),
                        () -> SPWalletClient.templates().remove(template))),
                this::fill);
        this.quickScroll = new EssentialScrollContainer(
                ScrollContainer.ScrollDirection.VERTICAL, Sizing.fill(100), Sizing.fill(100), this.quickList);

        TabBar<SideTab> sideTabs = new TabBar<>(List.of(SideTab.values()), this.sideTab,
                SideTab::label, selected -> {
            this.sideTab = selected;
            lastSideTab = selected;
            this.showSideTab();
        });
        sideTabs.margins(Insets.of(11, 0, 10, 0));

        TabBar<HistoryFilter> historyTabs = new TabBar<>(List.of(HistoryFilter.values()), this.historyFilter,
                HistoryFilter::label, selected -> {
            this.historyFilter = selected;
            this.refreshHistory();
        });
        historyTabs.padding(Insets.of(10, 10, 12, 12));

        this.historyBar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100))
                .child(historyTabs)
                .child(this.clearHistoryButton(rootComponent));
        this.quickBar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100))
                .child(this.saveTemplateButton(rootComponent));
        this.sideBar = UIContainers.horizontalFlow(Sizing.fill(SIDE_PANEL_PERCENT), Sizing.fill(100));
        this.sideContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        this.showSideTab();

        this.revalidate.run();
        this.refreshHistory();
        this.refreshQuick();

        this.statusMark = this.apiStatusMark();
        this.settingsGear = this.settingsButton();
        this.sideToggle = this.sideToggleButton();

        FlowLayout cardNav = UIContainers.horizontalFlow(Sizing.fill(CARD_PANEL_PERCENT), Sizing.fill(100));
        cardNav.child(UIComponents.label(Component.translatable("gui.spwallet.title.cards"))
                        .color(Color.ofArgb(EssentialColors.screenTitle()))
                        .shadow(EssentialColors.textShadow())
                        .margins(Insets.of(11, 0, 13, 0)))
                .child(this.doNotDisturbButton())
                .surface(EssentialSurfaces.NAV_LEFT);

        this.transferNav = UIContainers.horizontalFlow(Sizing.fill(TRANSFER_PANEL_PERCENT), Sizing.fill(100));
        this.transferNav.child(UIComponents.label(Component.translatable("gui.spwallet.title.transfer"))
                        .color(Color.ofArgb(EssentialColors.screenTitle()))
                        .shadow(EssentialColors.textShadow())
                        .margins(Insets.of(11, 0, 10, 0)))
                .child(this.sideToggle)
                .surface(EssentialSurfaces.NAV_RIGHT);

        this.sideNav = UIContainers.horizontalFlow(Sizing.fill(SIDE_PANEL_PERCENT), Sizing.fill(100));
        this.sideNav.child(sideTabs).surface(EssentialSurfaces.NAV_RIGHT);

        FlowLayout cardTabs = UIContainers.horizontalFlow(Sizing.fill(CARD_PANEL_PERCENT), Sizing.fill(100));
        cardTabs.child(serverTabs).surface(EssentialSurfaces.PANEL_LEFT);

        FlowLayout transferTabs = UIContainers.horizontalFlow(Sizing.fill(TRANSFER_PANEL_PERCENT), Sizing.fill(100));
        transferTabs.child(this.modeTabs).surface(EssentialSurfaces.PANEL_RIGHT_TOP);

        this.sideBar.surface(EssentialSurfaces.PANEL_RIGHT_TOP);

        FlowLayout cardBody = UIContainers.horizontalFlow(Sizing.fill(CARD_PANEL_PERCENT), Sizing.fill(100));
        cardBody.child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100))
                        .child(this.cardList)
                        .margins(Insets.of(0, 3, 3, 0)))
                .surface(EssentialSurfaces.PANEL_LEFT);

        FlowLayout transferBody = UIContainers.horizontalFlow(Sizing.fill(TRANSFER_PANEL_PERCENT), Sizing.fill(100));
        transferBody.child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100))
                        .child(this.form)
                        .margins(Insets.of(0, 8, 0, 3))
                        .horizontalAlignment(HorizontalAlignment.CENTER)
                        .verticalAlignment(VerticalAlignment.CENTER))
                .surface(EssentialSurfaces.PANEL_RIGHT);

        this.sideBody = UIContainers.horizontalFlow(Sizing.fill(SIDE_PANEL_PERCENT), Sizing.fill(100));
        this.sideBody.child(this.sideContent.margins(Insets.of(0, 6, 6, 6)))
                .surface(EssentialSurfaces.PANEL_RIGHT);

        this.cardColumns.addAll(List.of(cardNav, cardTabs, cardBody));
        this.transferColumns.addAll(List.of(this.transferNav, transferTabs, transferBody));

        this.navRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        this.navRow.child(cardNav).child(this.transferNav);
        this.tabRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        this.tabRow.child(cardTabs).child(transferTabs);
        this.bodyRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(72));
        this.bodyRow.child(cardBody).child(transferBody);

        this.frame = UIContainers.verticalFlow(Sizing.fill(96), Sizing.content());
        this.frame.child(this.navRow).child(this.tabRow).child(this.bodyRow);
        this.layOutSideColumn();

        rootComponent
                .child(this.frame)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER)
                .surface(Surface.flat(EssentialColors.background()));

        if (this.mode == TransferMode.NICKNAME && this.prefilledTarget != null) {
            this.findPlayerCards();
        } else if (this.prefilledTarget != null && CardNumber.isAmbiguous(this.prefilledTarget)) {
            this.resolveAmbiguousTarget(this.prefilledTarget);
        }
    }

    /**
     * Shows or hides the side column as {@link SPWalletConfig#sidePanelCollapsed} says. Hidden, the
     * card list and the form get the room back, as they had it before there was a side column, and
     * the status dot and the gear move into the transfer header.
     */
    private void layOutSideColumn() {
        boolean collapsed = SPWalletConfig.get().sidePanelCollapsed;
        int cards = collapsed ? COLLAPSED_CARD_PANEL_PERCENT : CARD_PANEL_PERCENT;
        int transfer = collapsed ? 100 - COLLAPSED_CARD_PANEL_PERCENT : TRANSFER_PANEL_PERCENT;

        this.frame.horizontalSizing(Sizing.fill(collapsed ? 85 : 96));
        this.cardColumns.forEach(column -> column.horizontalSizing(Sizing.fill(cards)));
        this.transferColumns.forEach(column -> column.horizontalSizing(Sizing.fill(transfer)));
        this.form.horizontalSizing(Sizing.fill(collapsed ? 70 : 88));

        detach(this.sideNav);
        detach(this.sideBar);
        detach(this.sideBody);
        detach(this.statusMark);
        detach(this.settingsGear);

        FlowLayout header = collapsed ? this.transferNav : this.sideNav;
        // In the transfer header they sit left of the toggle, which takes the corner.
        int shift = collapsed ? 14 : 0;
        this.statusMark.margins(Insets.of(11, 0, 0, 26 + shift));
        this.settingsGear.margins(Insets.of(11, 0, 0, 12 + shift));
        header.child(this.statusMark).child(this.settingsGear);

        if (!collapsed) {
            this.navRow.child(this.sideNav);
            this.tabRow.child(this.sideBar);
            this.bodyRow.child(this.sideBody);
        }

        this.sideToggle.text(Component.literal(collapsed ? "«" : "»"));
        this.sideToggle.tooltip(Component.translatable(collapsed
                ? "gui.spwallet.side.show"
                : "gui.spwallet.side.hide"));
    }

    private static void detach(UIComponent component) {
        if (component.parent() instanceof FlowLayout parent) {
            parent.removeChild(component);
        }
    }

    /** The arrow at the right of the transfer header, pointing where the side column goes. */
    private TransparentButton sideToggleButton() {
        TransparentButton button = new TransparentButton(Component.literal("»"),
                EssentialColors.tabText(),
                EssentialColors.tabTextHovered(),
                EssentialColors.tabTextHovered(),
                pressed -> {
                    SPWalletConfig config = SPWalletConfig.get();
                    config.sidePanelCollapsed = !config.sidePanelCollapsed;
                    SPWalletConfig.HANDLER.save();
                    this.sideLayoutPending = true;
                });

        button.shadow(EssentialColors.textShadow());
        button.positioning(Positioning.relative(100, 0));
        button.margins(Insets.of(11, 0, 0, 12));

        return button;
    }

    /** Puts the bar and the list of the open side tab into the side column. */
    private void showSideTab() {
        this.sideBar.clearChildren();
        this.sideContent.clearChildren();

        if (this.sideTab == SideTab.QUICK) {
            this.sideBar.child(this.quickBar);
            this.sideContent.child(this.quickScroll);
        } else {
            this.sideBar.child(this.historyBar);
            this.sideContent.child(this.historyScroll);
        }
    }

    /** "+ Save" in the quick tab's bar, which saves what the form holds as a template. */
    private UIComponent saveTemplateButton(FlowLayout rootComponent) {
        TransparentButton button = new TransparentButton(Component.translatable("gui.spwallet.quick.save"),
                EssentialColors.tabText(),
                EssentialColors.tabTextHovered(),
                EssentialColors.tabTextSelected(),
                pressed -> {
                    if (this.templateTarget() == null) {
                        MessageScreen.open(Component.translatable("gui.spwallet.title.error"),
                                Component.translatable("gui.spwallet.quick.save_needs_receiver"));
                        return;
                    }

                    rootComponent.child(this.nameTemplate());
                });

        button.shadow(EssentialColors.textShadow());
        button.tooltip(Component.translatable("gui.spwallet.quick.save.tooltip"));
        button.margins(Insets.of(10, 0, 12, 0));

        return button;
    }

    /**
     * Who a template made from the form now would pay: the card number, or the nickname in the
     * by-nickname form; {@code null} while the form names nobody yet.
     */
    private @Nullable String templateTarget() {
        if (this.mode == TransferMode.NUMBER) {
            String number = CardNumber.normalize(this.numberBox.value());
            return CardNumber.is(number) ? number : null;
        }

        String nickname = this.nicknameBox.value();
        return NICKNAME.matcher(nickname).matches() ? nickname : null;
    }

    /** The overlay that asks what to call the template, then saves it. */
    private OverlayContainer<UIComponent> nameTemplate() {
        String target = this.templateTarget();
        TransferMode mode = this.mode;
        String number = mode == TransferMode.NICKNAME && this.selectedTarget != null ? this.selectedTarget.number() : null;
        Integer amount = AmountFormat.parse(this.amountBox.value());
        String comment = this.commentBox.value().isEmpty() ? null : this.commentBox.value();

        EssentialTextBox name = new EssentialTextBox(Sizing.fill(100),
                Component.translatable("gui.spwallet.input.template_name"));
        name.textBox.setMaxLength(MAX_TEMPLATE_NAME_LENGTH);
        name.textBox.text(comment == null ? target : comment);

        AtomicReference<OverlayContainer<UIComponent>> overlay = new AtomicReference<>();
        EssentialButton save = new EssentialButton(EssentialButton.Style.BLUE,
                Component.translatable("gui.spwallet.quick.save_button"),
                button -> {
                    SPWalletClient.templates().add(new TemplateStorage.Template(name.value().trim(),
                            this.server.key(), mode, target, number, amount == null ? 0 : amount, comment));
                    overlay.get().remove();
                });
        save.horizontalSizing(Sizing.fill(47));
        name.textBox.onChanged().subscribe(value -> save.active(!value.isBlank()));

        UIComponent content = UIContainers.verticalFlow(Sizing.fill(28), Sizing.content())
                .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                        .child(UIComponents.label(Component.translatable("modal.spwallet.save_template.description"))
                                .color(Color.ofArgb(EssentialColors.modalText()))
                                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                                .shadow(EssentialColors.textShadow())
                                .horizontalSizing(Sizing.fill(100)))
                        .child(name)
                        .child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                                .child(new EssentialButton(EssentialButton.Style.NEUTRAL,
                                        Component.translatable("gui.spwallet.button.cancel"),
                                        button -> overlay.get().remove())
                                        .horizontalSizing(Sizing.fill(47)))
                                .child(save)
                                .gap(8)
                                .horizontalAlignment(HorizontalAlignment.CENTER))
                        .gap(12)
                        .margins(Insets.of(17)))
                .surface(Surface.flat(EssentialColors.background())
                        .and(Surface.outline(EssentialColors.modalOutline())));

        OverlayContainer<UIComponent> container = UIContainers.overlay(content);
        container.closeOnClick(false);
        container.surface(Surface.flat(EssentialColors.overlayDim()));
        overlay.set(container);

        return container;
    }

    /** Fills the form in from a template: who, how much and the comment, all as saved. */
    private void fill(TemplateStorage.Template template) {
        this.fillReceiver(template.mode(), template.target(), template.number());
        this.amountBox.textBox.text(template.amount() > 0 ? String.valueOf(template.amount()) : "");
        this.commentBox.textBox.text(template.comment() == null ? "" : template.comment());
    }

    /** Fills only the receiver in from a recent one; the amount and comment are left as they are. */
    private void fill(HistoryStorage.Recent recent) {
        if (recent.receiverName() != null) {
            this.fillReceiver(TransferMode.NICKNAME, recent.receiverName(), recent.receiver());
        } else {
            this.fillReceiver(TransferMode.NUMBER, recent.receiver(), null);
        }
    }

    /**
     * Switches to the form {@code mode} and puts {@code target} in it. A nickname is looked up at
     * once, and the card numbered {@code number} picked among theirs.
     */
    private void fillReceiver(TransferMode mode, String target, @Nullable String number) {
        if (this.mode != mode) {
            this.modeTabs.select(mode);
        }

        if (mode == TransferMode.NUMBER) {
            this.numberBox.textBox.text(target);
        } else {
            this.nicknameBox.textBox.text(target);
            this.findPlayerCards(number);
        }

        this.revalidate.run();
    }

    /** Builds the quick list again if a template was saved or removed, a transfer made or the server switched. */
    private void refreshQuick() {
        if (this.quickList == null) {
            return;
        }

        String key = SPWalletClient.templates().version() + "/" + SPWalletClient.history().version() + "/" + this.server;

        if (key.equals(this.shownQuick)) {
            return;
        }

        this.shownQuick = key;
        this.quickList.show(SPWalletClient.templates().templates(this.server),
                SPWalletClient.history().recent(this.server, RECENT_RECEIVERS));
    }

    /**
     * Completes the nickname: the first press takes what was typed as the start of a name, each
     * press after it moves to the next name that starts the same way, Shift goes back. Typing
     * anything in between starts over.
     */
    private void completeNickname(boolean backwards) {
        String value = this.nicknameBox.value();

        if (this.completions == null || !value.equals(this.lastCompletion)) {
            String prefix = value.toLowerCase(Locale.ROOT);
            this.completions = this.knownNicknames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
            this.completionIndex = backwards ? 0 : -1;
        }

        if (this.completions.isEmpty()) {
            return;
        }

        int size = this.completions.size();
        this.completionIndex = Math.floorMod(this.completionIndex + (backwards ? -1 : 1), size);
        this.lastCompletion = this.completions.get(this.completionIndex);
        this.nicknameBox.textBox.setValue(this.lastCompletion);
    }

    /** The players in the tab list and the nicknames recently paid, sorted and each once. */
    private List<String> knownNicknames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ClientPacketListener connection = Minecraft.getInstance().getConnection();

        if (connection != null) {
            for (PlayerInfo player : connection.getOnlinePlayers()) {
                names.add(player.getProfile().name());
            }
        }

        for (HistoryStorage.Recent recent : SPWalletClient.history().recent(this.server, RECENT_RECEIVERS)) {
            if (recent.receiverName() != null) {
                names.add(recent.receiverName());
            }
        }

        return new ArrayList<>(names);
    }

    /** The bin at the right end of the journal's tabs, which asks before it forgets anything. */
    private UIComponent clearHistoryButton(FlowLayout rootComponent) {
        TransparentButton button = new TransparentButton(Component.literal("🗑"),
                EssentialColors.cardBalance(),
                EssentialColors.error(),
                EssentialColors.error(),
                pressed -> rootComponent.child(confirm(
                        Component.translatable("modal.spwallet.clear_history.description", this.server.label()),
                        24, Component.translatable("gui.spwallet.history.clear"),
                        () -> SPWalletClient.history().clear(this.server))));

        button.shadow(EssentialColors.textShadow());
        button.tooltip(Component.translatable("gui.spwallet.history.clear"));
        button.positioning(Positioning.relative(100, 0));
        button.margins(Insets.of(10, 0, 0, 12));

        return button;
    }

    /**
     * Builds the journal again if anything it shows changed: an operation was added, the server or
     * the filter was switched, or another card was picked while the journal shows one card.
     */
    private void refreshHistory() {
        if (this.historyList == null) {
            return;
        }

        Card card = this.historyFilter == HistoryFilter.CARD ? this.selectedCard : null;
        String key = SPWalletClient.history().version() + "/" + this.server + "/" + this.historyFilter
                + "/" + (card == null ? "" : card.id());

        if (key.equals(this.shownHistory)) {
            return;
        }

        this.shownHistory = key;

        if (this.historyFilter == HistoryFilter.CARD && card == null) {
            this.historyList.show(List.of(), Component.translatable("gui.spwallet.history.pick_card"));
            return;
        }

        List<HistoryStorage.Entry> entries = SPWalletClient.history().entries(this.server);

        if (card != null) {
            entries = entries.stream().filter(entry -> entry.cardId().equals(card.id())).toList();
        }

        this.historyList.show(entries, Component.translatable("gui.spwallet.history.empty"));
    }

    /**
     * The moon at the right of the cards header, which turns do not disturb on and off. Lit while
     * it is on, with how much has come in meanwhile in its tooltip.
     */
    private UIComponent doNotDisturbButton() {
        TransparentButton button = new TransparentButton(Component.literal("☾"),
                EssentialColors.tabText(),
                EssentialColors.tabTextHovered(),
                EssentialColors.tabTextSelected(),
                pressed -> {});

        Runnable update = () -> {
            boolean on = SPWalletConfig.get().doNotDisturb;
            button.selected = on;
            button.tooltip(on
                    ? Component.translatable("gui.spwallet.do_not_disturb.tooltip_on",
                            AmountFormat.card(IncomingNotifications.held()))
                    : Component.translatable("gui.spwallet.do_not_disturb.tooltip_off"));
        };

        button.onPress(pressed -> {
            IncomingNotifications.doNotDisturb(!SPWalletConfig.get().doNotDisturb);
            update.run();
        });
        update.run();

        button.shadow(EssentialColors.textShadow());
        button.positioning(Positioning.relative(100, 0));
        button.margins(Insets.of(11, 0, 0, 13));

        return button;
    }

    /** The API status dot, just left of the gear. */
    private UIComponent apiStatusMark() {
        ApiStatusMark mark = new ApiStatusMark();
        mark.shadow(EssentialColors.textShadow());
        mark.positioning(Positioning.relative(100, 0));
        mark.margins(Insets.of(11, 0, 0, 26));

        return mark;
    }

    /** The gear in the corner of the transfer header, which opens the mod's settings. */
    private UIComponent settingsButton() {
        TransparentButton button = new TransparentButton(Component.literal("⚙"),
                EssentialColors.tabText(),
                EssentialColors.tabTextHovered(),
                EssentialColors.tabTextHovered(),
                pressed -> this.openSettings());

        button.shadow(EssentialColors.textShadow());
        button.tooltip(Component.translatable("gui.spwallet.button.settings"));
        button.positioning(Positioning.relative(100, 0));
        button.margins(Insets.of(11, 0, 0, 12));

        return button;
    }

    /**
     * Swaps the wallet for the settings screen, and comes back to a fresh wallet afterwards: owo
     * builds a screen once, so only a new one picks up a changed theme in the colours it takes
     * when it is built. What was typed into the form is carried over.
     */
    private void openSettings() {
        // Put the player's own scale back first, so that the wallet waiting behind the settings
        // remembers that one rather than the forced scale as the one to restore.
        this.closing = true;

        if (SPWalletConfig.get().forceGuiScale) {
            applyGuiScale(this.previousGuiScale);
        }

        // A half typed card number would be taken for a nickname, so only a whole one is kept.
        String target = this.mode == TransferMode.NUMBER
                ? (CardNumber.is(this.numberBox.value()) ? this.numberBox.value() : null)
                : this.nicknameBox.value();
        Integer amount = AmountFormat.parse(this.amountBox.value());

        WalletScreen wallet = new WalletScreen(this.server, target, amount == null ? 0 : amount,
                this.commentBox.value());
        Minecraft.getInstance().setScreen(ConfigScreen.create(wallet));
    }

    /**
     * Decides whether a target like {@code FURRY} meant a card number or a player of that name.
     *
     * <p>The form is already set up for the card number, which is what the answer falls back to.
     * A player who actually goes by that name wins instead: they are the one a sign naming them
     * meant to be paid, and their card list is shown so that the card can still be picked from it —
     * the card whose number matches their name usually being in it.
     */
    private void resolveAmbiguousTarget(String target) {
        Card card = this.selectedCard;

        if (card == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        SPWorldsApi.playerCards(card, target)
                .thenAcceptAsync(found -> {
                    // The answer is late and unasked for, so it only applies while the form is
                    // still exactly as it was left: anything typed or clicked wins over it.
                    if (found.isEmpty() || this.mode != TransferMode.NUMBER
                            || !this.numberBox.value().equals(target)) {
                        return;
                    }

                    this.nicknameBox.textBox.text(target);
                    this.playerCards.show(found, target);
                    this.modeTabs.select(TransferMode.NICKNAME);
                }, client)
                .exceptionally(throwable -> {
                    // Nobody by that name: it was a card number, which the form already holds.
                    SPWallet.LOGGER.debug("No player called {}, reading it as a card number", target);
                    return null;
                });
    }

    /** Puts the fields of the current {@link TransferMode} into the form, keeping what was typed. */
    private void rebuildForm() {
        this.form.clearChildren();

        if (this.mode == TransferMode.NUMBER) {
            this.form.child(this.numberBox);
        } else {
            // Side by side, because the form has to fit six rows into the panel.
            this.form.child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                    .child(this.nicknameBox.horizontalSizing(Sizing.fill(60)))
                    .child(this.findButton.horizontalSizing(Sizing.fill(38)))
                    .gap(4)
                    .verticalAlignment(VerticalAlignment.CENTER));
            this.form.child(this.playerCardsScroll);
        }

        this.form.child(this.amountBox)
                .child(this.commentBox)
                .child(this.transferButton)
                .gap(7);
    }

    /** Looks up the cards of the player named in the nickname field. */
    private void findPlayerCards() {
        this.findPlayerCards(null);
    }

    /** Looks up the cards of the player named in the nickname field, picking the one numbered {@code preferred}. */
    private void findPlayerCards(@Nullable String preferred) {
        Card card = this.selectedCard;
        String nickname = this.nicknameBox.value();

        if (card == null || nickname.isEmpty()) {
            return;
        }

        // playerCards() rejects a name the API cannot take in a URL by throwing, and this method is
        // also called straight from build() - an exception there would blank the whole screen.
        if (!NICKNAME.matcher(nickname).matches()) {
            this.playerCards.message(Component.translatable("gui.spwallet.description.bad_nickname"),
                    EssentialColors.error());
            return;
        }

        Minecraft client = Minecraft.getInstance();
        this.playerCards.message(Component.translatable("gui.spwallet.description.searching"),
                EssentialColors.cardBalance());

        SPWorldsApi.playerCards(card, nickname)
                .thenAcceptAsync(found -> {
                    if (found.isEmpty()) {
                        this.playerCards.message(
                                Component.translatable("gui.spwallet.description.no_player_cards"),
                                EssentialColors.error());
                    } else {
                        this.playerCards.show(found, preferred);
                    }

                    this.revalidate.run();
                }, client)
                .exceptionallyAsync(throwable -> {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    SPWallet.LOGGER.warn("Could not look up the cards of {}", nickname, cause);

                    // Only a refusal about the name itself means there is no such player; the API
                    // being down says nothing either way.
                    ApiException.Kind kind = ApiException.kindOf(throwable);
                    this.playerCards.message(kind == ApiException.Kind.OTHER
                                    ? Component.translatable("gui.spwallet.description.player_not_found", nickname)
                                    : kind.description(),
                            EssentialColors.error());
                    this.revalidate.run();

                    return null;
                }, client);
    }

    private CardList buildCardList(FlowLayout rootComponent) {
        return new CardList(SPWalletClient.cards().cards(this.server),
                card -> {
                    this.selectedCard = card;
                    this.revalidate.run();
                },
                card -> rootComponent.child(this.confirmDeletion(rootComponent, card)));
    }

    /** Drops the current card list and builds it again from storage. */
    private void reloadCards(FlowLayout rootComponent) {
        this.selectedCard = null;
        this.cardList.child(this.buildCardList(rootComponent));
        this.revalidate.run();
    }

    /** The "do you really want to delete this card" overlay. */
    private OverlayContainer<UIComponent> confirmDeletion(FlowLayout rootComponent, Card card) {
        return confirm(Component.translatable("modal.spwallet.delete_card.description").append(card.name() + "?"),
                20, Component.translatable("gui.spwallet.button.delete"), () -> {
                    SPWalletClient.cards().remove(this.server, card);
                    this.reloadCards(rootComponent);
                });
    }

    /**
     * A question over the whole screen, with "no" and a red button that does {@code action}.
     *
     * @param width how wide it is, as a percentage of the screen
     */
    private static OverlayContainer<UIComponent> confirm(Component question, int width, Component confirmLabel,
                                                         Runnable action) {
        AtomicReference<OverlayContainer<UIComponent>> overlay = new AtomicReference<>();

        UIComponent content = UIContainers.verticalFlow(Sizing.fill(width), Sizing.content())
                .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                        .child(UIComponents.label(question)
                                .color(Color.ofArgb(EssentialColors.modalText()))
                                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                                .shadow(EssentialColors.textShadow())
                                .horizontalSizing(Sizing.fill(100)))
                        .child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                                .child(new EssentialButton(EssentialButton.Style.NEUTRAL,
                                        Component.translatable("gui.spwallet.button.no"),
                                        button -> overlay.get().remove())
                                        .horizontalSizing(Sizing.fill(47)))
                                .child(new EssentialButton(EssentialButton.Style.RED, confirmLabel,
                                        button -> {
                                            overlay.get().remove();
                                            action.run();
                                        })
                                        .horizontalSizing(Sizing.fill(47)))
                                .gap(8)
                                .horizontalAlignment(HorizontalAlignment.CENTER))
                        .gap(18)
                        .margins(Insets.of(17)))
                .surface(Surface.flat(EssentialColors.background())
                        .and(Surface.outline(EssentialColors.modalOutline())));

        OverlayContainer<UIComponent> container = UIContainers.overlay(content);
        container.closeOnClick(false);
        container.surface(Surface.flat(EssentialColors.overlayDim()));
        overlay.set(container);

        return container;
    }

    /** Whether the nickname field holds something the API will accept in a URL. */
    private boolean canLookUp() {
        return this.selectedCard != null && NICKNAME.matcher(this.nicknameBox.value()).matches();
    }

    /** The card number a transfer would go to, or {@code null} while the form is incomplete. */
    private @Nullable String receiver() {
        if (this.mode == TransferMode.NUMBER) {
            String number = CardNumber.normalize(this.numberBox.value());
            return CardNumber.is(number) ? number : null;
        }

        return this.selectedTarget == null ? null : this.selectedTarget.number();
    }

    private boolean canTransfer() {
        if (this.selectedCard == null || this.receiver() == null) {
            return false;
        }

        Integer amount = AmountFormat.parse(this.amountBox.value());
        return amount != null && amount > 0 && !this.overBalance(amount);
    }

    /**
     * Whether {@code amount} is more than the selected card holds. Not known until the card's
     * balance has come back, and until then not held against the transfer: the API refuses it
     * anyway.
     */
    private boolean overBalance(int amount) {
        Integer balance = this.selectedCard == null ? null : CardInfoCache.balance(this.selectedCard.id());
        return balance != null && amount > balance;
    }

    /**
     * What the amount field shows at its end: the amount in the other format from the one typed, so
     * {@code 2:10} reads back as {@code = 138 АР} and {@code 138} as {@code = 2 ст. 10 АР}, or why it
     * cannot be sent.
     */
    private EssentialTextBox.@Nullable Suffix amountHint() {
        String value = this.amountBox.value();

        if (value.isBlank()) {
            return null;
        }

        Integer amount = AmountFormat.parse(value);

        if (amount == null) {
            return new EssentialTextBox.Suffix(Component.translatable("gui.spwallet.amount_hint.invalid"),
                    EssentialColors.error());
        }

        if (this.overBalance(amount)) {
            return new EssentialTextBox.Suffix(Component.translatable("gui.spwallet.amount_hint.over_balance"),
                    EssentialColors.error());
        }

        if (AmountFormat.isStacks(value)) {
            return hint(AmountFormat.TOTAL.format(amount));
        }

        // Under a stack both formats read the same.
        return amount < AmountFormat.STACK ? null : hint(AmountFormat.STACKS.format(amount));
    }

    private static EssentialTextBox.Suffix hint(Component amount) {
        return new EssentialTextBox.Suffix(Component.literal("= ").append(amount), EssentialColors.cardBalance());
    }

    private void transfer() {
        Card card = this.selectedCard;
        String receiver = this.receiver();

        if (card == null || receiver == null || !this.canTransfer()) {
            return;
        }

        int value = AmountFormat.parse(this.amountBox.value());
        int threshold = SPWalletConfig.get().confirmTransferAbove;

        if (threshold > 0 && value > threshold) {
            Component to = this.mode == TransferMode.NICKNAME
                    ? Component.translatable("modal.spwallet.confirm_transfer.to_player", receiver, this.nicknameBox.value())
                    : Component.literal(receiver);

            this.root.child(confirm(Component.translatable("modal.spwallet.confirm_transfer.description",
                            AmountFormat.card(value), card.name(), to),
                    36, Component.translatable("gui.spwallet.button.confirm_transfer"),
                    () -> this.send(card, receiver, value)));
            return;
        }

        this.send(card, receiver, value);
    }

    private void send(Card card, String receiver, int value) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;

        String text = this.commentBox.value().isEmpty()
                ? Component.translatable("gui.spwallet.description.no_comment").getString()
                : this.commentBox.value();
        String signed = player == null ? text : text + " - " + player.getGameProfile().name();
        // Read now: by the time the answer comes the form may have been changed.
        String comment = this.commentBox.value().isEmpty() ? null : this.commentBox.value();
        String receiverName = this.mode == TransferMode.NICKNAME ? this.nicknameBox.value() : null;
        SPServer server = this.server;

        SPWorldsApi.transfer(card, new Transaction(receiver, value, signed))
                .thenAcceptAsync(newBalance -> {
                    CardInfoCache.balanceChanged(card, newBalance);
                    SPWalletClient.history().add(new HistoryStorage.Entry(System.currentTimeMillis(),
                            server.key(), card.id(), card.name(), HistoryStorage.Kind.OUT, value,
                            receiver, receiverName, comment, newBalance, false));
                    IncomingNotifications.pushOutgoing(card.name(), CardInfoCache.color(card.id()), value, newBalance,
                            receiverName == null ? receiver : receiver + " (" + receiverName + ")", comment);
                    PaymentSound.playSuccess();
                    MessageScreen.open(
                        Component.translatable("gui.spwallet.title.success"),
                        Component.translatable("gui.spwallet.description.successfully_sent")
                                .append(card.name() + " ")
                                .append(AmountFormat.card(value))
                                .append(Component.translatable("gui.spwallet.description.diamonds_to_the_card"))
                                .append(receiver)
                                .append("\n")
                                .append(Component.translatable("gui.spwallet.description.balance"))
                                .append(AmountFormat.card(newBalance)));
                }, client)
                .exceptionallyAsync(throwable -> {
                    ApiException.Kind kind = ApiException.kindOf(throwable);
                    Component message = Component.literal(ApiException.messageOf(throwable));

                    // The API's own message for a 5xx or a timeout tells the player little; say
                    // what it means first. With no answer at all the transfer may still have gone
                    // through, which is worth checking before sending it again.
                    Component explained = switch (kind) {
                        case OTHER -> message;
                        case UNREACHABLE -> Component.translatable("gui.spwallet.api_error.transfer_unknown")
                                .append("\n").append(message);
                        default -> kind.description().copy().append("\n").append(message);
                    };

                    MessageScreen.open(Component.translatable("gui.spwallet.title.error"), explained);

                    return null;
                }, client);
    }
}
