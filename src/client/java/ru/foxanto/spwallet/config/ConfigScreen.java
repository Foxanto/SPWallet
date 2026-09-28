package ru.foxanto.spwallet.config;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.foxanto.spwallet.gui.HudPositionScreen;
import ru.foxanto.spwallet.gui.SoundPickerScreen;
import ru.foxanto.spwallet.util.PaymentSound;

/**
 * The settings screen, built with YACL. Kept apart from {@link ModMenuIntegration} because Mod Menu
 * is optional: the wallet opens this screen itself, and must not drag the Mod Menu API in with it.
 */
public final class ConfigScreen {
    private ConfigScreen() {}

    public static Screen create(Screen parent) {
        SPWalletConfig config = SPWalletConfig.get();
        SPWalletConfig defaults = SPWalletConfig.HANDLER.defaults();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("SPWallet"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("config.spwallet.category.main"))
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("config.spwallet.group.behaviour"))
                                .option(toggle("capture_card_messages", defaults.captureCardMessages,
                                        () -> config.captureCardMessages,
                                        value -> config.captureCardMessages = value))
                                .option(toggle("sign_payments", defaults.signPayments,
                                        () -> config.signPayments,
                                        value -> config.signPayments = value))
                                .option(toggle("player_transfers", defaults.playerTransfers,
                                        () -> config.playerTransfers,
                                        value -> config.playerTransfers = value))
                                .option(toggle("qr_payments", defaults.qrPayments,
                                        () -> config.qrPayments,
                                        value -> config.qrPayments = value))
                                .option(toggle("payment_sound", defaults.paymentSound,
                                        () -> config.paymentSound,
                                        value -> config.paymentSound = value))
                                .option(ButtonOption.createBuilder()
                                        .name(Component.translatable("config.spwallet.option.payment_sound_choice"))
                                        .description(OptionDescription.of(Component.translatable(
                                                "config.spwallet.option.payment_sound_choice.description")))
                                        .action(screen -> Minecraft.getInstance()
                                                .setScreen(new SoundPickerScreen(screen, PaymentSound.Slot.PAYMENT)))
                                        .build())
                                .option(Option.<Float>createBuilder()
                                        .name(Component.translatable("config.spwallet.option.payment_sound_volume"))
                                        .description(OptionDescription.of(Component.translatable(
                                                "config.spwallet.option.payment_sound_volume.description")))
                                        .binding(defaults.paymentSoundVolume,
                                                () -> config.paymentSoundVolume,
                                                value -> config.paymentSoundVolume = value)
                                        .controller(option -> FloatSliderControllerBuilder.create(option)
                                                .range(0F, 1F)
                                                .step(0.05F)
                                                .formatValue(value -> Component.literal(Math.round(value * 100) + "%")))
                                        .build())
                                .option(toggle("force_gui_scale", defaults.forceGuiScale,
                                        () -> config.forceGuiScale,
                                        value -> config.forceGuiScale = value))
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("config.spwallet.group.incoming"))
                                .description(OptionDescription.of(Component.translatable(
                                        "config.spwallet.group.incoming.description")))
                                .option(toggle("incoming_notifications", defaults.incomingNotifications,
                                        () -> config.incomingNotifications,
                                        value -> config.incomingNotifications = value))
                                .option(toggle("incoming_sound", defaults.incomingSound,
                                        () -> config.incomingSound,
                                        value -> config.incomingSound = value))
                                .option(ButtonOption.createBuilder()
                                        .name(Component.translatable("config.spwallet.option.incoming_sound_choice"))
                                        .description(OptionDescription.of(Component.translatable(
                                                "config.spwallet.option.incoming_sound_choice.description")))
                                        .action(screen -> Minecraft.getInstance()
                                                .setScreen(new SoundPickerScreen(screen, PaymentSound.Slot.INCOMING)))
                                        .build())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("config.spwallet.group.display"))
                                .option(Option.<Theme>createBuilder()
                                        .name(Component.translatable("config.spwallet.option.theme"))
                                        .description(OptionDescription.of(Component.translatable(
                                                "config.spwallet.option.theme.description")))
                                        .binding(defaults.theme,
                                                () -> config.theme,
                                                value -> config.theme = value)
                                        .controller(option -> EnumControllerBuilder.create(option)
                                                .enumClass(Theme.class)
                                                .formatValue(Theme::label))
                                        .build())
                                .option(toggle("hud_enabled", defaults.hudEnabled,
                                        () -> config.hudEnabled,
                                        value -> config.hudEnabled = value))
                                .option(ButtonOption.createBuilder()
                                        .name(Component.translatable("config.spwallet.option.hud_position"))
                                        .description(OptionDescription.of(Component.translatable(
                                                "config.spwallet.option.hud_position.description")))
                                        .action(screen -> Minecraft.getInstance()
                                                .setScreen(new HudPositionScreen(screen)))
                                        .build())
                                .option(toggle("inventory_panel", defaults.inventoryPanel,
                                        () -> config.inventoryPanel,
                                        value -> config.inventoryPanel = value))
                                .option(Option.<PanelSide>createBuilder()
                                        .name(Component.translatable("config.spwallet.option.inventory_panel_side"))
                                        .description(OptionDescription.of(Component.translatable(
                                                "config.spwallet.option.inventory_panel_side.description")))
                                        .binding(defaults.inventoryPanelSide,
                                                () -> config.inventoryPanelSide,
                                                value -> config.inventoryPanelSide = value)
                                        .controller(option -> EnumControllerBuilder.create(option)
                                                .enumClass(PanelSide.class)
                                                .formatValue(PanelSide::label))
                                        .build())
                                .build())
                        .build())
                .save(SPWalletConfig.HANDLER::save)
                .build()
                .generateScreen(parent);
    }

    private static Option<Boolean> toggle(String key,
                                          boolean defaultValue,
                                          java.util.function.Supplier<Boolean> getter,
                                          java.util.function.Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("config.spwallet.option." + key))
                .description(OptionDescription.of(Component.translatable("config.spwallet.option." + key + ".description")))
                .binding(defaultValue, getter::get, setter)
                .controller(option -> BooleanControllerBuilder.create(option).yesNoFormatter())
                .build();
    }
}
