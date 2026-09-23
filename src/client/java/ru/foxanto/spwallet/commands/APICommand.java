package ru.foxanto.spwallet.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import ru.foxanto.spwallet.SPWallet;
import ru.foxanto.spwallet.api.Card;
import ru.foxanto.spwallet.api.SPWorldsApi;
import ru.foxanto.spwallet.client.SPWalletClient;
import ru.foxanto.spwallet.util.SPServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /spwapi} — sends one request to the SPWorlds API with a saved card's credentials and prints
 * the reply into chat exactly as it arrived.
 *
 * <p>This is a client command: nothing is sent to the Minecraft server, and the output is only
 * visible locally. It deliberately ignores the debug stubs, so what it shows is what the real API
 * answered.
 */
public final class APICommand {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    /** Endpoints of the public API that only need a card token. */
    private static final List<String> ENDPOINTS = List.of("card", "accounts/me");

    private APICommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("spwapi")
                .then(ClientCommandManager.argument("card", CardNameArgument.cardName())
                        .suggests(cardSuggestions())
                        .executes(context -> run(context, "card"))
                        .then(ClientCommandManager.argument("endpoint", StringArgumentType.greedyString())
                                .suggests(endpointSuggestions())
                                .executes(context ->
                                        run(context, StringArgumentType.getString(context, "endpoint"))))));
    }

    private static int run(CommandContext<FabricClientCommandSource> context, String endpoint) {
        FabricClientCommandSource source = context.getSource();
        String query = context.getArgument("card", String.class);
        Card card = findCard(query);

        if (card == null) {
            source.sendError(Component.translatable("command.spwallet.api.unknown_card", query));
            return 0;
        }

        String url = SPWorldsApi.API_URL + endpoint;
        source.sendFeedback(Component.literal("GET " + url).withStyle(ChatFormatting.GRAY));
        source.sendFeedback(Component.translatable("command.spwallet.api.using_card",
                        Component.literal(card.name()).withStyle(ChatFormatting.WHITE),
                        Component.literal(card.id()).withStyle(ChatFormatting.WHITE),
                        Component.literal(maskedAuthorization(card)).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));

        Minecraft client = source.getClient();

        SPWorldsApi.raw(card, endpoint, "GET", null)
                .thenAcceptAsync(response -> report(source, url, response), client)
                .exceptionallyAsync(throwable -> {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    SPWallet.LOGGER.warn("/spwapi {} failed", url, cause);
                    source.sendError(Component.literal(String.valueOf(cause.getMessage())));
                    return null;
                }, client);

        return 1;
    }

    private static void report(FabricClientCommandSource source, String url, SPWorldsApi.RawResponse response) {
        SPWallet.LOGGER.info("/spwapi {} -> HTTP {} {}", url, response.status(), response.body());

        ChatFormatting statusColor = response.status() / 100 == 2 ? ChatFormatting.GREEN : ChatFormatting.RED;
        source.sendFeedback(Component.literal("HTTP " + response.status()).withStyle(statusColor));

        source.sendFeedback(Component.literal(prettify(response.body()))
                .withStyle(ChatFormatting.WHITE)
                .append(" ")
                .append(Component.translatable("command.spwallet.api.copy")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent.CopyToClipboard(response.body()))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.translatable("command.spwallet.api.copy.tooltip"))))));
    }

    /** Pretty-prints the body when it is JSON, and leaves it untouched when it is not. */
    private static String prettify(String body) {
        try {
            return PRETTY.toJson(PRETTY.fromJson(body, JsonElement.class));
        } catch (JsonSyntaxException e) {
            return body;
        }
    }

    /**
     * The {@code Authorization} header with all but its first characters hidden. Enough to tell that
     * a header is being sent and which card it belongs to, without putting the token in the chat log.
     */
    private static String maskedAuthorization(Card card) {
        String header = card.authorization();
        int keep = Math.min(header.length(), "Bearer ".length() + 6);
        return header.substring(0, keep) + "…";
    }

    /**
     * Finds a saved card by {@code "<server>/<name>"} or by name alone, searching the current server
     * first so that the same name on SP and SPm resolves to the one in front of the player.
     */
    private static Card findCard(String query) {
        int separator = query.indexOf('/');

        if (separator > 0) {
            SPServer server = serverByKey(query.substring(0, separator));
            return server == null ? null : findCard(server, query.substring(separator + 1));
        }

        for (SPServer server : searchOrder()) {
            Card card = findCard(server, query);

            if (card != null) {
                return card;
            }
        }

        return null;
    }

    private static Card findCard(SPServer server, String name) {
        return SPWalletClient.cards().cards(server).stream()
                .filter(card -> card.name().equalsIgnoreCase(name) || card.id().equals(name))
                .findFirst()
                .orElse(null);
    }

    /** The real servers, the one the player is on first. */
    private static List<SPServer> searchOrder() {
        List<SPServer> order = new ArrayList<>();
        SPServer current = SPServer.current();

        if (current != SPServer.OTHER) {
            order.add(current);
        }

        for (SPServer server : SPServer.values()) {
            if (server.key() != null && !order.contains(server)) {
                order.add(server);
            }
        }

        return order;
    }

    private static SPServer serverByKey(String key) {
        for (SPServer server : SPServer.values()) {
            if (key.equalsIgnoreCase(server.key())) {
                return server;
            }
        }

        return null;
    }

    private static SuggestionProvider<FabricClientCommandSource> cardSuggestions() {
        return (context, builder) -> {
            List<String> names = new ArrayList<>();

            for (SPServer server : searchOrder()) {
                for (Card card : SPWalletClient.cards().cards(server)) {
                    String name = server.key() + "/" + card.name();
                    // A space would end the argument, so such a name is offered already quoted.
                    names.add(name.contains(" ")
                            ? '"' + name.replace("\\", "\\\\").replace("\"", "\\\"") + '"'
                            : name);
                }
            }

            return SharedSuggestionProvider.suggest(names, builder);
        };
    }

    private static SuggestionProvider<FabricClientCommandSource> endpointSuggestions() {
        return (context, builder) -> {
            List<String> endpoints = new ArrayList<>(ENDPOINTS);
            String username = Minecraft.getInstance().getUser().getName();
            endpoints.add("accounts/" + username + "/cards");

            return suggestGreedy(endpoints, builder);
        };
    }

    /**
     * {@link SharedSuggestionProvider#suggest} matches against the last word, which does not work for
     * a greedy argument that may already hold a slash-separated path.
     */
    private static CompletableFuture<Suggestions> suggestGreedy(List<String> options, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();

        for (String option : options) {
            if (option.toLowerCase().startsWith(typed)) {
                builder.suggest(option);
            }
        }

        return builder.buildFuture();
    }
}
