package ru.foxanto.spwallet.connect;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import ru.foxanto.spwallet.config.SPWalletConfig;

import java.util.HashSet;
import java.util.Set;

/**
 * Показывает вызовы игроку: уведомление, звук и сообщение в чате с кнопками. В режиме «Не
 * беспокоить» SPWallet остаётся только сообщение в чате.
 */
final class CallNotifier {
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(8000L);
    /** Чтобы один вызов не показывать дважды (реалтайм + список активных при входе) */
    private static final Set<String> shown = new HashSet<>();

    private CallNotifier() {}

    public static void handle(ConnectDto.Event event, String myUuid) {
        if ("review.new".equals(event.type()) && event.review() != null) {
            newReview(event.review());
            return;
        }
        if (event.call() == null) return;
        switch (event.type()) {
            case "call.new" -> newCall(event.call());
            case "call.updated" -> updated(event.call(), myUuid);
            default -> {
            }
        }
    }

    public static void newCall(ConnectDto.CallInfo call) {
        if (!"OPEN".equals(call.status()) || !shown.add(call.id())) return;
        Minecraft mc = Minecraft.getInstance();
        String where = coords(call);

        // Карточка на HUD: её можно принять клавишей или мышкой. В «Не беспокоить» — только чат.
        if (showToasts()) {
            ru.foxanto.spwallet.gui.overlay.CallHud.incoming(call);
        }
        if (SPWalletConfig.get().connectSound && !SPWalletConfig.get().doNotDisturb) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.0f));
        }

        MutableComponent msg = prefix()
                .append(Component.literal(call.caller().name()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" вызывает ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(call.org().name()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" — " + where).withStyle(ChatFormatting.GRAY));
        if (call.message() != null && !call.message().isBlank()) {
            msg.append(Component.literal(" «" + call.message() + "»").withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE));
        }
        msg.append(" ").append(button("[Принять]", ChatFormatting.GREEN, "/spconnect accept " + call.id(), "Принять вызов"));
        chat(msg);
    }

    private static void updated(ConnectDto.CallInfo call, String myUuid) {
        boolean mine = call.caller().uuid().equals(myUuid);
        // Чужой вызов принят или закрыт — карточка с ним сотруднику больше не нужна
        if (!mine) ru.foxanto.spwallet.gui.overlay.CallHud.remove(call.id());
        switch (call.status()) {
            case "ACCEPTED" -> {
                if (mine) {
                    // Вызвавшему — карточка «Вызов принят» в стиле кошелька, даже в «Не беспокоить»:
                    // этого ответа он ждёт.
                    ru.foxanto.spwallet.gui.overlay.CallHud.accepted(call);
                    chat(prefix().append(Component.literal("Ваш вызов принял " + call.acceptedBy() + ". ").withStyle(ChatFormatting.GREEN))
                            .append(button("[Завершить]", ChatFormatting.GRAY, "/spconnect close " + call.id(),
                                    "Сотрудник помог, вызов можно закрыть"))
                            .append(" ")
                    );
                } else {
                    chat(prefix().append(Component.literal("Вызов от " + call.caller().name() + " принял " + call.acceptedBy())
                            .withStyle(ChatFormatting.GRAY)));
                }
            }
            case "CANCELLED" -> {
                if (!mine) chat(prefix().append(Component.literal(call.caller().name() + " отменил вызов").withStyle(ChatFormatting.GRAY)));
                else ru.foxanto.spwallet.gui.overlay.CallHud.remove(call.id());
            }
            case "CLOSED" -> {
                // Завершил сам — оценку он уже ставил; завершил сотрудник — просим оценить
                if (mine && !SPConnect.wasClosedByMe(call.id())) {
                    SPConnect.offerRating(call);
                    ru.foxanto.spwallet.gui.overlay.CallHud.finished(call);
                    chat(prefix().append(Component.literal("Вызов завершён. ").withStyle(ChatFormatting.GRAY))
                            .append(button("[Оценить]", ChatFormatting.GOLD, "/spconnect rate",
                                    "Оценить сотрудника")));
                } else if (mine) {
                    ru.foxanto.spwallet.gui.overlay.CallHud.remove(call.id());
                }
            }
            default -> {
            }
        }
    }

    /** Сотруднику оставили отзыв. */
    private static void newReview(ConnectDto.ReviewInfo review) {
        String org = review.org() == null ? "" : " (" + review.org().name() + ")";
        String author = review.callerName() == null ? "" : " от " + review.callerName();
        MutableComponent msg = prefix()
                .append(Component.literal("Новый отзыв о вас" + author + org + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(stars(review.rating())).withStyle(ChatFormatting.GOLD));
        if (review.text() != null && !review.text().isBlank()) {
            msg.append(Component.literal(" «" + review.text() + "»").withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE));
        }
        chat(msg);
        if (showToasts()) {
            SystemToast.addOrUpdate(Minecraft.getInstance().getToastManager(), TOAST,
                    Component.literal("Новый отзыв " + stars(review.rating())).withStyle(ChatFormatting.GOLD),
                    Component.literal(review.text() == null || review.text().isBlank() ? "Без текста" : review.text()));
        }
    }

    public static String stars(int rating) {
        int filled = Math.max(0, Math.min(5, rating));
        return "★".repeat(filled) + "☆".repeat(5 - filled);
    }

    /** Всплывающие уведомления включены и не мешает режим «Не беспокоить». */
    private static boolean showToasts() {
        return SPWalletConfig.get().connectToasts && !SPWalletConfig.get().doNotDisturb;
    }

    public static String coords(ConnectDto.CallInfo call) {
        String dim = switch (call.dimension()) {
            case "minecraft:overworld" -> "";
            case "minecraft:the_nether" -> " (ад)";
            case "minecraft:the_end" -> " (энд)";
            default -> " (" + call.dimension() + ")";
        };
        return call.x() + " " + call.y() + " " + call.z() + dim;
    }

    public static MutableComponent prefix() {
        return Component.literal("[SPConnect] ").withStyle(ChatFormatting.AQUA);
    }

    public static void chat(Component msg) {
        Minecraft.getInstance().gui.getChat().addMessage(msg);
    }

    private static MutableComponent button(String text, ChatFormatting color, String command, String hover) {
        return Component.literal(text).withStyle(Style.EMPTY
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }
}
