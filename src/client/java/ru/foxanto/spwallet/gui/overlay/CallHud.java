package ru.foxanto.spwallet.gui.overlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import ru.foxanto.spwallet.connect.ConnectDto;
import ru.foxanto.spwallet.connect.SPConnect;
import ru.foxanto.spwallet.gui.HudPositionScreen;
import ru.foxanto.spwallet.gui.PlayerSkins;

import java.util.ArrayList;
import java.util.List;

/**
 * Calls on the HUD, in the same look as the money notifications, under them on the left.
 *
 * <ul>
 *     <li>Staff get a card for each new call that stays until someone takes it or it is called
 *     off: who, which organisation, where, the message. The key (Y by default) takes the newest one;
 *     while any screen is open and the mouse is free, the card has a button to click instead.</li>
 *     <li>The caller gets «Вызов принят» with who is coming, for a few seconds.</li>
 *     <li>When staff finish the call, the caller gets a card asking to rate them; the key or the
 *     button opens the stars in the wallet.</li>
 * </ul>
 */
public final class CallHud {
    private static final long ACCEPTED_SHOWN_MS = 6_000;
    private static final long FINISHED_SHOWN_MS = 25_000;
    private static final long INCOMING_MAX_MS = 15 * 60_000;
    private static final long SLIDE_MS = 250;
    private static final int MAX_SHOWN = 3;

    private static final int PADDING = 4;
    private static final int STRIP = 2;
    private static final int HEAD = 18;
    private static final int GAP = 3;
    private static final int LINE = 10;
    private static final int BUTTON_HEIGHT = 12;
    private static final int MIN_WIDTH = 150;
    private static final int MAX_WIDTH = 230;

    private static final int GREEN = 0xFF55FF55;
    private static final int BUTTON = 0xFF2E9E57;
    private static final int BUTTON_HOVER = 0xFF37B866;

    private enum Kind { INCOMING, ACCEPTED, FINISHED }

    private record Entry(Kind kind, ConnectDto.CallInfo call, long shownAt) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    /** Where each card's button was last drawn, for the mouse. */
    private static final List<Button> BUTTONS = new ArrayList<>();

    private record Button(Entry entry, int x, int y, int width, int height) {
        boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + this.width && my >= this.y && my < this.y + this.height;
        }
    }

    private static KeyMapping key;

    private CallHud() {}

    public static void register(KeyMapping.Category category) {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.spwallet.take_call",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                Entry first = firstActionable();
                if (first != null) act(first);
            }
        });

        // Over any open screen the cards are drawn on top of it and take clicks.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            // The editor has its own copies; the wallet has the call tab.
            if (screen instanceof HudPositionScreen || screen instanceof ru.foxanto.spwallet.gui.WalletScreen) return;
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) ->
                    draw(graphics, mouseX, mouseY, true));
            ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
                for (Button button : List.copyOf(BUTTONS)) {
                    if (button.contains(click.x(), click.y())) {
                        act(button.entry());
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return false;
                    }
                }
                return true;
            });
        });
    }

    /** A new call for the organisations the player works for. */
    public static void incoming(ConnectDto.CallInfo call) {
        remove(call.id());
        add(new Entry(Kind.INCOMING, call, System.currentTimeMillis()));
    }

    /** The player's own call was taken. */
    public static void accepted(ConnectDto.CallInfo call) {
        remove(call.id());
        add(new Entry(Kind.ACCEPTED, call, System.currentTimeMillis()));
    }

    /** Staff finished the player's call: time to rate them. */
    public static void finished(ConnectDto.CallInfo call) {
        remove(call.id());
        add(new Entry(Kind.FINISHED, call, System.currentTimeMillis()));
    }

    /** The call is no longer anyone's business here: taken by someone else, called off, closed. */
    public static void remove(String callId) {
        ENTRIES.removeIf(entry -> entry.call().id().equals(callId));
    }

    public static void clear() {
        ENTRIES.clear();
    }

    /** How many call cards are up, for the game tests. */
    public static int shown() {
        return ENTRIES.size();
    }

    private static void add(Entry entry) {
        ENTRIES.add(entry);
        while (ENTRIES.size() > MAX_SHOWN) ENTRIES.removeFirst();
    }

    private static @Nullable Entry firstActionable() {
        // The newest call first: that is the one the player just heard.
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            if (ENTRIES.get(i).kind() != Kind.ACCEPTED) return ENTRIES.get(i);
        }
        return null;
    }

    private static void act(Entry entry) {
        ENTRIES.remove(entry);
        switch (entry.kind()) {
            case INCOMING -> SPConnect.accept(entry.call().id());
            case FINISHED -> SPConnect.rate(entry.call());
            case ACCEPTED -> {
            }
        }
    }

    /** On the HUD, while no screen is open; over a screen it is drawn by the screen hook instead. */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (Minecraft.getInstance().screen == null) {
            draw(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, false);
        }
    }

    private static void draw(GuiGraphics graphics, int mouseX, int mouseY, boolean mouseFree) {
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(entry -> now - entry.shownAt() > shownFor(entry));
        BUTTONS.clear();

        Minecraft client = Minecraft.getInstance();
        if (ENTRIES.isEmpty() || client.options.hideGui) return;

        Font font = client.font;
        int x = 4;
        int y = IncomingNotifications.stackBottom(graphics.guiWidth(), graphics.guiHeight()) + GAP;

        for (Entry entry : List.copyOf(ENTRIES)) {
            int width = width(font, entry);
            int height = height(entry);
            float shown = slide(entry, now);
            int left = x - Math.round((1 - shown) * (width + x));
            drawEntry(graphics, font, entry, left, y, width, height, mouseX, mouseY, mouseFree);
            y += height + GAP;
        }
    }

    private static void drawEntry(GuiGraphics g, Font font, Entry entry, int x, int y, int width, int height,
                                  int mouseX, int mouseY, boolean mouseFree) {
        ConnectDto.CallInfo call = entry.call();
        g.fill(x, y, x + width, y + height, CardPanel.HUD_BACKGROUND);
        g.fill(x + PADDING, y + PADDING - 1, x + PADDING + STRIP, y + height - PADDING + 1, orgColor(call));

        String headOf = entry.kind() == Kind.INCOMING ? call.caller().name() : call.acceptedBy();
        int headX = x + PADDING + STRIP + 4;
        if (headOf != null) PlayerSkins.drawHead(g, headOf, headX, y + PADDING, HEAD);

        int textX = headX + HEAD + 5;
        int top = y + PADDING;
        for (Component line : lines(font, entry, width - (textX - x) - PADDING)) {
            g.drawString(font, line, textX, top, CardPanel.HUD_TEXT, false);
            top += LINE;
        }

        if (entry.kind() == Kind.ACCEPTED) return;

        Component label = Component.translatable(entry.kind() == Kind.INCOMING
                ? "gui.spwallet.call_hud.accept" : "gui.spwallet.call_hud.rate");
        int by = y + height - PADDING - BUTTON_HEIGHT;

        if (mouseFree) {
            int bw = font.width(label) + 12;
            boolean hover = mouseX >= textX && mouseX < textX + bw && mouseY >= by && mouseY < by + BUTTON_HEIGHT;
            g.fill(textX, by, textX + bw, by + BUTTON_HEIGHT, hover ? BUTTON_HOVER : BUTTON);
            g.drawString(font, label, textX + 6, by + 2, 0xFFFFFFFF, false);
            BUTTONS.add(new Button(entry, textX, by, bw, BUTTON_HEIGHT));
        } else {
            Component hint = Component.literal("[").append(key.getTranslatedKeyMessage()).append("] ")
                    .withColor(GREEN).append(label.copy().withColor(CardPanel.HUD_DIM));
            g.drawString(font, hint, textX, by + 2, CardPanel.HUD_DIM, false);
        }
    }

    private static List<Component> lines(Font font, Entry entry, int room) {
        ConnectDto.CallInfo call = entry.call();
        String org = call.org() == null ? "" : call.org().name();
        List<Component> lines = new ArrayList<>();

        switch (entry.kind()) {
            case INCOMING -> {
                lines.add(Component.literal(org).withColor(orgColor(call))
                        .append(Component.literal(" · " + call.caller().name()).withColor(CardPanel.HUD_TEXT)));
                lines.add(Component.literal(coords(call)).withColor(CardPanel.HUD_DIM));
                if (call.message() != null && !call.message().isBlank()) {
                    lines.add(Component.literal(font.plainSubstrByWidth("«" + call.message() + "»", room))
                            .withColor(CardPanel.HUD_DIM));
                }
            }
            case ACCEPTED -> {
                lines.add(Component.translatable("gui.spwallet.call_hud.accepted").withColor(GREEN));
                lines.add(Component.translatable("gui.spwallet.call_hud.coming", call.acceptedBy(), org)
                        .withColor(CardPanel.HUD_DIM));
            }
            case FINISHED -> {
                lines.add(Component.translatable("gui.spwallet.call_hud.finished").withColor(CardPanel.HUD_TEXT));
                lines.add(Component.translatable("gui.spwallet.call_hud.rate_who", call.acceptedBy())
                        .withColor(CardPanel.HUD_DIM));
            }
        }
        return lines;
    }

    private static int width(Font font, Entry entry) {
        int text = 0;
        for (Component line : lines(font, entry, MAX_WIDTH)) text = Math.max(text, font.width(line));
        int fixed = PADDING * 2 + STRIP + 4 + HEAD + 5;
        return Math.clamp(fixed + text, MIN_WIDTH, MAX_WIDTH);
    }

    private static int height(Entry entry) {
        int lines = lines(Minecraft.getInstance().font, entry, MAX_WIDTH).size();
        int content = lines * LINE - 1;
        if (entry.kind() != Kind.ACCEPTED) content += 3 + BUTTON_HEIGHT;
        return PADDING * 2 + Math.max(HEAD, content);
    }

    private static long shownFor(Entry entry) {
        return switch (entry.kind()) {
            case INCOMING -> INCOMING_MAX_MS;
            case ACCEPTED -> ACCEPTED_SHOWN_MS;
            case FINISHED -> FINISHED_SHOWN_MS;
        };
    }

    private static float slide(Entry entry, long now) {
        long age = now - entry.shownAt();
        float in = Math.min(1F, age / (float) SLIDE_MS);
        float out = Math.min(1F, (shownFor(entry) - age) / (float) SLIDE_MS);
        float t = Math.clamp(Math.min(in, out), 0F, 1F);
        return 1 - (1 - t) * (1 - t);
    }

    private static int orgColor(ConnectDto.CallInfo call) {
        try {
            return 0xFF000000 | Integer.parseInt(call.org().color().replace("#", ""), 16);
        } catch (RuntimeException e) {
            return 0xFF5865F2;
        }
    }

    private static String coords(ConnectDto.CallInfo call) {
        String dim = switch (call.dimension() == null ? "" : call.dimension()) {
            case "minecraft:the_nether" -> " · ад";
            case "minecraft:the_end" -> " · энд";
            default -> "";
        };
        return call.x() + " " + call.y() + " " + call.z() + dim;
    }
}
