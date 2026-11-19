package ru.foxanto.spwallet.connect;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Постоянное соединение с сайтом, пока игрок находится на сервере СП/СПм.
 * Через него сайт присылает вызовы сотрудникам и обновления статуса вызова.
 */
public final class ConnectRealtime {
    private static final Logger LOG = LoggerFactory.getLogger("SPWallet/SPConnect");

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SPWallet-SPConnect-Realtime");
        t.setDaemon(true);
        return t;
    });
    private final Consumer<ConnectDto.Event> onEvent;

    private volatile WebSocket socket;
    private volatile String server;
    private volatile boolean wanted;
    private volatile int attempt;

    ConnectRealtime(Consumer<ConnectDto.Event> onEvent) {
        this.onEvent = onEvent;
        scheduler.scheduleAtFixedRate(this::ping, 30, 30, TimeUnit.SECONDS);
    }

    /** Игрок зашёл на сервер СП/СПм (id = SP | SPM) или вышел (null). */
    public synchronized void setServer(String serverId) {
        this.server = serverId;
        this.wanted = serverId != null;
        if (wanted) {
            if (socket == null) connect();
            else sendPresence();
        } else if (socket != null) {
            sendPresence();
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "left");
            socket = null;
        }
    }

    private void connect() {
        ConnectApi.get().token().thenCompose(token -> {
            String base = ConnectApi.base().replaceFirst("^http", "ws");
            URI uri = URI.create(base + "/api/mod/ws?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
            return ConnectApi.get().http().newWebSocketBuilder().buildAsync(uri, new Listener());
        }).whenComplete((ws, err) -> {
            if (err != null) {
                LOG.warn("не удалось подключиться: {}", ConnectApi.errorMessage(err));
                scheduleReconnect();
                return;
            }
            synchronized (this) {
                if (!wanted) {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "left");
                    return;
                }
                socket = ws;
                attempt = 0;
                sendPresence();
            }
        });
    }

    private void scheduleReconnect() {
        if (!wanted) return;
        long delay = Math.min(60, 5L << Math.min(attempt++, 4));
        scheduler.schedule(() -> {
            synchronized (this) {
                if (wanted && socket == null) connect();
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void sendPresence() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "presence");
        msg.addProperty("server", server);
        send(msg);
    }

    private void ping() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "ping");
        send(msg);
    }

    private void send(JsonObject msg) {
        WebSocket ws = socket;
        if (ws != null) ws.sendText(msg.toString(), true);
    }

    public boolean connected() {
        return socket != null;
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                try {
                    ConnectDto.Event event = ConnectApi.GSON.fromJson(text, ConnectDto.Event.class);
                    Minecraft.getInstance().execute(() -> onEvent.accept(event));
                } catch (Exception e) {
                    LOG.warn("непонятное сообщение: {}", text);
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            closed(ws, statusCode == 4001);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            LOG.warn("соединение прервано: {}", error.toString());
            closed(ws, false);
        }

        private void closed(WebSocket ws, boolean unauthorized) {
            synchronized (ConnectRealtime.this) {
                if (socket == ws) socket = null;
            }
            if (unauthorized) ConnectApi.get().invalidateToken();
            scheduleReconnect();
        }
    }
}
