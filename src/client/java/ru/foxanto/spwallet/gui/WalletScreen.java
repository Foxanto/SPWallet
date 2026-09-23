package ru.foxanto.spwallet.gui;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.api.CardNumber;
import ru.foxanto.spwallet.api.PlayerCard;
import ru.foxanto.spwallet.api.SPWorldsApi;
import ru.foxanto.spwallet.api.Transaction;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.component.CardList;
import ru.foxanto.spwallet.gui.component.EssentialButton;
import ru.foxanto.spwallet.gui.component.EssentialScrollContainer;
import ru.foxanto.spwallet.gui.component.EssentialTextBox;
import ru.foxanto.spwallet.gui.component.PlayerCardList;
import ru.foxanto.spwallet.gui.component.TabBar;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.PaymentSound;
import ru.foxanto.spwallet.util.SPServer;
import ru.foxanto.spwallet.util.TransferMode;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** The wallet: the saved cards on the left, the transfer form on the right. */
public class WalletScreen extends EssentialScreen {
    /** The GUI scale this screen is laid out for. */
    private static final int WALLET_GUI_SCALE = 2;

    /**
     * Width of the card list panel, as a percentage. The transfer panel takes the rest. The card
     * rows fit a name, a number and the delete button, so this cannot go much below 30.
     */
    private static final int CARD_PANEL_PERCENT = 40;

    private static final int MAX_AMOUNT_DIGITS = 6;
    private static final int MAX_COMMENT_LENGTH = 45;
    private static final int MAX_USERNAME_LENGTH = 16;

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

    private EssentialScrollContainer cardList;
    private FlowLayout form;
    private EssentialTextBox numberBox;
    private EssentialTextBox nicknameBox;
    private EssentialTextBox amountBox;
    private EssentialTextBox commentBox;
    private EssentialButton findButton;
    private EssentialButton transferButton;
    private PlayerCardList playerCards;
    private EssentialScrollContainer playerCardsScroll;
    private Runnable revalidate = () -> {};

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
        this.numberBox = new EssentialTextBox(Sizing.fill(100),
                Component.translatable("gui.spwallet.input.transfer.card_number"));
        this.numberBox.textBox.setMaxLength(CardNumber.LENGTH);
        this.numberBox.textBox.setFilter(CardNumber::isBeingTyped);

        this.nicknameBox = new EssentialTextBox(Sizing.fill(100),
                Component.translatable("gui.spwallet.input.transfer.nickname"));
        this.nicknameBox.textBox.setMaxLength(MAX_USERNAME_LENGTH);

        this.amountBox = new EssentialTextBox(Sizing.fill(100),
                Component.translatable("gui.spwallet.input.transfer.amount"));
        this.amountBox.textBox.setMaxLength(MAX_AMOUNT_DIGITS);

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

        this.form = UIContainers.verticalFlow(Sizing.fill(70), Sizing.content());
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

        TabBar<TransferMode> modeTabs = new TabBar<>(List.of(TransferMode.values()), this.mode,
                TransferMode::label, selected -> {
            this.mode = selected;
            this.rebuildForm();
            this.revalidate.run();
        });
        modeTabs.padding(Insets.of(10, 10, 12, 12));

        this.revalidate.run();

        rootComponent
                .child(UIContainers.verticalFlow(Sizing.fill(85), Sizing.content())
                        .child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30))
                                .child(UIContainers.horizontalFlow(Sizing.fill(CARD_PANEL_PERCENT), Sizing.fill(100))
                                        .child(UIComponents.label(Component.translatable("gui.spwallet.title.cards"))
                                                .color(Color.ofArgb(EssentialColors.SCREEN_TITLE))
                                                .shadow(true)
                                                .margins(Insets.of(11, 0, 13, 0)))
                                        .surface(EssentialSurfaces.NAV_LEFT))
                                .child(UIContainers.horizontalFlow(Sizing.fill(100 - CARD_PANEL_PERCENT), Sizing.fill(100))
                                        .child(UIComponents.label(Component.translatable("gui.spwallet.title.transfer"))
                                                .color(Color.ofArgb(EssentialColors.SCREEN_TITLE))
                                                .shadow(true)
                                                .margins(Insets.of(11, 0, 10, 0)))
                                        .surface(EssentialSurfaces.NAV_RIGHT)))

                        .child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30))
                                .child(UIContainers.horizontalFlow(Sizing.fill(CARD_PANEL_PERCENT), Sizing.fill(100))
                                        .child(serverTabs)
                                        .surface(EssentialSurfaces.PANEL_LEFT))
                                .child(UIContainers.horizontalFlow(Sizing.fill(100 - CARD_PANEL_PERCENT), Sizing.fill(100))
                                        .child(modeTabs)
                                        .surface(EssentialSurfaces.PANEL_RIGHT_TOP)))

                        .child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(72))
                                .child(UIContainers.horizontalFlow(Sizing.fill(CARD_PANEL_PERCENT), Sizing.fill(100))
                                        .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100))
                                                .child(this.cardList)
                                                .margins(Insets.of(0, 3, 3, 0)))
                                        .surface(EssentialSurfaces.PANEL_LEFT))
                                .child(UIContainers.horizontalFlow(Sizing.fill(100 - CARD_PANEL_PERCENT), Sizing.fill(100))
                                        .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100))
                                                .child(this.form)
                                                .margins(Insets.of(0, 8, 0, 3))
                                                .horizontalAlignment(HorizontalAlignment.CENTER)
                                                .verticalAlignment(VerticalAlignment.CENTER))
                                        .surface(EssentialSurfaces.PANEL_RIGHT))))

                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER)
                .surface(Surface.flat(EssentialColors.BACKGROUND));

        if (this.mode == TransferMode.NICKNAME && this.prefilledTarget != null) {
            this.findPlayerCards();
        }
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
        Card card = this.selectedCard;
        String nickname = this.nicknameBox.value();

        if (card == null || nickname.isEmpty()) {
            return;
        }

        // playerCards() rejects a name the API cannot take in a URL by throwing, and this method is
        // also called straight from build() - an exception there would blank the whole screen.
        if (!NICKNAME.matcher(nickname).matches()) {
            this.playerCards.message(Component.translatable("gui.spwallet.description.bad_nickname"),
                    EssentialColors.ERROR);
            return;
        }

        Minecraft client = Minecraft.getInstance();
        this.playerCards.message(Component.translatable("gui.spwallet.description.searching"),
                EssentialColors.CARD_BALANCE);

        SPWorldsApi.playerCards(card, nickname)
                .thenAcceptAsync(found -> {
                    if (found.isEmpty()) {
                        this.playerCards.message(
                                Component.translatable("gui.spwallet.description.no_player_cards"),
                                EssentialColors.ERROR);
                    } else {
                        this.playerCards.show(found);
                    }

                    this.revalidate.run();
                }, client)
                .exceptionallyAsync(throwable -> {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    SPWallet.LOGGER.warn("Could not look up the cards of {}", nickname, cause);

                    this.playerCards.message(
                            Component.translatable("gui.spwallet.description.player_not_found", nickname),
                            EssentialColors.ERROR);
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
        AtomicReference<OverlayContainer<UIComponent>> overlay = new AtomicReference<>();

        UIComponent content = UIContainers.verticalFlow(Sizing.fill(20), Sizing.content())
                .child(UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                        .child(UIComponents.label(Component
                                        .translatable("modal.spwallet.delete_card.description")
                                        .append(card.name() + "?"))
                                .color(Color.ofArgb(EssentialColors.MODAL_TEXT))
                                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                                .shadow(true)
                                .horizontalSizing(Sizing.fill(100)))
                        .child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                                .child(new EssentialButton(EssentialButton.Style.NEUTRAL,
                                        Component.translatable("gui.spwallet.button.no"),
                                        button -> overlay.get().remove())
                                        .horizontalSizing(Sizing.fill(47)))
                                .child(new EssentialButton(EssentialButton.Style.RED,
                                        Component.translatable("gui.spwallet.button.delete"),
                                        button -> {
                                            SPWalletClient.cards().remove(this.server, card);
                                            overlay.get().remove();
                                            this.reloadCards(rootComponent);
                                        })
                                        .horizontalSizing(Sizing.fill(47)))
                                .gap(8)
                                .horizontalAlignment(HorizontalAlignment.CENTER))
                        .gap(18)
                        .margins(Insets.of(17)))
                .surface(Surface.flat(EssentialColors.BACKGROUND)
                        .and(Surface.outline(EssentialColors.MODAL_OUTLINE)));

        OverlayContainer<UIComponent> container = UIContainers.overlay(content);
        container.closeOnClick(false);
        container.surface(Surface.flat(EssentialColors.OVERLAY_DIM));
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

        String value = this.amountBox.value();

        if (!value.matches("[0-9]+")) {
            return false;
        }

        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void transfer() {
        Card card = this.selectedCard;
        String receiver = this.receiver();

        if (card == null || receiver == null || !this.canTransfer()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;

        int value = Integer.parseInt(this.amountBox.value());
        String text = this.commentBox.value().isEmpty()
                ? Component.translatable("gui.spwallet.description.no_comment").getString()
                : this.commentBox.value();
        String signed = player == null ? text : text + " - " + player.getGameProfile().name();

        SPWorldsApi.transfer(card, new Transaction(receiver, value, signed))
                .thenAcceptAsync(newBalance -> {
                    CardInfoCache.balanceChanged(card, newBalance);
                    PaymentSound.playSuccess();
                    MessageScreen.open(
                        Component.translatable("gui.spwallet.title.success"),
                        Component.translatable("gui.spwallet.description.successfully_sent")
                                .append(card.name() + " " + value)
                                .append(Component.translatable("gui.spwallet.description.diamonds_to_the_card"))
                                .append(receiver)
                                .append("\n")
                                .append(Component.translatable("gui.spwallet.description.balance"))
                                .append(String.valueOf(newBalance)));
                }, client)
                .exceptionallyAsync(throwable -> {
                    MessageScreen.open(
                            Component.translatable("gui.spwallet.title.error"),
                            Component.literal(String.valueOf(throwable.getCause() == null
                                    ? throwable.getMessage()
                                    : throwable.getCause().getMessage())));

                    return null;
                }, client);
    }
}
