package ru.foxanto.spwallet.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.WalletScreen;

/**
 * Restores the player's GUI scale when the wallet is replaced by another screen instead of being
 * closed, which bypasses {@link WalletScreen#onClose()}.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    public @Nullable Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void spwallet$restoreGuiScale(Screen screen, CallbackInfo ci) {
        if (screen instanceof WalletScreen) {
            return;
        }

        if (this.screen instanceof WalletScreen wallet && !wallet.closing && SPWalletConfig.get().forceGuiScale) {
            WalletScreen.applyGuiScale(wallet.previousGuiScale());
        }
    }
}
