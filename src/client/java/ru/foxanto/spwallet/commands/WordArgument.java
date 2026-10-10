package ru.foxanto.spwallet.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;

import java.util.List;

/**
 * Everything up to the next space, whatever characters it holds.
 *
 * <p>Brigadier's unquoted string stops at anything outside {@code [A-Za-z0-9_-.+]}, so a pasted card
 * token with {@code /} or {@code =} in it would be cut short. Like {@link CardNameArgument}, it is only
 * used by client commands and needs no registration.
 */
public final class WordArgument implements ArgumentType<String> {
    private static final List<String> EXAMPLES = List.of("1a2b3c4d-0000-0000-0000-000000000000", "Ab3/x+Yz=");

    public static WordArgument word() {
        return new WordArgument();
    }

    @Override
    public String parse(StringReader reader) {
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
