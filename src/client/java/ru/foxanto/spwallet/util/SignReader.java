package ru.foxanto.spwallet.util;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.SPWallet;

import java.util.Arrays;

/** Turns a sign in the world into a {@link SignPayment}. */
public final class SignReader {
    private SignReader() {}

    /**
     * Reads the payment a sign asks for, or {@code null} when it asks for none.
     *
     * <p>Both sides are read. Which one counts as the front depends on where the player stands, and
     * a hanging sign in particular is routinely written on one side and walked up to from the other;
     * reading only the facing side made those signs look like they did nothing.
     */
    public static @Nullable SignPayment read(SignBlockEntity sign, Player player) {
        boolean facingFront = sign.isFacingFrontText(player);
        SignPayment payment = readSide(sign, player, facingFront);

        return payment != null ? payment : readSide(sign, player, !facingFront);
    }

    private static @Nullable SignPayment readSide(SignBlockEntity sign, Player player, boolean front) {
        // The same filtering the player themselves sees, so the mod never reads hidden text.
        Component[] messages = sign.getText(front).getMessages(player.isTextFilteringEnabled());
        String[] lines = new String[messages.length];

        for (int i = 0; i < messages.length; i++) {
            lines[i] = messages[i].getString();
        }

        SignPayment payment = SignPayment.parse(lines);

        if (DebugData.enabled()) {
            SPWallet.LOGGER.info("Sign {} {}: {} -> {}",
                    sign.getBlockPos(), front ? "front" : "back", Arrays.toString(lines), payment);
        }

        return payment;
    }
}
