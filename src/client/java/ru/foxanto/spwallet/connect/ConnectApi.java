package ru.foxanto.spwallet.connect;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.foxanto.spwallet.config.SPWalletConfig;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Клиент API сайта SPConnect.
 *
 * <p>Авторизация: сайт выдаёт serverId, мод сообщает Mojang о «входе на сервер» (как при обычном
 * входе на сервер), сайт проверяет это у Mojang и выдаёт свой токен. Токен доступа Minecraft и
 * токены карт SPWallet на сайт не отправляются.
 */
public final class ConnectApi {
    private static final Logger LOG = LoggerFactory.getLogger("SPWallet/SPConnect");
    public static final Gson GSON = new Gson();
    private static final ConnectApi INSTANCE = new ConnectApi();
    private static final String USER_AGENT = "SPWallet/" + FabricLoader.getInstance().getModContainer("spwallet")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev") + " (SPConnect)";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SPWallet-SPConnect");
        t.setDaemon(true);
        return t;
    });

    private volatile String token;
    private volatile String tokenOwner;
    private volatile long tokenExpiresAt;
    private CompletableFuture<String> pendingAuth;

    private ConnectApi() {}

    public static ConnectApi get() {
        return INSTANCE;
    }

    HttpClient http() {
        return http;
    }

    /** Адрес сайта без завершающего слэша. */
    static String base() {
        String url = SPWalletConfig.get().connectApiUrl;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ---------- Авторизация ----------

    /** Действующий токен; при необходимости проходит авторизацию через Mojang. */
    public synchronized CompletableFuture<String> token() {
        User user = Minecraft.getInstance().getUser();
        String owner = user.getProfileId().toString();
        if (token != null && owner.equals(tokenOwner) && System.currentTimeMillis() < tokenExpiresAt - 60_000) {
            return CompletableFuture.completedFuture(token);
        }
        if (pendingAuth == null || pendingAuth.isDone()) {
            pendingAuth = CompletableFuture.supplyAsync(() -> authenticate(user), executor);
        }
        return pendingAuth;
    }

    public synchronized void invalidateToken() {
        token = null;
    }

    private String authenticate(User user) {
        ConnectDto.AuthChallenge challenge = send("POST", "/api/mod/auth/challenge", new JsonObject(), null,
                ConnectDto.AuthChallenge.class);
        try {
            Minecraft.getInstance().services().sessionService()
                    .joinServer(user.getProfileId(), user.getAccessToken(), challenge.serverId());
        } catch (Exception e) {
            throw new ConnectException(401, "Mojang не подтвердил вход: " + e.getMessage(), "mojang", 0);
        }
        JsonObject body = new JsonObject();
        body.addProperty("username", user.getName());
        body.addProperty("serverId", challenge.serverId());
        ConnectDto.AuthResult result = send("POST", "/api/mod/auth/verify", body, null, ConnectDto.AuthResult.class);
        synchronized (this) {
            token = result.token();
            tokenOwner = user.getProfileId().toString();
            tokenExpiresAt = System.currentTimeMillis() + result.expiresIn() * 1000L;
        }
        LOG.info("Вход в SPConnect выполнен как {}", result.name());
        return result.token();
    }

    // ---------- Запросы ----------

    public <T> CompletableFuture<T> get(String path, Type type) {
        return authorized("GET", path, null, type);
    }

    public <T> CompletableFuture<T> post(String path, Object body, Type type) {
        return authorized("POST", path, body == null ? new JsonObject() : body, type);
    }

    public CompletableFuture<ConnectDto.ModConfigResponse> config() {
        return CompletableFuture.supplyAsync(() -> send("GET", "/api/mod/config", null, null,
                ConnectDto.ModConfigResponse.class), executor);
    }

    private <T> CompletableFuture<T> authorized(String method, String path, Object body, Type type) {
        return token()
                .thenApplyAsync(t -> this.<T>send(method, path, body, t, type), executor)
                .exceptionallyCompose(err -> {
                    // Токен истёк или сайт перезапущен с новым ключом — авторизуемся заново один раз
                    if (unwrap(err) instanceof ConnectException api && api.status == 401 && !"mojang".equals(api.code)) {
                        invalidateToken();
                        return token().thenApplyAsync(t -> this.<T>send(method, path, body, t, type), executor);
                    }
                    return CompletableFuture.failedFuture(err);
                });
    }

    private <T> T send(String method, String path, Object body, String bearer, Type type) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
        if (bearer != null) req.header("Authorization", "Bearer " + bearer);
        if (body != null) {
            req.header("Content-Type", "application/json");
            req.method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        } else {
            req.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> res;
        try {
            res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ConnectException(0, "Сайт SPConnect недоступен", "network", 0);
        }
        if (res.statusCode() >= 400) {
            String message = "Ошибка " + res.statusCode();
            String code = null;
            int retryAfter = 0;
            try {
                JsonObject err = JsonParser.parseString(res.body()).getAsJsonObject();
                if (err.has("error")) message = err.get("error").getAsString();
                if (err.has("code")) code = err.get("code").getAsString();
                if (err.has("retryAfter")) retryAfter = err.get("retryAfter").getAsInt();
            } catch (Exception ignored) {
            }
            throw new ConnectException(res.statusCode(), message, code, retryAfter);
        }
        return type == Void.class ? null : GSON.fromJson(res.body(), type);
    }

    public static Throwable unwrap(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
        return t;
    }

    public static String errorMessage(Throwable t) {
        Throwable e = unwrap(t);
        return e instanceof ConnectException ? e.getMessage() : "Ошибка: " + e;
    }
}
