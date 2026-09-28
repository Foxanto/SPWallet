package ru.foxanto.spwallet.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import ru.foxanto.spwallet.config.SPWalletConfig;
import ru.foxanto.spwallet.util.PaymentSound;

/**
 * Lets the player pick one of the mod's sounds ({@link PaymentSound.Slot}) from the built-in ones, those added by resource packs and
 * the files in the audios folder. Clicking a sound picks it and plays it; the choice is saved at once.
 */
public class SoundPickerScreen extends Screen {
    private static final int TOP = 36;
    private static final int BOTTOM = 64;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_WIDTH = 300;

    private final @Nullable Screen parent;
    private final PaymentSound.Slot slot;
    private @Nullable SoundList list;

    public SoundPickerScreen(@Nullable Screen parent, PaymentSound.Slot slot) {
        super(slot.title());
        this.parent = parent;
        this.slot = slot;
    }

    @Override
    protected void init() {
        this.list = new SoundList(this.minecraft, this.width, this.height - TOP - BOTTOM, TOP, this.slot);
        this.addRenderableWidget(this.list);
        this.refresh();

        int buttonWidth = 100;
        int y = this.height - 28;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.spwallet.button.open_folder"), button -> {
                    PaymentSound.createFolder();
                    Util.getPlatform().openPath(PaymentSound.folder());
                })
                .bounds(this.width / 2 - buttonWidth * 3 / 2 - 4, y, buttonWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.spwallet.button.refresh"),
                        button -> this.refresh())
                .bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.spwallet.button.done"),
                        button -> this.onClose())
                .bounds(this.width / 2 + buttonWidth / 2 + 4, y, buttonWidth, 20)
                .build());
    }

    private void refresh() {
        if (this.list != null) {
            this.list.fill(this.slot.choice());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, EssentialColors.screenTitle());
        graphics.drawCenteredString(this.font, Component.translatable("gui.spwallet.description.sound_picker"),
                this.width / 2, 22, EssentialColors.tabText());
        graphics.drawCenteredString(this.font, Component.translatable("gui.spwallet.description.sound_picker_folder"),
                this.width / 2, this.height - BOTTOM + 5, EssentialColors.tabText());
        graphics.drawCenteredString(this.font, Component.translatable("gui.spwallet.description.sound_picker_pack"),
                this.width / 2, this.height - BOTTOM + 17, EssentialColors.tabText());
    }

    @Override
    public void onClose() {
        PaymentSound.stopPreview();
        this.minecraft.setScreen(this.parent);
    }

    private static void choose(PaymentSound.Slot slot, PaymentSound.Option option) {
        slot.choose(option.id());
        SPWalletConfig.HANDLER.save();
        PaymentSound.preview(option.id());
    }

    private static final class SoundList extends ObjectSelectionList<SoundEntry> {
        private final PaymentSound.Slot slot;

        SoundList(Minecraft minecraft, int width, int height, int y, PaymentSound.Slot slot) {
            super(minecraft, width, height, y, ROW_HEIGHT);
            this.slot = slot;
        }

        void fill(String chosen) {
            this.clearEntries();
            SoundEntry selected = null;

            for (PaymentSound.Option option : PaymentSound.options()) {
                SoundEntry entry = new SoundEntry(this.slot, option);
                this.addEntry(entry);

                if (option.id().equals(chosen)) {
                    selected = entry;
                }
            }

            this.setSelected(selected);

            if (selected != null) {
                this.scrollToEntry(selected);
            }
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }
    }

    private static final class SoundEntry extends ObjectSelectionList.Entry<SoundEntry> {
        private final PaymentSound.Slot slot;
        private final PaymentSound.Option option;

        SoundEntry(PaymentSound.Slot slot, PaymentSound.Option option) {
            this.slot = slot;
            this.option = option;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            var font = Minecraft.getInstance().font;
            int y = this.getContentYMiddle() - font.lineHeight / 2;
            Component origin = this.option.origin().label();

            // A long file name is cut short rather than run under the origin label.
            int room = this.getContentWidth() - 4 - font.width(origin) - 8;
            String name = this.option.name().getString();

            if (font.width(name) > room) {
                name = font.plainSubstrByWidth(name, room - font.width("…")) + "…";
            }

            graphics.drawString(font, name, this.getContentX() + 2, y, EssentialColors.modalText());
            graphics.drawString(font, origin, this.getContentRight() - 2 - font.width(origin), y,
                    EssentialColors.cardBalance());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            choose(this.slot, this.option);
            return super.mouseClicked(click, doubled);
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", this.option.name());
        }
    }
}
