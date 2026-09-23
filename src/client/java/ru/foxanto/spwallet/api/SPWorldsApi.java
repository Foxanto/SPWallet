package ru.foxanto.spwallet.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.util.DebugData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;

/**
 * Thin client for the SPWorlds public API, documented at
 * <a href="https://github.com/sp-worlds/api-docs/wiki">github.com/sp-worlds/api-docs</a>.
 *
 * <p>Every call is asynchronous: the returned future completes on an HTTP client thread, so UI code
 * has to hop back onto the render thread itself (see {@code thenAcceptAsync(..., Minecraft)}).
 *
 * <p>The whole API allows 200 requests per minute. Opening the wallet costs one request per saved
 * card, so keep any new polling well clear of that.
 */
public final class SPWorldsApi {
    public static final String API_URL = "https://spworlds.ru/api/public/";

    /** What the API accepts inside a URL path. Anything else comes back as a bare 400. */
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SPWorldsApi() {}

    /** Reads the current balance of {@code card}. */
    public static CompletableFuture<Integer> balance(Card card) {
        return request(card, "card", "GET", null).thenApply(SPWorldsApi::balanceOf);
    }

    /**
     * Transfers {@code transaction.amount()} from {@code card} to the card it names.
     *
     * @return the balance of {@code card} after the transfer, which the API answers with
     */
    public static CompletableFuture<Integer> transfer(Card card, Transaction transaction) {
        return request(card, "transactions", "POST", GSON.toJson(transaction))
                .thenApply(SPWorldsApi::balanceOf);
    }

    /**
     * Reads the number and colour of every card of the account {@code card} belongs to, keyed by
     * card id.
     *
     * <p>{@code /accounts/me} answers with every card the token owner has, so one request covers the
     * whole wallet no matter how many cards are saved.
     */
    public static CompletableFuture<Map<String, AccountCard>> accountCards(Card card) {
        return request(card, "accounts/me", "GET", null).thenApply(json -> {
            if (!json.has("cards")) {
                throw new ApiException("Response contained no cards");
            }

            Map<String, AccountCard> cards = new HashMap<>();

            for (JsonElement element : json.getAsJsonArray("cards")) {
                JsonObject accountCard = element.getAsJsonObject();

                if (accountCard.has("id") && accountCard.has("number")) {
                    cards.put(accountCard.get("id").getAsString(), new AccountCard(
                            accountCard.get("number").getAsString(), colorOf(accountCard.get("color"))));
                }
            }

            return cards;
        });
    }

    /**
     * Reads a card colour, which the API sends as an index into {@link CardColor}. An index outside
     * it gives {@code null}, so a colour added to the site later shows as neutral rather than wrong.
     */
    private static @Nullable Integer colorOf(@Nullable JsonElement color) {
        if (!(color instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return null;
        }

        CardColor known = CardColor.byIndex(primitive.getAsInt());
        return known == null ? null : known.argb();
    }

    /**
     * Reads the cards of the player called {@code username}, which is what a transfer by nickname
     * has to pick a receiver from.
     *
     * <p>{@code card} only supplies the credentials; the answer is about the other player.
     *
     * @throws IllegalArgumentException if the name cannot be part of a URL, which the API answers
     *         to with a bare 400 rather than anything explanatory
     */
    public static CompletableFuture<List<PlayerCard>> playerCards(Card card, String username) {
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("Not a Minecraft username: " + username);
        }

        return requestArray(card, "accounts/" + username + "/cards", "GET", null).thenApply(array -> {
            List<PlayerCard> cards = new ArrayList<>();

            for (JsonElement element : array) {
                JsonObject playerCard = element.getAsJsonObject();

                if (playerCard.has("number")) {
                    String number = playerCard.get("number").getAsString();
                    String name = playerCard.has("name") ? playerCard.get("name").getAsString() : number;
                    cards.add(new PlayerCard(name, number));
                }
            }

            return cards;
        });
    }

    private static int balanceOf(JsonObject json) {
        if (!json.has("balance")) {
            throw new ApiException("Response contained no balance");
        }

        return json.get("balance").getAsInt();
    }

    /**
     * A response exactly as the server sent it.
     *
     * @param status the HTTP status code
     * @param body the response body, unparsed
     */
    public record RawResponse(int status, String body) {}

    /**
     * Sends one request and hands back the status and body verbatim.
     *
     * <p>Unlike {@link #request}, this neither parses the answer nor honours {@link DebugData}: it
     * always talks to the real server. It exists for {@code /spwapi}, whose whole job is to show
     * what the API actually replies with.
     */
    public static CompletableFuture<RawResponse> raw(Card card, String endpoint, String method, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", card.authorization())
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        throw new ApiException(throwable.getMessage(), throwable);
                    }

                    return new RawResponse(response.statusCode(), response.body());
                });
    }

    private static CompletableFuture<JsonObject> request(Card card, String endpoint, String method, String body) {
        return requestJson(card, endpoint, method, body).thenApply(JsonElement::getAsJsonObject);
    }

    /** Same as {@link #request}, for the endpoints that answer with a bare JSON array. */
    private static CompletableFuture<JsonArray> requestArray(Card card, String endpoint, String method, String body) {
        return requestJson(card, endpoint, method, body).thenApply(JsonElement::getAsJsonArray);
    }

    private static CompletableFuture<JsonElement> requestJson(Card card, String endpoint, String method, String body) {
        if (DebugData.enabled()) {
            return CompletableFuture.completedFuture(stubResponse(endpoint));
        }

        return raw(card, endpoint, method, body).thenApply(SPWorldsApi::parse);
    }

    private static JsonElement parse(RawResponse response) {
        JsonElement json;

        try {
            json = GSON.fromJson(response.body(), JsonElement.class);
        } catch (JsonSyntaxException e) {
            throw new ApiException("Malformed response (HTTP " + response.status() + ")", e);
        }

        if (response.status() / 100 != 2) {
            // Errors come back as {"statusCode": .., "error": .., "message": ..}; if even that is
            // missing there is nothing better to show than the status code.
            String message = json != null && json.isJsonObject() && json.getAsJsonObject().has("message")
                    ? json.getAsJsonObject().get("message").getAsString()
                    : "HTTP " + response.status();

            throw new ApiException(message);
        }

        if (json == null || json.isJsonNull()) {
            throw new ApiException("Empty response");
        }

        return json;
    }

    /**
     * Canned answers for {@link DebugData}, so the wallet can be worked on without an account and
     * without sending anything to the live API.
     */
    private static JsonElement stubResponse(String endpoint) {
        if (endpoint.equals("card") || endpoint.equals("transactions")) {
            JsonObject json = new JsonObject();
            json.addProperty("balance", DebugData.BALANCE);
            return json;
        }

        if (endpoint.equals("accounts/me")) {
            JsonArray cards = new JsonArray();

            DebugData.numbers().forEach((id, number) -> {
                JsonObject accountCard = new JsonObject();
                accountCard.addProperty("id", id);
                accountCard.addProperty("number", number);
                accountCard.addProperty("color", DebugData.color(id).ordinal());
                cards.add(accountCard);
            });

            JsonObject json = new JsonObject();
            json.add("cards", cards);
            return json;
        }

        if (endpoint.startsWith("accounts/") && endpoint.endsWith("/cards")) {
            JsonArray cards = new JsonArray();

            for (PlayerCard card : DebugData.playerCards()) {
                JsonObject playerCard = new JsonObject();
                playerCard.addProperty("name", card.name());
                playerCard.addProperty("number", card.number());
                cards.add(playerCard);
            }

            return cards;
        }

        throw new ApiException("No debug response for endpoint '" + endpoint + "'");
    }
}
