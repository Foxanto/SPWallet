package ru.foxanto.spwallet.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.List;

/**
 * A card name, optionally prefixed with its server: {@code Тбанк}, {@code spm/Тбанк}, or
 * {@code "Моя карта"} in quotes when it has spaces.
 *
 * <p>Brigadier's own unquoted string only takes Latin letters, digits and {@code _-.+}, so a Cyrillic
 * name or the {@code /} between server and name broke the command unless the whole thing was quoted.
 * This reads up to the next space instead. It is only ever used by a client command, so it is never
 * sent to a server and needs no registration.
 */
public final class CardNameArgument implements ArgumentType<String> {
    private static final List<String> EXAMPLES = List.of("Основная", "spm/Тбанк", "\"Моя карта\"");

    public static CardNameArgument cardName() {
        return new CardNameArgument();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) {
            return reader.readQuotedString();
        }

        int start = reader.getCursor();

        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }

        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public List<String> getExamples() {
        return EXAMPLES;
    }
}
