package ru.foxanto.spwallet.connect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/** Определяет, на каком сервере SPWorlds (СП / СПм) сейчас игрок, по адресу сервера. */
public final class ConnectServers {
    private static final Logger LOG = LoggerFactory.getLogger("SPWallet/SPConnect");

    /** Адреса по умолчанию, пока сайт не прислал актуальный список */
    private static volatile List<ConnectDto.ServerInfo> servers = List.of(
            new ConnectDto.ServerInfo("SP", "СП", List.of("sp.spworlds.ru", "sp.spworlds.org")),
            new ConnectDto.ServerInfo("SPM", "СПм", List.of("spm.spworlds.ru", "spm.spworlds.org"))
    );
    private static volatile ConnectDto.ServerInfo current;

    private ConnectServers() {}

    public static void refreshConfig() {
        ConnectApi.get().config().whenComplete((cfg, err) -> {
            if (err != null) {
                LOG.warn("не удалось получить настройки с сайта: {}", ConnectApi.errorMessage(err));
            } else if (cfg.servers() != null && !cfg.servers().isEmpty()) {
                servers = cfg.servers();
            }
        });
    }

    /** Вызывается при входе на сервер. Возвращает найденный сервер SPWorlds или null. */
    public static ConnectDto.ServerInfo detect(Minecraft client) {
        ServerData data = client.getCurrentServer();
        current = data == null ? null : match(data.ip);
        return current;
    }

    public static void clear() {
        current = null;
    }

    public static ConnectDto.ServerInfo current() {
        return current;
    }

    static ConnectDto.ServerInfo match(String address) {
        String host = address.toLowerCase(Locale.ROOT).trim();
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        for (ConnectDto.ServerInfo s : servers) {
            for (String h : s.hosts()) {
                if (host.equals(h) || host.endsWith("." + h)) return s;
            }
        }
        // Сайт мог прислать не все адреса (например, без .org) — тогда узнаём сервер, как кошелёк
        ru.foxanto.spwallet.util.SPServer known = ru.foxanto.spwallet.util.SPServer.byAddress(address);
        if (known == ru.foxanto.spwallet.util.SPServer.OTHER) return null;
        String id = known == ru.foxanto.spwallet.util.SPServer.SP ? "SP" : "SPM";
        for (ConnectDto.ServerInfo s : servers) {
            if (s.id().equals(id)) return s;
        }
        return new ConnectDto.ServerInfo(id, id.equals("SP") ? "СП" : "СПм", List.of());
    }
}
