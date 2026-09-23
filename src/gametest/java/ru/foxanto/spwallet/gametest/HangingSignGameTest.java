package ru.foxanto.spwallet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import ru.foxanto.spwallet.util.SignPayment;
import ru.foxanto.spwallet.util.SignReader;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts real signs into a world and reads them back through {@link SignReader}.
 *
 * <p>{@link SignPaymentGameTest} covers the parsing of plain strings; this covers the step before
 * it, where the text is pulled off a block entity. Hanging signs are the reason it exists: they are
 * a {@code HangingSignBlockEntity}, and which of their two sides counts as the front depends on
 * where the player is standing.
 */
final class HangingSignGameTest {
    private static final BlockPos STANDING = new BlockPos(0, -58, 0);
    private static final BlockPos HANGING = new BlockPos(2, -58, 0);
    private static final BlockPos HANGING_BACK = new BlockPos(4, -58, 0);

    private HangingSignGameTest() {}

    static void run(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            List<String> failures = singleplayer.getServer().computeOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                List<String> problems = new ArrayList<>();

                place(level, STANDING, Blocks.OAK_SIGN, true, "Карта: 79907", "", "100 АР", "");
                place(level, HANGING, Blocks.OAK_HANGING_SIGN, true, "Перевод Foxanto", "", "64 АР", "");
                // Written on the side the player is not looking at.
                place(level, HANGING_BACK, Blocks.OAK_HANGING_SIGN, false, "Карта: 12345", "", "7 АР", "");

                check(problems, level, player, STANDING, "standing sign", "79907", 100);
                check(problems, level, player, HANGING, "hanging sign", "Foxanto", 64);
                check(problems, level, player, HANGING_BACK, "hanging sign, written on the back", "12345", 7);

                return problems;
            });

            if (!failures.isEmpty()) {
                throw new AssertionError("Signs in the world are not read correctly:\n  "
                        + String.join("\n  ", failures));
            }
        }
    }

    private static void place(ServerLevel level, BlockPos pos, Block block, boolean front, String... lines) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());

        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            throw new AssertionError(block + " did not produce a sign block entity");
        }

        Component[] messages = new Component[lines.length];

        for (int i = 0; i < lines.length; i++) {
            messages[i] = Component.literal(lines[i]);
        }

        sign.setText(new SignText(messages, messages, DyeColor.BLACK, false), front);
    }

    private static void check(List<String> problems, ServerLevel level, Player player, BlockPos pos,
                              String what, String target, int amount) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            problems.add(what + ": no sign block entity at " + pos);
            return;
        }

        SignPayment payment = SignReader.read(sign, player);

        if (payment == null) {
            problems.add(what + ": read as not a payment sign");
            return;
        }

        if (!payment.target().equals(target) || payment.amount() != amount) {
            problems.add(what + ": got " + payment + ", wanted target=" + target + ", amount=" + amount);
        }
    }
}
