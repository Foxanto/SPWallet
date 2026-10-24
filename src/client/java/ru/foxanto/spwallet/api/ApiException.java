package ru.foxanto.spwallet.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Thrown when the SPWorlds API answers with an error, or cannot be reached at all. */
public class ApiException extends RuntimeException {
    /**
     * What went wrong, as far as the player is concerned: the first three are the API's trouble and
     * hit every card alike, the rest are about the one card asked for.
     */
    public enum Kind {
        /** No answer at all: no connection, a timeout, a DNS failure. */
        UNREACHABLE,
        /** A 5xx. */
        SERVER,
        /** A 429: the 200 requests a minute are used up. */
        RATE_LIMITED,
        /** A 401 or 403, which is what a wrong or revoked card token gets. */
        UNAUTHORIZED,
        OTHER;

        /** Whether this is the API's trouble rather than the card's. */
        public boolean apiWide() {
            return this == UNREACHABLE || this == SERVER || this == RATE_LIMITED;
        }

        /** A sentence for the wallet, such as "API is unavailable". */
        public Component description() {
            return Component.translatable("gui.spwallet.api_error." + this.name().toLowerCase(Locale.ROOT));
        }

        /** A word or two that fits in a panel row, where the balance would be. */
        public Component shortDescription() {
            return Component.translatable("gui.spwallet.api_error.short." + this.name().toLowerCase(Locale.ROOT));
        }

        static Kind of(int status) {
            if (status == 0) {
                return UNREACHABLE;
            }

            if (status == 429) {
                return RATE_LIMITED;
            }

            if (status == 401 || status == 403) {
                return UNAUTHORIZED;
            }

            return status / 100 == 5 ? SERVER : OTHER;
        }
    }

    /** The HTTP status, or {@code 0} when the server never answered. */
    private final int status;

    public ApiException(String message) {
        this(message, 200);
    }

    public ApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    /** No answer at all, for which {@code cause} says why. */
    public ApiException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public ApiException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return this.status;
    }

    public Kind kind() {
        return Kind.of(this.status);
    }

    /**
     * The kind of whatever a future failed with: the {@link ApiException} inside the
     * {@link java.util.concurrent.CompletionException} it is wrapped in, or {@link Kind#OTHER} for
     * anything else.
     */
    public static Kind kindOf(@Nullable Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException api) {
                return api.kind();
            }
        }

        return Kind.OTHER;
    }

    /** The message of the {@link ApiException} inside {@code throwable}, or of {@code throwable} itself. */
    public static String messageOf(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException) {
                return String.valueOf(cause.getMessage());
            }
        }

        return String.valueOf(throwable.getCause() == null ? throwable.getMessage() : throwable.getCause().getMessage());
    }
}
