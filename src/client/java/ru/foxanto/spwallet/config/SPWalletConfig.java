package ru.foxanto.spwallet.config;

import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.util.PaymentSound;

public class SPWalletConfig {
    public static final ConfigClassHandler<SPWalletConfig> HANDLER = ConfigClassHandler.createBuilder(SPWalletConfig.class)
            .id(SPWallet.id("config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("spwallet.json5"))
                    .setJson5(true)
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting)
                    .build())
            .build();
    /** Debuging mode */
    @SerialEntry
    public boolean isDebuging = false;

    /** Offer to save a card when the server prints its "Управление картой" message. */
    @SerialEntry
    public boolean captureCardMessages = true;

    /** Open the transfer screen when a player is right clicked with the modifier held. */
    @SerialEntry
    public boolean playerTransfers = true;

    /** Open the transfer screen when a sign whose first line is {@code #SPWP} is used. */
    @SerialEntry
    public boolean signPayments = true;

    /**
     * Read QR codes drawn on maps: by clicking a framed map with the modifier held, or with the scan
     * key, which also looks at a held map and, failing that, the whole screen.
     */
    @SerialEntry
    public boolean qrPayments = true;

    /** Play a chime when a transfer goes through. */
    @SerialEntry
    public boolean paymentSound = true;

    /**
     * Which sound that is, as {@link ru.foxanto.spwallet.util.PaymentSound} saves it: a sound event,
     * an {@code .ogg} from a resource pack or a file from the audios folder.
     */
    @SerialEntry
    public String paymentSoundChoice = PaymentSound.DEFAULT;

    /** How loud the mod's sounds are, from 0 to 1. */
    @SerialEntry
    public float paymentSoundVolume = 0.8F;

    /**
     * Show a notification when a card's balance goes up. A stopgap until the SPWorlds API can list
     * transactions: the mod checks the balances every {@link #incomingCheckSeconds} and reports any rise.
     */
    @SerialEntry
    public boolean incomingNotifications = true;

    /**
     * How often the balances are checked for that, in seconds. Each check is one request per card;
     * with many cards the checks are spaced out further so the API's 200 requests a minute are not
     * used up.
     */
    @SerialEntry
    public int incomingCheckSeconds = 5;

    /** Play a sound with that notification. */
    @SerialEntry
    public boolean incomingSound = true;

    /** Which sound, saved the same way as {@link #paymentSoundChoice}. */
    @SerialEntry
    public String incomingSoundChoice = PaymentSound.DEFAULT_INCOMING;

    /**
     * Where the notifications appear, as a fraction of the free room like {@link #hudX}: the bottom
     * left corner by default.
     */
    @SerialEntry
    public float notificationX = 0;

    @SerialEntry
    public float notificationY = 1;

    /** Which palette the mod's screens and panels are drawn in. */
    @SerialEntry
    public Theme theme = Theme.DARK;

    /** Show the balances of the saved cards on the HUD. */
    @SerialEntry
    public boolean hudEnabled = true;

    /**
     * Where the HUD balances sit, as a fraction of the free room across and down the screen:
     * {@code 0} is the left or top edge, {@code 1} the right or bottom one.
     */
    @SerialEntry
    public float hudX = 0;

    @SerialEntry
    public float hudY = 0;

    /** Show the saved cards next to the player's inventory. */
    @SerialEntry
    public boolean inventoryPanel = true;

    /** Which side of the inventory that panel is on. */
    @SerialEntry
    public PanelSide inventoryPanelSide = PanelSide.RIGHT;

    /**
     * Where the inventory panel sits when it was dragged ({@link PanelSide#FREE}), as a fraction of
     * the free room across and down the screen, like {@link #hudX} and {@link #hudY}.
     */
    @SerialEntry
    public float inventoryPanelX = 1;

    @SerialEntry
    public float inventoryPanelY = 0;

    /**
     * Force GUI scale 2 while the wallet is open, like SPWorlds Pay did. The screen is laid out for
     * that scale, so turning this off can make it look cramped on other scales.
     */
    @SerialEntry
    public boolean forceGuiScale = true;

    public static SPWalletConfig get() {
        return HANDLER.instance();
    }

    public static void load() {
        HANDLER.load();
    }
}
