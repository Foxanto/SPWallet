package ru.foxanto.spwallet.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.config.SPWalletConfig;

/**
 * The chime played when a transfer goes through.
 *
 * <p>The sound is the mod's own, synthesised for it, and is defined in {@code sounds.json} as
 * {@code spwallet:payment_success}, so a resource pack can swap in any other sound under that name.
 *
 * <p>The event is deliberately not put in the sound registry. The sound manager looks sounds up by
 * id in {@code sounds.json} alone, and a registry entry from a client-only mod is one more thing
 * that could disagree with a Fabric server during registry sync.
 */
public final class PaymentSound {
    private static final SoundEvent SUCCESS = SoundEvent.createVariableRangeEvent(SPWallet.id("payment_success"));

    private PaymentSound() {}

    public static void playSuccess() {
        if (!SPWalletConfig.get().paymentSound) {
            return;
        }

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SUCCESS, 1.0F, 0.8F));
    }
}
