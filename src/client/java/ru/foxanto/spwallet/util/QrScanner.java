package ru.foxanto.spwallet.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.WalletScreen;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Reads QR codes drawn on maps and turns them into a transfer.
 *
 * <p>A map is read straight from its data rather than from the screen whenever there is one to
 * read: 128 by 128 exact pixels decode far more reliably than the same code seen at an angle,
 * shaded and under the crosshair. The screen is only the fallback, for codes spread over several
 * maps or drawn some other way.
 */
public final class QrScanner {
    private static final int MAP_SIZE = 128;

    private QrScanner() {}

    /**
     * Scans on request: the map in the frame being looked at, then a map in either hand, then the
     * whole screen.
     */
    public static void scan(Minecraft client) {
        LocalPlayer player = client.player;

        if (player == null || client.level == null) {
            return;
        }

        if (client.crosshairPickEntity instanceof ItemFrame frame) {
            int[] map = mapPixels(frame.getItem(), client.level);

            if (map != null) {
                decodeAsync(() -> QrDecoder.decodeMap(map, MAP_SIZE));
                return;
            }
        }

        for (ItemStack held : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            int[] map = mapPixels(held, client.level);

            if (map != null) {
                decodeAsync(() -> QrDecoder.decodeMap(map, MAP_SIZE));
                return;
            }
        }

        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels;

            try (NativeImage owned = image) {
                pixels = owned.getPixels();
            }

            decodeAsync(() -> QrDecoder.decode(pixels, width, height));
        });
    }

    /**
     * Reads the map in a clicked item frame right away, so the click can be cancelled only when the
     * map really holds a code. Returns whether it did; a frame without one is left to rotate as usual.
     */
    public static boolean scanFrame(ItemFrame frame) {
        int[] map = mapPixels(frame.getItem(), frame.level());

        if (map == null) {
            return false;
        }

        String text = QrDecoder.decodeMap(map, MAP_SIZE);

        if (text == null) {
            return false;
        }

        handle(text);
        return true;
    }

    /** The map's pixels as ARGB, or {@code null} when the stack is not a map the client knows. */
    private static int @Nullable [] mapPixels(ItemStack stack, Level level) {
        MapItemSavedData data = MapItem.getSavedData(stack, level);

        if (data == null) {
            return null;
        }

        int[] argb = new int[MAP_SIZE * MAP_SIZE];

        for (int i = 0; i < argb.length; i++) {
            argb[i] = MapColor.getColorFromPackedId(data.colors[i] & 0xFF);
        }

        return argb;
    }

    /** Decoding a full screen takes long enough to be felt, so it never runs on the game thread. */
    private static void decodeAsync(Supplier<@Nullable String> decode) {
        Minecraft client = Minecraft.getInstance();

        CompletableFuture.supplyAsync(decode, Util.backgroundExecutor())
                .whenComplete((text, error) -> client.execute(() -> {
                    if (error != null) {
                        SPWallet.LOGGER.warn("QR decoding failed", error);
                    }

                    if (text == null) {
                        message(Component.translatable("message.spwallet.qr.not_found")
                                .withStyle(ChatFormatting.GRAY));
                    } else {
                        handle(text);
                    }
                }));
    }

    /**
     * Acts on a code: a transfer opens the wallet, a link asks whether to open it, and anything else
     * is shown in chat. The chat line is only ever local; nothing is sent to the server.
     */
    private static void handle(String text) {
        if (DebugData.enabled()) {
            SPWallet.LOGGER.info("QR code: {}", text);
        }

        Minecraft client = Minecraft.getInstance();
        SPServer server = SPServer.current();

        if (server == SPServer.OTHER
                && (SPWalletConfig.get().isDebuging || FabricLoader.getInstance().isDevelopmentEnvironment())) {
            server = SPServer.SP;
        }

        SignPayment payment = QrPayment.parse(text);

        if (payment != null && server != SPServer.OTHER) {
            client.setScreen(new WalletScreen(server, payment.target(), payment.amount(), payment.comment()));
            return;
        }

        String trimmed = text.trim();
        URI link = QrPayment.isLink(trimmed) ? toUri(trimmed) : null;

        Component shown = Component.literal(trimmed).withStyle(style -> link != null
                ? style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(link))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("message.spwallet.qr.open_link")))
                : style.withColor(ChatFormatting.WHITE)
                        .withClickEvent(new ClickEvent.CopyToClipboard(trimmed))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("message.spwallet.qr.copy"))));

        message(Component.translatable("message.spwallet.qr.found", shown).withStyle(ChatFormatting.GRAY));

        if (link != null) {
            // The vanilla prompt: open, copy or cancel. The chat line stays for a later click.
            ConfirmLinkScreen.confirmLinkNow(client.screen, link);
        }
    }

    private static @Nullable URI toUri(String text) {
        try {
            return URI.create(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void message(Component message) {
        Minecraft.getInstance().gui.getChat().addMessage(message);
    }
}
