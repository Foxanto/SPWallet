package ru.foxanto.spwallet.gui.component;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import ru.foxanto.spwallet.config.AmountFormat;
import ru.foxanto.spwallet.gui.EssentialColors;
import ru.foxanto.spwallet.storage.HistoryStorage.Recent;
import ru.foxanto.spwallet.storage.TemplateStorage.Template;
import ru.foxanto.spwallet.util.TransferMode;

import java.util.List;
import java.util.function.Consumer;

/**
 * The side column's quick list: the saved payments, then the receivers of the last transfers. A
 * click on either fills the transfer form in; nothing is sent until the player presses the button.
 */
public class QuickList extends FlowLayout {
    private final Consumer<Template> onTemplate;
    private final Consumer<Template> onDelete;
    private final Consumer<Recent> onRecent;

    public QuickList(Consumer<Template> onTemplate, Consumer<Template> onDelete, Consumer<Recent> onRecent) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.onTemplate = onTemplate;
        this.onDelete = onDelete;
        this.onRecent = onRecent;
        this.gap(3);
    }

    public void show(List<Template> templates, List<Recent> recent) {
        this.clearChildren();

        this.child(heading(Component.translatable("gui.spwallet.quick.templates")));

        if (templates.isEmpty()) {
            this.child(text(Component.translatable("gui.spwallet.quick.no_templates"), EssentialColors.cardBalance()));
        }

        for (Template template : templates) {
            this.child(this.templateRow(template));
        }

        this.child(heading(Component.translatable("gui.spwallet.quick.recent")).margins(Insets.top(6)));

        if (recent.isEmpty()) {
            this.child(text(Component.translatable("gui.spwallet.quick.no_recent"), EssentialColors.cardBalance()));
        }

        for (Recent receiver : recent) {
            this.child(this.recentRow(receiver));
        }
    }

    private FlowLayout templateRow(Template template) {
        TransparentButton delete = new TransparentButton(Component.literal("×"),
                EssentialColors.cardBalance(),
                EssentialColors.error(),
                EssentialColors.error(),
                button -> this.onDelete.accept(template));
        delete.tooltip(Component.translatable("gui.spwallet.quick.delete_template"));
        delete.positioning(Positioning.relative(100, 0));

        Component details = Component.literal(template.mode() == TransferMode.NICKNAME
                ? template.target()
                : "#" + template.target());

        if (template.amount() > 0) {
            details = details.copy().append(", ").append(AmountFormat.card(template.amount()));
        }

        Row row = new Row(() -> this.onTemplate.accept(template));
        row.child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                        .child(label(Component.literal(template.name()), EssentialColors.modalText()))
                        .child(delete))
                .child(text(details, EssentialColors.tabText()));

        if (template.comment() != null && !template.comment().isEmpty()) {
            row.child(text(Component.literal("«" + template.comment() + "»"), EssentialColors.cardBalance()));
        }

        return row;
    }

    private FlowLayout recentRow(Recent receiver) {
        Component who = receiver.receiverName() == null
                ? Component.literal("#" + receiver.receiver())
                : Component.literal(receiver.receiverName())
                        .append(Component.literal(" #" + receiver.receiver()).withColor(EssentialColors.cardBalance()));

        Row row = new Row(() -> this.onRecent.accept(receiver));
        row.child(text(who, EssentialColors.modalText()))
                .child(text(Component.translatable("gui.spwallet.quick.last_amount", AmountFormat.card(receiver.amount())),
                        EssentialColors.cardBalance()));

        return row;
    }

    private static LabelComponent heading(Component text) {
        return label(text, EssentialColors.screenTitle());
    }

    private static LabelComponent label(Component text, int color) {
        LabelComponent label = UIComponents.label(text);
        label.color(Color.ofArgb(color)).shadow(EssentialColors.textShadow());
        return label;
    }

    /** A label as wide as the row, which wraps rather than running past its edge. */
    private static LabelComponent text(Component text, int color) {
        LabelComponent label = label(text, color);
        label.horizontalSizing(Sizing.fill(100));
        return label;
    }

    /** One entry, which lights up under the mouse and is clicked as a whole. */
    private static final class Row extends FlowLayout {
        private final Runnable onPress;

        Row(Runnable onPress) {
            super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
            this.onPress = onPress;
            this.gap(1);
            this.padding(Insets.of(3, 4, 4, 4));
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
            // The delete cross takes its own click; the row only gets the rest. The cross does not
            // say it took it, so it is told apart by where the click landed, which comes in
            // relative to the row.
            if (this.childAt(this.x + (int) click.x(), this.y + (int) click.y()) instanceof TransparentButton) {
                return super.onMouseDown(click, doubled);
            }

            this.onPress.run();
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        @Override
        public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
            this.surface(Surface.flat(this.hovered ? EssentialColors.rowHover() : EssentialColors.border()));
            super.draw(context, mouseX, mouseY, partialTicks, delta);
        }
    }
}
