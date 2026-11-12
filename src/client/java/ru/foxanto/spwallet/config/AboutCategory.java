package ru.foxanto.spwallet.config;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * The "About" tab of the settings screen. Everything in it is read from {@code fabric.mod.json}, so
 * the people listed there and here can never drift apart.
 */
final class AboutCategory {
    private static final String MOD_ID = "spwallet";

    private AboutCategory() {}

    static ConfigCategory create() {
        ModMetadata meta = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

        ConfigCategory.Builder category = ConfigCategory.createBuilder()
                .name(Component.translatable("config.spwallet.category.about"));

        OptionGroup.Builder mod = OptionGroup.createBuilder()
                .name(Component.translatable("config.spwallet.about.group.mod"))
                .option(LabelOption.create(Component.literal(meta.getName()).withStyle(ChatFormatting.BOLD)
                        .append(Component.literal(" " + meta.getVersion().getFriendlyString())
                                .withStyle(ChatFormatting.GRAY))))
                .option(LabelOption.create(Component.translatable("modmenu.descriptionTranslation." + MOD_ID)));
        meta.getContact().asMap().forEach((key, url) -> mod.option(link(Component.translatable(
                "config.spwallet.about.contact." + key), url)));
        if (!meta.getLicense().isEmpty()) {
            mod.option(LabelOption.create(Component.translatable("config.spwallet.about.license",
                    String.join(", ", meta.getLicense()))));
        }
        category.group(mod.build());

        addPeople(category, "authors", meta.getAuthors());
        addPeople(category, "contributors", meta.getContributors());
        return category.build();
    }

    private static void addPeople(ConfigCategory.Builder category, String key, Collection<Person> people) {
        if (people.isEmpty()) {
            return;
        }
        OptionGroup.Builder group = OptionGroup.createBuilder()
                .name(Component.translatable("config.spwallet.about.group." + key));
        for (Person person : people) {
            Component name = personLabel(person.getName());
            Map<String, String> contacts = person.getContact().asMap();
            if (contacts.isEmpty()) {
                group.option(LabelOption.create(name));
                continue;
            }
            contacts.forEach((type, url) -> group.option(link(contacts.size() == 1 ? name
                    : name.copy().append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                            .append(Component.translatable("config.spwallet.about.contact." + type)), url)));
        }
        category.group(group.build());
    }

    /** Splits "Name (Role)" from the metadata into a name and a translated, greyed-out role. */
    private static Component personLabel(String raw) {
        int open = raw.indexOf('(');
        if (open < 0 || !raw.endsWith(")")) {
            return Component.literal(raw);
        }
        String name = raw.substring(0, open).trim();
        String role = raw.substring(open + 1, raw.length() - 1).trim();
        String roleKey = "config.spwallet.about.role." + role.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        return Component.literal(name).append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatableWithFallback(roleKey, role).withStyle(ChatFormatting.GRAY));
    }

    private static ButtonOption link(Component name, String url) {
        return ButtonOption.createBuilder()
                .name(name)
                .description(OptionDescription.of(Component.literal(url)))
                .action(screen -> ConfirmLinkScreen.confirmLinkNow(screen, url))
                .build();
    }
}
