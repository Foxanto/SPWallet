package ru.foxanto.spwallet.connect;

import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.util.DebugData;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * «SPConnect от SPWallet»: вызов сотрудников организаций СП/СПм. Раньше это был отдельный мод
 * SPConnect, теперь он встроен в кошелёк и общается с тем же сайтом.
 *
 * <p>Если у игрока остался старый мод SPConnect, кошелёк не подключается к реалтайму и не
 * регистрирует свои команды — иначе каждый вызов приходил бы дважды, — а один раз просит удалить
 * старый мод. Вкладка «Вызов» при этом работает.
 */
public final class SPConnect {
    private static final List<ConnectDto.OrgInfo> DEBUG_ORGS = List.of(
            new ConnectDto.OrgInfo("debug-guides", "Гиды", "Покажут город и помогут с первым домом", "#3BA55C", 30, 0),
            new ConnectDto.OrgInfo("debug-fbi", "ФБР", "Грифер, вор или мошенник рядом", "#ED4245", 30, 754),
            new ConnectDto.OrgInfo("debug-bank", "Банкиры", "Обмен АР, кредиты и открытие карт", "#FAA61A", 15, 0));

    /** В режиме отладки: свои вызовы, которые можно оценить и отменить, не трогая сайт. */
    private static final List<ConnectDto.MyCall> DEBUG_CALLS = new java.util.ArrayList<>(List.of(
            new ConnectDto.MyCall("debug-call-1", new ConnectDto.OrgRef("debug-bank", "Банкиры", "#FAA61A"),
                    "ACCEPTED", "Steve", null, null),
            new ConnectDto.MyCall("debug-call-2", new ConnectDto.OrgRef("debug-guides", "Гиды", "#3BA55C"),
                    "OPEN", null, null, null)));

    private static final ConnectDto.ReviewsAbout DEBUG_REVIEWS = new ConnectDto.ReviewsAbout(
            new ConnectDto.Summary(4.5, 2),
            List.of(new ConnectDto.OrgSummary(new ConnectDto.OrgRef("debug-guides", "Гиды", "#3BA55C"), 4.5, 2)),
            List.of(new ConnectDto.ReviewInfo("debug-review-1", new ConnectDto.OrgRef("debug-guides", "Гиды", "#3BA55C"),
                            "Alex", 5, "Быстро пришёл и всё объяснил", null),
                    new ConnectDto.ReviewInfo("debug-review-2", new ConnectDto.OrgRef("debug-guides", "Гиды", "#3BA55C"),
                            "Steve", 4, "", null)));

    /** Растёт с каждым событием реалтайма, чтобы открытая вкладка знала, что пора обновиться. */
    private static volatile int version;

    /** Вызов, который игрок сейчас оценивает: открывается звёздами наверху вкладки «Вызов». */
    private static volatile ConnectDto.@org.jetbrains.annotations.Nullable CallInfo pendingRating;
    /** Вызовы, которые игрок завершил сам: о них не нужна карточка «оцените». */
    private static final java.util.Set<String> CLOSED_BY_ME = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final boolean LEGACY = FabricLoader.getInstance().isModLoaded("spconnect");

    private static ConnectRealtime realtime;
    private static boolean legacyWarned;

    private SPConnect() {}

    public static void init() {
        ConnectServers.refreshConfig();

        if (LEGACY) {
            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(SPConnect::warnLegacy));
            return;
        }

        realtime = new ConnectRealtime(event -> {
            CallNotifier.handle(event, myUuid());
            changed();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> onJoin(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ConnectServers.clear();
            realtime.setServer(null);
        });
    }

    /** Старый отдельный мод SPConnect тоже установлен. */
    public static boolean legacyInstalled() {
        return LEGACY;
    }

    /**
     * Сервер SPWorlds, на котором сейчас работает вызов, или {@code null}. В режиме отладки — СП на
     * любом сервере, как и кошелёк.
     */
    public static @Nullable ConnectDto.ServerInfo server() {
        ConnectDto.ServerInfo server = ConnectServers.current();

        if (server == null && DebugData.enabled()) {
            return new ConnectDto.ServerInfo("SP", "СП", List.of());
        }

        return server;
    }

    /** Организации, которые можно вызвать на {@code server}. В режиме отладки сайт не трогается. */
    public static CompletableFuture<List<ConnectDto.OrgInfo>> orgs(ConnectDto.ServerInfo server) {
        if (DebugData.enabled()) {
            return CompletableFuture.completedFuture(DEBUG_ORGS);
        }

        return ConnectApi.get().get("/api/mod/orgs?server=" + server.id(),
                new TypeToken<List<ConnectDto.OrgInfo>>() {}.getType());
    }

    /** Вызывает организацию туда, где игрок стоит сейчас. */
    public static CompletableFuture<ConnectDto.CreateCallResult> call(ConnectDto.ServerInfo server,
                                                                      ConnectDto.OrgInfo org, String message) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return CompletableFuture.failedFuture(new ConnectException(0, "Нет игрока", "no_player", 0));
        }

        if (DebugData.enabled()) {
            return CompletableFuture.completedFuture(new ConnectDto.CreateCallResult(null, 2));
        }

        BlockPos pos = player.blockPosition();
        String dimension = player.level().dimension().identifier().toString();
        var request = new ConnectDto.CreateCallRequest(org.id(), server.id(), dimension,
                pos.getX(), pos.getY(), pos.getZ(), message);
        return ConnectApi.get().post("/api/mod/calls", request, ConnectDto.CreateCallResult.class);
    }

    /** Свои вызовы игрока за сутки, новые сверху. */
    public static CompletableFuture<List<ConnectDto.MyCall>> myCalls(ConnectDto.ServerInfo server) {
        if (DebugData.enabled()) {
            return CompletableFuture.completedFuture(List.copyOf(DEBUG_CALLS));
        }

        return ConnectApi.get().get("/api/mod/calls/mine?server=" + server.id(),
                new TypeToken<List<ConnectDto.MyCall>>() {}.getType());
    }

    /** Оценивает свой вызов: 1–5 звёзд и необязательный текст. */
    public static CompletableFuture<Void> review(String callId, int rating, String text) {
        if (DebugData.enabled()) {
            DEBUG_CALLS.replaceAll(call -> call.id().equals(callId)
                    ? new ConnectDto.MyCall(call.id(), call.org(), call.status(), call.acceptedBy(), call.createdAt(),
                            new ConnectDto.MyReview(rating, text))
                    : call);
            return CompletableFuture.completedFuture(null);
        }

        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("rating", rating);
        body.addProperty("text", text);
        return ConnectApi.get().<Object>post("/api/mod/calls/" + callId + "/review", body, Object.class)
                .thenApply(ignored -> null);
    }

    /** Отменяет свой открытый вызов или завершает принятый. */
    public static CompletableFuture<Void> close(String callId) {
        if (DebugData.enabled()) {
            DEBUG_CALLS.replaceAll(call -> call.id().equals(callId)
                    ? new ConnectDto.MyCall(call.id(), call.org(), "OPEN".equals(call.status()) ? "CANCELLED" : "CLOSED",
                            call.acceptedBy(), call.createdAt(), call.review())
                    : call);
            return CompletableFuture.completedFuture(null);
        }

        return ConnectApi.get().<Object>post("/api/mod/calls/" + callId + "/close", null, Object.class)
                .thenApply(ignored -> null);
    }

    /** Принимает вызов, как кнопка [Принять]: из карточки на HUD или по клавише. */
    public static void accept(String callId) {
        if (DebugData.enabled()) {
            CallNotifier.chat(CallNotifier.prefix().append(Component.literal("Вы приняли вызов (отладка)")
                    .withStyle(ChatFormatting.GREEN)));
            return;
        }

        ConnectApi.get().<ConnectDto.CallInfo>post("/api/mod/calls/" + callId + "/accept", null, ConnectDto.CallInfo.class)
                .whenComplete((call, err) -> reply(err, () -> "Вы приняли вызов от " + call.caller().name()
                        + ": " + CallNotifier.coords(call)));
    }

    /** Открывает звёзды для вызова, который завершил сотрудник. */
    public static void rate(ConnectDto.CallInfo call) {
        offerRating(call);
        Minecraft.getInstance().execute(ru.foxanto.spwallet.gui.WalletScreen::openCallTab);
    }

    /** Вызов завершил сотрудник: звёзды ждут игрока наверху вкладки «Вызов». */
    public static void offerRating(ConnectDto.CallInfo call) {
        pendingRating = call;
        changed();
    }

    public static ConnectDto.@org.jetbrains.annotations.Nullable CallInfo pendingRating() {
        return pendingRating;
    }

    /** Оценка поставлена или пропущена. */
    public static void ratingDone() {
        pendingRating = null;
        changed();
    }

    /** Игрок завершает вызов сам: карточка «оцените» по нему не придёт, оценку он ставит тут же. */
    public static void closedByMe(String callId) {
        CLOSED_BY_ME.add(callId);
    }

    static boolean wasClosedByMe(String callId) {
        return CLOSED_BY_ME.remove(callId);
    }

    /** Отзывы о самом игроке, если он сотрудник; пусто, если нет. */
    public static CompletableFuture<ConnectDto.ReviewsAbout> reviewsAboutMe() {
        if (DebugData.enabled()) {
            return CompletableFuture.completedFuture(DEBUG_REVIEWS);
        }

        return ConnectApi.get().get("/api/mod/reviews/me", ConnectDto.ReviewsAbout.class);
    }

    /** Растёт с каждым событием реалтайма; вкладка сравнивает его, чтобы знать, когда обновиться. */
    public static int version() {
        return version;
    }

    static void changed() {
        version++;
    }

    public static boolean connected() {
        return realtime != null && realtime.connected();
    }

    private static void onJoin(Minecraft client) {
        if (!SPWalletConfig.get().connectEnabled) {
            return;
        }

        ConnectDto.ServerInfo server = ConnectServers.detect(client);
        realtime.setServer(server == null ? null : server.id());
        if (server == null) return;

        // Вызовы, пришедшие, пока игрок был не в сети
        ConnectApi.get().<List<ConnectDto.CallInfo>>get("/api/mod/calls/active?server=" + server.id(),
                        new TypeToken<List<ConnectDto.CallInfo>>() {}.getType())
                .thenAccept(calls -> client.execute(() -> calls.stream()
                        .filter(c -> !c.caller().uuid().equals(myUuid()))
                        .forEach(CallNotifier::newCall)));
    }

    private static void warnLegacy() {
        if (legacyWarned) {
            return;
        }

        legacyWarned = true;
        CallNotifier.chat(CallNotifier.prefix().append(Component.translatable("chat.spwallet.connect.legacy")
                .withStyle(ChatFormatting.YELLOW)));
    }

    /** {@code /spconnect}: кнопки [Принять] и [Отменить] в чате и проверка связи. */
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (LEGACY) {
            return;
        }

        dispatcher.register(literal("spconnect")
                .then(literal("accept").then(argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    ConnectApi.get().<ConnectDto.CallInfo>post("/api/mod/calls/" + id + "/accept", null, ConnectDto.CallInfo.class)
                            .whenComplete((call, err) -> reply(err, () -> "Вы приняли вызов от " + call.caller().name()
                                    + ": " + CallNotifier.coords(call)));
                    return 1;
                })))
                .then(literal("close").then(argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    ConnectApi.get().<ConnectDto.CallInfo>post("/api/mod/calls/" + id + "/close", null, ConnectDto.CallInfo.class)
                            .whenComplete((call, err) -> reply(err, () -> "Вызов закрыт"));
                    return 1;
                })))
                .then(literal("rate").executes(ctx -> {
                    // Из кнопки [Оценить] в чате. Команда выполняется, пока чат ещё открыт и
                    // закроется следом, поэтому кошелёк открывается на тик позже.
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().execute(
                            ru.foxanto.spwallet.gui.WalletScreen::openCallTab));
                    return 1;
                }))
                .then(literal("status").executes(ctx -> {
                    ConnectDto.ServerInfo server = ConnectServers.current();
                    ConnectApi.get().<ConnectDto.Me>get("/api/mod/me", ConnectDto.Me.class).whenComplete((me, err) -> reply(err, () ->
                            "Сервер: " + (server == null ? "не СП" : server.name())
                                    + ", связь: " + (connected() ? "есть" : "нет")
                                    + ", сотрудник: " + (me.employeeOf().isEmpty() ? "нет"
                                    : String.join(", ", me.employeeOf().stream().map(ConnectDto.OrgRef::name).toList()))));
                    return 1;
                })));
    }

    private static void reply(Throwable err, Supplier<String> ok) {
        Minecraft.getInstance().execute(() -> CallNotifier.chat(err != null
                ? CallNotifier.prefix().append(Component.literal(ConnectApi.errorMessage(err)).withStyle(ChatFormatting.RED))
                : CallNotifier.prefix().append(Component.literal(ok.get()).withStyle(ChatFormatting.GREEN))));
    }

    private static String myUuid() {
        return Minecraft.getInstance().getUser().getProfileId().toString().replace("-", "");
    }
}
