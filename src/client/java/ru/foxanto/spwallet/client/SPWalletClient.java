package ru.foxanto.spwallet.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.commands.APICommand;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.AddCardScreen;
import ru.foxanto.spwallet.gui.MessageScreen;
import ru.foxanto.spwallet.gui.WalletScreen;
import ru.foxanto.spwallet.gui.overlay.BalanceHud;
import ru.foxanto.spwallet.gui.overlay.InventoryCardPanel;
import ru.foxanto.spwallet.storage.CardStorage;
import ru.foxanto.spwallet.util.QrScanner;
import ru.foxanto.spwallet.util.SPServer;
import ru.foxanto.spwallet.util.SignPayment;
import ru.foxanto.spwallet.util.SignReader;

import java.util.ArrayList;
import java.util.List;

public class SPWalletClient implements ClientModInitializer {
    /** Tail of the chat message the server prints when a card is created or looked up. */
    private static final String CARD_MESSAGE_SUFFIX =
            "] Управление картой "
                    + "[Копир. токен] "
                    + "[Копир. айди]";

    /** Chat prefixes of the two servers, which are never card names. */
    private static final List<String> SERVER_PREFIXES = List.of("[СП]", "[СПм]");

    /** All of the mod's key bindings live in one category of the controls screen. */
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SPWallet.id("wallet"));

    private static CardStorage cards;
    private static KeyMapping openWalletKey;
    private static KeyMapping interactKey;
    private static KeyMapping scanQrKey;

    /** The player's saved cards. */
    public static CardStorage cards() {
        return cards;
    }

    @Override
    public void onInitializeClient() {
        SPWalletConfig.load();
        cards = new CardStorage();

        // Client command: handled locally, never sent to the Minecraft server.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                APICommand.register(dispatcher));

        openWalletKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.spwallet.open_wallet_screen",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_Z,
                CATEGORY));

        interactKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.spwallet.interact",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_LSHIFT,
                CATEGORY));

        // Unbound by default: a map in a frame is scanned by clicking it, this is for the rest.
        scanQrKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.spwallet.scan_qr",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY));

        // Under the chat, so a message is never hidden behind the balances.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, SPWallet.id("balances"),
                BalanceHud::render);
        InventoryCardPanel.register();

        ClientTickEvents.END_CLIENT_TICK.register(SPWalletClient::onEndTick);
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide() || !SPWalletConfig.get().signPayments) {
                return InteractionResult.PASS;
            }

            if (!(level.getBlockEntity(hitResult.getBlockPos()) instanceof SignBlockEntity sign)) {
                return InteractionResult.PASS;
            }

            LocalPlayer localPlayer = Minecraft.getInstance().player;

            if (localPlayer == null || !interactKeyHeld()) {
                return InteractionResult.PASS;
            }

            return openSignPayment(sign, localPlayer) ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide()) {
                return InteractionResult.PASS;
            }

            LocalPlayer localPlayer = Minecraft.getInstance().player;

            if (localPlayer == null || !interactKeyHeld()) {
                return InteractionResult.PASS;
            }

            // A frame that holds no readable code still rotates its map as usual.
            if (entity instanceof ItemFrame frame) {
                return SPWalletConfig.get().qrPayments && QrScanner.scanFrame(frame)
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS;
            }

            if (!SPWalletConfig.get().playerTransfers) {
                return InteractionResult.PASS;
            }

            return openPlayerTransfer(entity) ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onGameMessage(message));
    }

    /**
     * Whether the key that turns a right click into a wallet action is held.
     *
     * <p>The bound key is read rather than the binding being consumed, because it acts as a
     * modifier for a click. Leaving it unbound means no key is needed at all.
     */
    private static boolean interactKeyHeld() {
        return interactKey.isUnbound() || interactKey.isDown();
    }

    /** Opens the wallet on the by-nickname form, already looking up the player that was clicked. */
    private static boolean openPlayerTransfer(Entity entity) {
        SPServer server = SPServer.current();

        if (server == SPServer.OTHER || !(entity instanceof Player target)) {
            return false;
        }

        LocalPlayer self = Minecraft.getInstance().player;
        String nickname = target.getGameProfile().name();

        if (self != null && nickname.equals(self.getGameProfile().name())) {
            return false;
        }

        Minecraft.getInstance().setScreen(new WalletScreen(server, nickname));
        return true;
    }

    private static void onEndTick(Minecraft client) {
        while (scanQrKey.consumeClick()) {
            if (SPWalletConfig.get().qrPayments) {
                QrScanner.scan(client);
            }
        }

        while (openWalletKey.consumeClick()) {
            SPServer server = SPServer.current();

            if (server == SPServer.OTHER) {
                if(SPWalletConfig.get().isDebuging || FabricLoader.getInstance().isDevelopmentEnvironment()){
                    client.setScreen(new WalletScreen(SPServer.SP));
                    continue;
                }
                MessageScreen.open(
                        Component.translatable("gui.spwallet.title.error"),
                        Component.translatable("gui.spwallet.description.join_to_server"));
            } else {
                client.setScreen(new WalletScreen(server));
            }
        }
    }

    /**
     * Opens the transfer screen filled in from a payment sign.
     *
     * <p>{@link SignReader} decides what counts as a payment sign; this only turns the result into
     * a screen.
     */
    private static boolean openSignPayment(SignBlockEntity sign, LocalPlayer player) {
        SPServer server = SPServer.current();

        if (server == SPServer.OTHER) {
            return false;
        }

        SignPayment payment = SignReader.read(sign, player);

        if (payment == null) {
            return false;
        }

        Minecraft.getInstance().setScreen(
                new WalletScreen(server, payment.target(), payment.amount(), payment.comment()));

        return true;
    }

    /** Offers to save the card described by a "Управление картой" chat message. */
    private static void onGameMessage(Component message) {
        if (!SPWalletConfig.get().captureCardMessages) {
            return;
        }

        SPServer server = SPServer.current();

        if (server == SPServer.OTHER) {
            return;
        }

        String text = message.getString();

        if (!text.startsWith("[") || !text.endsWith(CARD_MESSAGE_SUFFIX)) {
            return;
        }

        if (SERVER_PREFIXES.stream().anyMatch(text::startsWith)) {
            return;
        }

        // The message carries the token first and the card id second, both as copy-to-clipboard clicks.
        List<String> copyable = new ArrayList<>();
        collectCopyableText(message, copyable);

        if (copyable.size() < 2) {
            return;
        }

        String name = text.substring(1, text.length() - CARD_MESSAGE_SUFFIX.length());
        Card card = new Card(name, copyable.get(1), copyable.get(0));

        if (cards.contains(server, card.id())) {
            return;
        }

        Minecraft.getInstance().setScreen(new AddCardScreen(server, card));
    }

    private static void collectCopyableText(Component component, List<String> into) {
        if (component.getStyle().getClickEvent() instanceof ClickEvent.CopyToClipboard copy) {
            into.add(copy.value());
        }

        for (Component sibling : component.getSiblings()) {
            collectCopyableText(sibling, into);
        }
    }
}
