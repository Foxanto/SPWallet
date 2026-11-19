package ru.foxanto.spwallet.connect;

/** Ошибка API сайта SPConnect: HTTP-статус, текст для игрока и машинный код (например, cooldown). */
public class ConnectException extends RuntimeException {
    public final int status;
    public final String code;
    /** Для кулдауна: сколько секунд ждать */
    public final int retryAfter;

    public ConnectException(int status, String message, String code, int retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }
}
