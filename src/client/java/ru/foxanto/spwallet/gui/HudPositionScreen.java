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
import ru.foxanto.spwallet.util.CardInfoCache;
import ru.foxanto.spwallet.util.CardInfoCache.Row;
import ru.foxanto.spwallet.util.SPServer;

import java.util.List;

/**
 * Lets the player drag the HUD balance panel to where they want it. The position is saved when the
 * screen closes, by Esc or by the done button alike.
 */
public class HudPositionScreen extends Screen {
    private final @Nullable Screen parent;

    private boolean dragging;
    private double grabX;
    private double grabY;

    public HudPositionScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.spwallet.title.hud_position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int y = this.height - 28;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.spwallet.button.reset"), button -> {
                    SPWalletConfig.get().hudX = 0;
                    SPWalletConfig.get().hudY = 0;
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

        // Above the middle of the screen: clear of the corner the panel starts in and of the
        // hotbar, which the game still draws behind this screen.
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40,
                EssentialColors.screenTitle());
        graphics.drawCenteredString(this.font, Component.translatable("gui.spwallet.description.hud_position"),
                this.width / 2, this.height / 2 - 26, EssentialColors.tabText());

        List<Row> rows = this.rows();
        int width = CardPanel.width(this.font, rows, BalanceHud.STYLE);
        int height = CardPanel.height(rows, BalanceHud.STYLE);
        int x = BalanceHud.x(width, this.width);
        int y = BalanceHud.y(height, this.height);

        CardPanel.render(graphics, this.font, rows, BalanceHud.STYLE, x, y);

        if (this.dragging || this.over(mouseX, mouseY)) {
            graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, EssentialColors.tabTextSelected());
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Only dimmed, not blurred: the point is to see where the panel lands against the game.
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        if (click.button() != 0 || !this.over(click.x(), click.y())) {
            return false;
        }

        List<Row> rows = this.rows();
        this.dragging = true;
        this.grabX = click.x() - BalanceHud.x(CardPanel.width(this.font, rows, BalanceHud.STYLE), this.width);
        this.grabY = click.y() - BalanceHud.y(CardPanel.height(rows, BalanceHud.STYLE), this.height);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent drag, double dragX, double dragY) {
        if (!this.dragging) {
            return super.mouseDragged(drag, dragX, dragY);
        }

        List<Row> rows = this.rows();
        int width = CardPanel.width(this.font, rows, BalanceHud.STYLE);
        int height = CardPanel.height(rows, BalanceHud.STYLE);

        SPWalletConfig.get().hudX = BalanceHud.fraction((int) Math.round(drag.x() - this.grabX), width, this.width);
        SPWalletConfig.get().hudY = BalanceHud.fraction((int) Math.round(drag.y() - this.grabY), height, this.height);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent release) {
        this.dragging = false;
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

    private boolean over(double mouseX, double mouseY) {
        List<Row> rows = this.rows();
        int width = CardPanel.width(this.font, rows, BalanceHud.STYLE);
        int height = CardPanel.height(rows, BalanceHud.STYLE);
        int x = BalanceHud.x(width, this.width);
        int y = BalanceHud.y(height, this.height);

        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private List<Row> rows() {
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
