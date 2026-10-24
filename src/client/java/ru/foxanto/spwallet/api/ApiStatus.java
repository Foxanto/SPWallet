package ru.foxanto.spwallet.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * How the SPWorlds API has been answering lately, and how long the background checks should leave
 * it alone after it stopped.
 *
 * <p>Every request {@link SPWorldsApi} sends reports here. A 429, a 5xx or no answer at all starts a
 * back-off that doubles with each failure in a row, from {@link #FIRST_BACKOFF_MS} up to
 * {@link #MAX_BACKOFF_MS}, or longer if the server sent {@code Retry-After}; the first answer that is
 * not one of those ends it. Only the checks that run on their own wait it out (see
 * {@link ru.foxanto.spwallet.util.CardInfoCache}): what the player asks for, such as opening the
 * wallet or sending a transfer, still goes out, and is what notices the API is back sooner.
 *
 * <p>Written from the HTTP client's threads and read from the render thread, hence the locking.
 */
public final class ApiStatus {
    /** An answer slower than this marks the API as slow. */
    static final long SLOW_MS = 2_000;
    static final long FIRST_BACKOFF_MS = 5_000;
    static final long MAX_BACKOFF_MS = 5 * 60_000;

    public enum State {
        /** Nothing has been asked yet. */
        UNKNOWN,
        OK,
        /** Answering, but slowly or with 429s. */
        SLOW,
        /** 5xx or no answer. */
        DOWN
    }

    private static State state = State.UNKNOWN;
    /** What the last failure was, or {@code null} while the API answers. */
    private static ApiException.@Nullable Kind failure;
    private static int failuresInARow;
    private static long backoffUntil;

    private ApiStatus() {}

    /**
     * Records one answer. Called by {@link SPWorldsApi} for every request; public for the game tests.
     *
     * @param status the HTTP status
     * @param elapsedMs how long it took
     * @param retryAfterMs what {@code Retry-After} asked for, or {@code 0} if it was not sent
     */
    public static synchronized void answered(int status, long elapsedMs, long retryAfterMs, long now) {
        ApiException.Kind kind = ApiException.Kind.of(status);

        if (!kind.apiWide()) {
            // A 401 or a 404 is still the API answering; it is the card that is wrong.
            failure = null;
            failuresInARow = 0;
            backoffUntil = 0;
            state = elapsedMs > SLOW_MS ? State.SLOW : State.OK;
            return;
        }

        failed(kind, retryAfterMs, now);
    }

    /** Records a request that got no answer at all. */
    public static synchronized void unreachable(long now) {
        failed(ApiException.Kind.UNREACHABLE, 0, now);
    }

    private static void failed(ApiException.Kind kind, long retryAfterMs, long now) {
        failure = kind;
        failuresInARow++;
        state = kind == ApiException.Kind.RATE_LIMITED ? State.SLOW : State.DOWN;

        long backoff = Math.min(MAX_BACKOFF_MS, FIRST_BACKOFF_MS << Math.min(failuresInARow - 1, 16));
        backoffUntil = now + Math.max(backoff, Math.min(retryAfterMs, MAX_BACKOFF_MS));
    }

    public static synchronized State state() {
        return state;
    }

    /** What the API last failed with, or {@code null} while it answers. */
    public static synchronized ApiException.@Nullable Kind failure() {
        return failure;
    }

    /** Whether the background checks should hold off at {@code now}. */
    public static synchronized boolean backingOff(long now) {
        return now < backoffUntil;
    }

    /** Seconds until the background checks try again, or {@code 0} when they are not waiting. */
    public static synchronized long secondsToRetry(long now) {
        return Math.max(0, (backoffUntil - now + 999) / 1000);
    }

    /**
     * A few words saying what is wrong, for the panels; {@code null} while all is well, so a working
     * API takes no room. No countdown, which would make a panel twitch as its width changed.
     */
    public static synchronized @Nullable Component problem() {
        if (state == State.UNKNOWN || state == State.OK) {
            return null;
        }

        return failure == null
                ? Component.translatable("gui.spwallet.api_status.slow")
                : failure.description();
    }

    /** The wallet's tooltip for the status mark: what {@link #problem()} says and when the next try is. */
    public static synchronized Component describe(long now) {
        Component problem = problem();

        if (problem == null) {
            return Component.translatable(state == State.OK
                    ? "gui.spwallet.api_status.ok"
                    : "gui.spwallet.api_status.unknown");
        }

        long seconds = secondsToRetry(now);
        return seconds == 0 ? problem : Component.translatable("gui.spwallet.api_status.retry", problem, seconds);
    }

    /** Forgets everything, for the game tests. */
    public static synchronized void reset() {
        state = State.UNKNOWN;
        failure = null;
        failuresInARow = 0;
        backoffUntil = 0;
    }
}
