package ru.foxanto.spwallet.gametest;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.UIComponent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.connect.ConnectDto;
import ru.foxanto.spwallet.gui.overlay.CallHud;
import ru.foxanto.spwallet.gui.WalletScreen;
import ru.foxanto.spwallet.gui.component.EssentialButton;
import ru.foxanto.spwallet.gui.component.TransparentButton;
import ru.foxanto.spwallet.util.SPServer;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * The «Call» side tab of the wallet (SPConnect by SPWallet) with the debug organisations: the list,
 * a call sent, the countdown it leaves, and the phone that opens the tab while the column is hidden.
 * Shot in Russian too, where the three tab names are longest.
 */
public class ConnectGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> SPWalletConfig.get().sidePanelCollapsed = false);

            openOnCallTab(context);
            context.takeScreenshot("spwallet_connect_tab");

            clickButton(context, "Call");
            expect(context.computeOnClient(client -> find(client, component -> component instanceof LabelComponent label
                    && label.text().getString().startsWith("Call sent to «Гиды»")) != null),
                    "a sent call should say so");
            context.takeScreenshot("spwallet_connect_sent");

            // Finishing the call the bankers took: the stars come first, Send rates and finishes it,
            // and the finished call leaves the list.
            clickButton(context, "Finish");
            context.takeScreenshot("spwallet_connect_rating");
            clickButton(context, "Send");
            context.waitTicks(5);
            expect(!Ui.exists(context, Ui.text("Finish")) && !Ui.exists(context, Ui.text("taken by Steve")),
                    "the finished call should leave My calls");
            context.takeScreenshot("spwallet_connect_rated");
            context.setScreen(() -> null);

            // Calls on the HUD: a new one for staff, taken with the key.
            ConnectDto.CallInfo call = new ConnectDto.CallInfo("hud-call", new ConnectDto.OrgRef("debug-guides", "Гиды", "#3BA55C"),
                    new ConnectDto.Player("00000000000000000000000000000001", "Steve"), "SP", "minecraft:overworld",
                    120, 64, -30, "Помогите с домом", "OPEN", null, null);
            context.runOnClient(client -> CallHud.incoming(call));
            context.waitTicks(10);
            context.takeScreenshot("spwallet_call_hud_incoming");
            // With a screen open the card has a button for the mouse.
            context.setScreen(() -> new net.minecraft.client.gui.screens.ChatScreen("", false));
            context.waitTicks(5);
            context.takeScreenshot("spwallet_call_hud_mouse");
            context.setScreen(() -> null);
            context.getInput().pressKey(GLFW.GLFW_KEY_Y);
            context.waitTicks(5);
            expect(context.computeOnClient(client -> CallHud.shown()) == 0, "the key should take the call");

            // The caller's side: taken, then finished and asked to rate.
            ConnectDto.CallInfo mine = new ConnectDto.CallInfo("my-call", new ConnectDto.OrgRef("debug-bank", "Банкиры", "#FAA61A"),
                    new ConnectDto.Player("00000000000000000000000000000002", "Player0"), "SP", "minecraft:overworld",
                    10, 70, 10, "", "ACCEPTED", "Alex", null);
            context.runOnClient(client -> CallHud.accepted(mine));
            context.waitTicks(10);
            context.takeScreenshot("spwallet_call_hud_accepted");
            context.runOnClient(client -> CallHud.finished(mine));
            context.waitTicks(10);
            context.takeScreenshot("spwallet_call_hud_finished");
            context.getInput().pressKey(GLFW.GLFW_KEY_Y);
            context.waitTicks(10);
            expect(Ui.exists(context, Ui.text("Rate the help")), "the key should open the stars for the finished call");
            context.takeScreenshot("spwallet_connect_rate_finished");
            Ui.click(context, Ui.text("Skip"), "Skip");
            expect(!Ui.exists(context, Ui.text("Rate the help")), "Skip should put the stars away");
            context.setScreen(() -> null);

            // Hidden column: the phone in the transfer header opens it again on the call tab.
            context.runOnClient(client -> SPWalletConfig.get().sidePanelCollapsed = true);
            context.setScreen(() -> new WalletScreen(SPServer.SP));
            context.waitTicks(10);
            double[] phone = context.computeOnClient(client -> centre(client, component ->
                    component instanceof TransparentButton button && "📞".equals(button.text().getString())));
            expect(phone != null, "the hidden column should leave a phone in the header");
            context.takeScreenshot("spwallet_connect_phone");
            context.getInput().setCursorPos(phone[0], phone[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(10);
            expect(!context.computeOnClient(client -> SPWalletConfig.get().sidePanelCollapsed),
                    "the phone should show the side column");
            expect(context.computeOnClient(client -> find(client, component -> component instanceof LabelComponent label
                    && label.text().getString().equals("SPConnect")) != null),
                    "the phone should open the call tab");
            context.setScreen(() -> null);

            setLanguage(context, "ru_ru");
            openOnCallTab(context);
            context.takeScreenshot("spwallet_connect_tab_ru");
            context.setScreen(() -> null);
            setLanguage(context, "en_us");
        }
    }

    /** Scrolls the first active button saying {@code text} into view and clicks it. */
    private static void clickButton(ClientGameTestContext context, String text) {
        Predicate<UIComponent> wanted = component -> component instanceof EssentialButton button && button.active()
                && button.getMessage().getString().equals(text);
        context.runOnClient(client -> {
            UIComponent target = find(client, wanted);
            for (UIComponent parent = target == null ? null : target.parent(); parent != null; parent = parent.parent()) {
                if (parent instanceof ScrollContainer<?> scroll) {
                    scroll.scrollTo(target);
                    break;
                }
            }
        });
        context.waitTicks(10);
        double[] at = context.computeOnClient(client -> centre(client, wanted));
        expect(at != null, "there should be an active " + text + " button");
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(10);
    }

    private static void openOnCallTab(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
                Field last = WalletScreen.class.getDeclaredField("lastSideTab");
                last.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object tab = Enum.valueOf((Class) last.getType(), "CALL");
                last.set(null, tab);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
        context.setScreen(() -> new WalletScreen(SPServer.SP));
        context.waitTicks(10);
    }

    private static void setLanguage(ClientGameTestContext context, String code) {
        CompletableFuture<Void> reload = context.computeOnClient(client -> {
            client.getLanguageManager().setSelected(code);
            client.options.languageCode = code;
            return client.reloadResourcePacks();
        });
        context.waitFor(client -> reload.isDone() && client.getOverlay() == null, 1200);
        context.waitTicks(5);
    }

    private static UIComponent find(Minecraft client, Predicate<UIComponent> wanted) {
        if (!(client.screen instanceof BaseOwoScreen<?> screen)) {
            return null;
        }

        try {
            Field field = BaseOwoScreen.class.getDeclaredField("uiAdapter");
            field.setAccessible(true);
            OwoUIAdapter<?> adapter = (OwoUIAdapter<?>) field.get(screen);
            AtomicReference<UIComponent> found = new AtomicReference<>();
            adapter.rootComponent.forEachDescendant(component -> {
                if (found.get() == null && wanted.test(component)) {
                    found.set(component);
                }
            });
            return found.get();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    /** The middle of the first matching component, in window pixels. */
    private static double[] centre(Minecraft client, Predicate<UIComponent> wanted) {
        UIComponent component = find(client, wanted);

        if (component == null) {
            return null;
        }

        double scale = client.getWindow().getGuiScale();
        return new double[] {
                (component.x() + component.width() / 2.0) * scale,
                (component.y() + component.height() / 2.0) * scale
        };
    }

    private static void expect(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }
}
