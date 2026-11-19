package ru.foxanto.spwallet.gametest;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.UIComponent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Finds owo components on the open screen and clicks them where they are, so the tests do not
 * depend on where the layout happens to put things.
 */
final class Ui {
    private Ui() {}

    /** A label, a button or a transparent button showing exactly {@code text}. */
    static Predicate<UIComponent> text(String text) {
        return component -> (component instanceof LabelComponent label && label.text().getString().equals(text))
                || (component instanceof ButtonComponent button && button.getMessage().getString().equals(text));
    }

    static UIComponent find(Minecraft client, Predicate<UIComponent> wanted) {
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

    static boolean exists(ClientGameTestContext context, Predicate<UIComponent> wanted) {
        return context.computeOnClient(client -> find(client, wanted) != null);
    }

    /** Scrolls the first matching component into view and clicks its middle. */
    static void click(ClientGameTestContext context, Predicate<UIComponent> wanted, String what) {
        context.runOnClient(client -> {
            UIComponent target = find(client, wanted);
            for (UIComponent parent = target == null ? null : target.parent(); parent != null; parent = parent.parent()) {
                if (parent instanceof ScrollContainer<?> scroll) {
                    scroll.scrollTo(target);
                    break;
                }
            }
        });
        context.waitTicks(5);
        double[] at = context.computeOnClient(client -> {
            UIComponent component = find(client, wanted);
            if (component == null) return null;
            double scale = client.getWindow().getGuiScale();
            return new double[] {(component.x() + component.width() / 2.0) * scale,
                    (component.y() + component.height() / 2.0) * scale};
        });
        if (at == null) throw new AssertionError("there should be " + what + " on the screen");
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
    }
}
