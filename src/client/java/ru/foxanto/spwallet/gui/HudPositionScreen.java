package ru.foxanto.spwallet.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.api.CardColor;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.gui.overlay.BalanceHud;
import ru.foxanto.spwallet.gui.overlay.CardPanel;
import ru.foxanto.spwallet.gui.overlay.IncomingNotifications;
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.CardInfoCache.Row;
import ru.foxanto.spwallet.util.SPServer;

import java.util.List;

/**
 * Lets the player drag the mod's HUD elements - the balance panel and the incoming money
 * notifications - to where they want them. The positions are saved when the screen closes, by Esc
 * or by the done button alike.
 */
public class HudPositionScreen extends Screen {
    private final @Nullable Screen parent;

    /** The element being dragged, or {@code null} for none. */
    private @Nullable Element dragging;
    private double grabX;
    private double grabY;

    public HudPositionScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.spwallet.title.hud_position"));
        this.parent = parent;
    }

    /** One draggable thing on the HUD, and where the config keeps it. */
    private abstract class Element {
        abstract int width();

        abstract int height();

        abstract int x();

        abstract int y();

        abstract void save(float fractionX, float fractionY);

        abstract void render(GuiGraphics graphics, int x, int y);

        boolean over(double mouseX, double mouseY) {
            int x = this.x();
            int y = this.y();
            return mouseX >= x && mouseX < x + this.width() && mouseY >= y && mouseY < y + this.height();
        }
    }

    private final Element balances = new Element() {
        @Override
        int width() {
            return CardPanel.width(HudPositionScreen.this.font, rows(), BalanceHud.STYLE);
        }

        @Override
        int height() {
            return CardPanel.height(rows(), BalanceHud.STYLE);
        }

        @Override
        int x() {
            return BalanceHud.x(this.width(), HudPositionScreen.this.width);
        }

        @Override
        int y() {
            return BalanceHud.y(this.height(), HudPositionScreen.this.height);
        }

        @Override
        void save(float fractionX, float fractionY) {
            SPWalletConfig.get().hudX = fractionX;
            SPWalletConfig.get().hudY = fractionY;
        }

        @Override
        void render(GuiGraphics graphics, int x, int y) {
            CardPanel.render(graphics, HudPositionScreen.this.font, rows(), BalanceHud.STYLE, x, y);
        }
    };

    private final Element notifications = new Element() {
        private final List<IncomingNotifications.Entry> sample = IncomingNotifications.sample();

        @Override
        int width() {
            return IncomingNotifications.width(HudPositionScreen.this.font, this.sample);
        }

        @Override
        int height() {
            return IncomingNotifications.height(this.sample);
        }

        @Override
        int x() {
            return IncomingNotifications.x(this.width(), HudPositionScreen.this.width);
        }

        @Override
        int y() {
            return IncomingNotifications.y(this.height(), HudPositionScreen.this.height);
        }

        @Override
        void save(float fractionX, float fractionY) {
            SPWalletConfig.get().notificationX = fractionX;
            SPWalletConfig.get().notificationY = fractionY;
        }

        @Override
        void render(GuiGraphics graphics, int x, int y) {
            IncomingNotifications.render(graphics, HudPositionScreen.this.font, this.sample, x, y,
                    HudPositionScreen.this.width, System.currentTimeMillis());
        }
    };

    /** The elements to show, the later ones on top and grabbed first. */
    private List<Element> elements() {
        return SPWalletConfig.get().incomingNotifications
                ? List.of(this.balances, this.notifications)
                : List.of(this.balances);
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int y = this.height - 28;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.spwallet.button.reset"), button -> {
                    SPWalletConfig config = SPWalletConfig.get();
                    SPWalletConfig defaults = SPWalletConfig.HANDLER.defaults();
                    config.hudX = defaults.hudX;
                    config.hudY = defaults.hudY;
                    config.notificationX = defaults.notificationX;
                    config.notificationY = defaults.notificationY;
                })
                .bounds(this.width / 2 - buttonWidth - 2, y, buttonWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.spwallet.button.done"),
                        button -> this.onClose())
                .bounds(this.width / 2 + 2, y, buttonWidth, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // Above the middle of the screen: clear of the corners the elements start in and of the
        // hotbar, which the game still draws behind this screen.
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40,
                EssentialColors.screenTitle());
        graphics.drawCenteredString(this.font, Component.translatable("gui.spwallet.description.hud_position"),
                this.width / 2, this.height / 2 - 26, EssentialColors.tabText());

        Element hovered = this.dragging != null ? this.dragging : this.at(mouseX, mouseY);

        for (Element element : this.elements()) {
            int x = element.x();
            int y = element.y();
            element.render(graphics, x, y);

            if (element == hovered) {
                graphics.renderOutline(x - 1, y - 1, element.width() + 2, element.height() + 2,
                        EssentialColors.tabTextSelected());
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Only dimmed, not blurred: the point is to see where the elements land against the game.
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        Element element = click.button() == 0 ? this.at(click.x(), click.y()) : null;

        if (element == null) {
            return false;
        }

        this.dragging = element;
        this.grabX = click.x() - element.x();
        this.grabY = click.y() - element.y();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent drag, double dragX, double dragY) {
        Element element = this.dragging;

        if (element == null) {
            return super.mouseDragged(drag, dragX, dragY);
        }

        element.save(
                BalanceHud.fraction((int) Math.round(drag.x() - this.grabX), element.width(), this.width),
                BalanceHud.fraction((int) Math.round(drag.y() - this.grabY), element.height(), this.height));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent release) {
        this.dragging = null;
        return super.mouseReleased(release);
    }

    @Override
    public void onClose() {
        SPWalletConfig.HANDLER.save();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The topmost element under the mouse. */
    private @Nullable Element at(double mouseX, double mouseY) {
        List<Element> elements = this.elements();

        for (int i = elements.size() - 1; i >= 0; i--) {
            if (elements.get(i).over(mouseX, mouseY)) {
                return elements.get(i);
            }
        }

        return null;
    }

    private static List<Row> rows() {
        SPServer server = CardInfoCache.server();
        List<Row> rows = server == null ? List.of() : CardInfoCache.favouriteRows(server);
        return rows.isEmpty() ? placeholder() : rows;
    }

    /**
     * Shown when no card is on the HUD yet, so there is still something to drag. The real panel
     * stays hidden until a card is starred, but its place can be chosen beforehand.
     */
    private static List<Row> placeholder() {
        return List.of(
                new Row("", Component.translatable("gui.spwallet.panel.example_card").getString(),
                        1337, false, null, CardColor.BLUE.argb(), true),
                new Row("", Component.translatable("gui.spwallet.panel.example_card_2").getString(),
                        64, false, null, CardColor.YELLOW.argb(), true));
    }
}
