package ru.foxanto.spwallet.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.api.AccountCard;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.api.SPWorldsApi;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.util.SPServer;

import java.util.regex.Pattern;

/**
 * {@code /spwadd <id> <token> [name]} — saves a card from the id and token the player copied out of
 * the server's "Управление картой" message.
 *
 * <p>The chat message is normally picked up on its own, but chat mods that restyle messages (prefixes,
 * timestamps, stripped click events) make it unrecognisable. This is the way around them: the player
 * clicks "[Копир. айди]" and "[Копир. токен]" and pastes both here. Nothing is sent to the Minecraft
 * server; the credentials are checked against the API before the card is saved.
 */
public final class AddCardCommand {
    /** Card ids are UUIDs, which tells them apart from tokens when they are pasted the wrong way round. */
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private AddCardCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("spwadd")
                .then(ClientCommandManager.argument("id", WordArgument.word())
                        .then(ClientCommandManager.argument("token", WordArgument.word())
                                .executes(context -> run(context, null))
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> run(context,
                                                StringArgumentType.getString(context, "name")))))));
    }

    private static int run(CommandContext<FabricClientCommandSource> context, String name) {
        FabricClientCommandSource source = context.getSource();
        SPServer server = SPServer.current();

        if (server == SPServer.OTHER && (SPWalletConfig.get().isDebuging
                || FabricLoader.getInstance().isDevelopmentEnvironment())) {
            server = SPServer.SP;
        }

        if (server == SPServer.OTHER) {
            source.sendError(Component.translatable("gui.spwallet.description.join_to_server"));
            return 0;
        }

        String id = context.getArgument("id", String.class);
        String token = context.getArgument("token", String.class);

        if (!UUID.matcher(id).matches() && UUID.matcher(token).matches()) {
            String swap = id;
            id = token;
            token = swap;
        }

        if (SPWalletClient.cards().contains(server, id)) {
            source.sendError(Component.translatable("command.spwallet.add.already_saved"));
            return 0;
        }

        String cardId = id;
        String cardToken = token;
        SPServer cardServer = server;
        source.sendFeedback(Component.translatable("command.spwallet.add.checking").withStyle(ChatFormatting.GRAY));

        // One request both proves the credentials work and supplies the card number as a default name.
        SPWorldsApi.accountCards(new Card(cardId, cardId, cardToken))
                .thenAcceptAsync(accountCards -> {
                    if (SPWalletClient.cards().contains(cardServer, cardId)) {
                        source.sendError(Component.translatable("command.spwallet.add.already_saved"));
                        return;
                    }

                    AccountCard accountCard = accountCards.get(cardId);
                    String cardName = name != null && !name.isBlank() ? name.strip()
                            : accountCard != null ? accountCard.number()
                            : cardId;

                    SPWalletClient.cards().add(cardServer, new Card(cardName, cardId, cardToken));
                    source.sendFeedback(Component.translatable("command.spwallet.add.saved",
                                    Component.literal(cardName).withStyle(ChatFormatting.WHITE), cardServer.label())
                            .withStyle(ChatFormatting.GREEN));
                }, source.getClient())
                .exceptionallyAsync(throwable -> {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    SPWallet.LOGGER.warn("/spwadd failed", cause);
                    source.sendError(Component.translatable("command.spwallet.add.failed",
                            String.valueOf(cause.getMessage())));
                    return null;
                }, source.getClient());

        return 1;
    }
}
