package ru.foxanto.spwallet.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A SPWorlds card as it is stored by the mod: a user chosen display name plus the credentials
 * the SPWorlds public API authenticates with.
 */
public record Card(String name, String id, String token) {
    /**
     * The value of the {@code Authorization} header the public API expects, which is
     * {@code Bearer base64(id:token)}.
     */
    public String authorization() {
        String key = this.id + ":" + this.token;
        return "Bearer " + Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }
}
