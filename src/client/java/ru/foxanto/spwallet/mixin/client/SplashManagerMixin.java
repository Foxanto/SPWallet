package ru.foxanto.spwallet.mixin.client;

import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds SPWorlds Pay's splash text to the title screen, kept as a nod to the original mod.
 *
 * <p>Vanilla hands {@code apply} an immutable list, so the argument is swapped for a copy rather
 * than added to in place. The line is repeated to give it a realistic chance of showing up.
 */
@Mixin(SplashManager.class)
public class SplashManagerMixin {
    private static final String SPLASH_TEXT = "Jabochca_Soviet ест чизкейки";
    private static final int COPIES = 15;

    @ModifyVariable(
            method = "apply(Ljava/util/List;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private List<Component> spwallet$addSplash(List<Component> splashes) {
        List<Component> combined = new ArrayList<>(splashes);
        Component splash = Component.literal(SPLASH_TEXT).setStyle(Style.EMPTY.withColor(0xFFFF00));

        for (int i = 0; i < COPIES; i++) {
            combined.add(splash);
        }

        return combined;
    }
}
