package ru.foxanto.spwallet.update;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.gui.EssentialColors;

/**
 * Предупреждение об обновлении: плашка в главном меню и в списке серверов, пока новая версия
 * скачана и ждёт выхода из игры, с кнопками «Отменить» и «Что нового», и одно всплывающее
 * уведомление, когда она скачалась.
 */
public final class UpdateNotice {
    private static final int X = 6;
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(10_000L);
    private static boolean toastShown;

    private UpdateNotice() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!toastShown && Updater.state() == Updater.State.READY) {
                toastShown = true;
                SystemToast.addOrUpdate(client.getToastManager(), TOAST,
                        Component.translatable("toast.spwallet.update.title", Updater.newVersion()),
                        Component.translatable("toast.spwallet.update.description"));
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof JoinMultiplayerScreen)) return;

            // В главном меню сверху логотип — плашка внизу слева, над строкой версии; в списке серверов — сверху
            int y = screen instanceof TitleScreen ? height - 74 : 6;
            Button toggle = Button.builder(Component.translatable("gui.spwallet.update.cancel"), button -> {
                if (Updater.state() == Updater.State.READY) Updater.cancel();
                else Updater.restore();
            }).bounds(X, y + 28, 72, 18).build();
            Button notes = Button.builder(Component.translatable("gui.spwallet.update.notes"), button -> {
                String url = Updater.releaseUrl();
                if (url != null && !url.isEmpty()) ConfirmLinkScreen.confirmLinkNow(screen, url);
            }).bounds(X + 76, y + 28, 90, 18).build();
            toggle.visible = notes.visible = false;
            Screens.getButtons(screen).add(toggle);
            Screens.getButtons(screen).add(notes);

            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) ->
                    render(graphics, y, toggle, notes, mouseX, mouseY));
        });
    }

    private static void render(GuiGraphics g, int y, Button toggle, Button notes, int mouseX, int mouseY) {
        Updater.State state = Updater.state();
        boolean shown = state == Updater.State.READY || state == Updater.State.CANCELLED;
        toggle.visible = notes.visible = shown;
        if (!shown) return;

        boolean ready = state == Updater.State.READY;
        toggle.setMessage(Component.translatable(ready ? "gui.spwallet.update.cancel" : "gui.spwallet.update.restore"));
        notes.visible = Updater.releaseUrl() != null && !Updater.releaseUrl().isEmpty();

        Font font = Minecraft.getInstance().font;
        Component title = Component.translatable(ready ? "gui.spwallet.update.ready" : "gui.spwallet.update.cancelled",
                Updater.newVersion());
        Component sub = Component.translatable(ready ? "gui.spwallet.update.ready_sub" : "gui.spwallet.update.cancelled_sub");
        int w = Math.max(Math.max(font.width(title), font.width(sub)) + 10, 170);
        int h = 48;

        g.fill(X - 3, y - 3, X + w, y + h, 0xE0181818);
        g.fill(X - 3, y - 3, X - 1, y + h, ready ? EssentialColors.success() : EssentialColors.warning());
        g.drawString(font, title, X + 3, y, 0xFFE2E2E2, true);
        g.drawString(font, sub, X + 3, y + 12, 0xFF9A9A9A, false);

        // Что нового — подсказкой по наведению на плашку
        String text = Updater.notes();
        if (!text.isEmpty() && mouseX >= X - 3 && mouseX < X + w && mouseY >= y - 3 && mouseY < y + 24) {
            g.setComponentTooltipForNextFrame(font, text.lines().map(line -> (Component) Component.literal(line)).toList(),
                    mouseX, mouseY);
        }
    }
}
