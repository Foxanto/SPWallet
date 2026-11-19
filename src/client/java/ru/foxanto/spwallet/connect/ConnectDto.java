package ru.foxanto.spwallet.connect;

import java.util.List;

/** Модели ответов API сайта SPConnect (поля совпадают с JSON). */
public final class ConnectDto {
    private ConnectDto() {}

    public record ServerInfo(String id, String name, List<String> hosts) {}

    public record ModConfigResponse(int protocol, List<ServerInfo> servers) {}

    public record AuthChallenge(String serverId) {}

    public record AuthResult(String token, String uuid, String name, long expiresIn) {}

    public record OrgInfo(String id, String name, String description, String color, int cooldownMinutes, int cooldownRemaining) {}

    public record OrgRef(String id, String name, String color) {}

    public record Player(String uuid, String name) {}

    public record CallInfo(
            String id,
            OrgRef org,
            Player caller,
            String server,
            String dimension,
            int x,
            int y,
            int z,
            String message,
            String status,
            String acceptedBy,
            String acceptedByUuid
    ) {}

    public record CreateCallResult(CallInfo call, int employeesOnline) {}

    public record CreateCallRequest(String orgId, String server, String dimension, int x, int y, int z, String message) {}

    public record Me(String uuid, String name, List<OrgRef> employeeOf) {}

    /** Сообщение реалтайм-канала: type = hello | call.new | call.updated | review.new | pong */
    public record Event(String type, CallInfo call, ReviewInfo review) {}

    /** Оценка игрока: у своего вызова — его собственная. */
    public record MyReview(int rating, String text) {}

    /** Свой вызов игрока за последние сутки. */
    public record MyCall(
            String id,
            OrgRef org,
            String status,
            String acceptedBy,
            String createdAt,
            MyReview review
    ) {}

    /** Отзыв о сотруднике. */
    public record ReviewInfo(String id, OrgRef org, String callerName, int rating, String text, String createdAt) {}

    public record Summary(Double average, int count) {}

    public record OrgSummary(OrgRef org, Double average, int count) {}

    /** Отзывы о самом игроке как о сотруднике. */
    public record ReviewsAbout(Summary total, List<OrgSummary> orgs, List<ReviewInfo> reviews) {}
}
