package ru.foxanto.spwallet.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.foxanto.spwallet.config.SPWalletConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Самообновление SPWallet из релизов GitHub.
 *
 * <p>При запуске игры мод смотрит последние релизы репозитория (из {@code contact.sources} в
 * fabric.mod.json). Если среди них есть версия новее установленной и для этой же версии Minecraft,
 * jar скачивается в папку mods под именем {@code .jar.pending} — такие файлы Fabric не загружает.
 * При выходе из игры старый jar заменяется новым. Игрок видит это заранее на плашке в главном
 * меню и может отменить.
 *
 * <p>Проверки: файл — zip с {@code fabric.mod.json} мода {@code spwallet} нужной версии, а если
 * GitHub прислал контрольную сумму файла ({@code digest}), она должна совпасть.
 */
public final class Updater {
    public enum State { IDLE, CHECKING, UP_TO_DATE, DOWNLOADING, READY, CANCELLED, FAILED }

    private static final Logger LOGGER = LoggerFactory.getLogger("SPWallet/Update");
    private static final String MOD_ID = "spwallet";
    private static final String FALLBACK_REPO = "Foxanto/SPWallet";
    private static final int MAX_SIZE = 32 * 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile State state = State.IDLE;
    private static volatile String newVersion;
    private static volatile String releaseUrl;
    private static volatile String notes = "";
    private static volatile Path pending;
    private static volatile Path current;
    private static boolean hookInstalled;

    private Updater() {}

    public static State state() {
        return state;
    }

    public static @Nullable String newVersion() {
        return newVersion;
    }

    public static @Nullable String releaseUrl() {
        return releaseUrl;
    }

    /** Что нового: первые строки описания релиза. */
    public static String notes() {
        return notes;
    }

    /** Проверяет обновления в фоне. В среде разработки и с выключенной настройкой ничего не делает. */
    public static void checkAsync() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment() || !SPWalletConfig.get().autoUpdate
                || state != State.IDLE) {
            return;
        }

        state = State.CHECKING;
        Thread thread = new Thread(Updater::run, "SPWallet-Update");
        thread.setDaemon(true);
        thread.start();
    }

    /** Игрок передумал: новая версия не поставится, а скачанный файл удалится при выходе. */
    public static synchronized void cancel() {
        if (state == State.READY) state = State.CANCELLED;
    }

    /** Игрок всё-таки хочет обновиться. */
    public static synchronized void restore() {
        if (state == State.CANCELLED && pending != null && Files.exists(pending)) state = State.READY;
    }

    private static void run() {
        try {
            ModContainer mod = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
            current = ownJar(mod);
            if (current == null) {
                LOGGER.info("SPWallet загружен не из jar в папке mods, автообновление выключено");
                state = State.UP_TO_DATE;
                return;
            }

            cleanLeftovers(current.getParent());
            Version own = mod.getMetadata().getVersion();
            String minecraft = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
            JsonObject release = newestRelease(repo(mod), own, minecraft);

            if (release == null) {
                state = State.UP_TO_DATE;
                return;
            }

            JsonObject asset = release.getAsJsonObject("_asset");
            String version = release.get("_version").getAsString();
            newVersion = version;
            releaseUrl = string(release, "html_url");
            notes = shorten(string(release, "body"));
            state = State.DOWNLOADING;
            LOGGER.info("Доступно обновление SPWallet {} (сейчас {}), скачиваю", version, own.getFriendlyString());

            byte[] jar = download(string(asset, "browser_download_url"));
            verify(jar, asset, version);

            Path mods = current.getParent();
            String name = MOD_ID + "-" + version.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.+-]", "_") + ".jar";
            Path target = mods.resolve(name);
            if (target.equals(current)) target = mods.resolve(name.replace(".jar", "-new.jar"));
            Path file = mods.resolve(target.getFileName() + ".pending");
            Files.write(file, jar);
            pending = file;
            installOnExit(target);
            state = State.READY;
            LOGGER.info("Обновление {} скачано, поставится при выходе из игры", version);
        } catch (Exception e) {
            state = State.FAILED;
            LOGGER.warn("Не удалось обновить SPWallet: {}", e.toString());
        }
    }

    /** Новейший релиз с jar для этой версии Minecraft, если он новее установленного. */
    private static @Nullable JsonObject newestRelease(String repo, Version own, String minecraft) throws Exception {
        String body = get("https://api.github.com/repos/" + repo + "/releases?per_page=10");
        JsonArray releases = JsonParser.parseString(body).getAsJsonArray();
        JsonObject best = null;
        Version bestVersion = own;

        for (JsonElement element : releases) {
            JsonObject release = element.getAsJsonObject();
            if (release.has("draft") && release.get("draft").getAsBoolean()) continue;

            for (JsonElement a : release.getAsJsonArray("assets")) {
                JsonObject asset = a.getAsJsonObject();
                String name = string(asset, "name");
                // spwallet-0.3.1-pbeta+1.21.11.jar: версия мода и Minecraft — в имени файла
                if (!name.startsWith(MOD_ID + "-") || !name.endsWith(".jar") || name.endsWith("-sources.jar")) continue;
                String versionText = name.substring(MOD_ID.length() + 1, name.length() - ".jar".length());
                // GitHub может заменить «+» в имени файла на точку
                if (versionText.endsWith("." + minecraft)) {
                    versionText = versionText.substring(0, versionText.length() - minecraft.length() - 1) + "+" + minecraft;
                }
                if (!versionText.endsWith("+" + minecraft)) continue;

                Version version;
                try {
                    version = SemanticVersion.parse(versionText);
                } catch (VersionParsingException e) {
                    continue;
                }

                if (version.compareTo(bestVersion) > 0) {
                    best = release;
                    bestVersion = version;
                    release.add("_asset", asset);
                    release.addProperty("_version", versionText);
                }
            }
        }

        return best;
    }

    private static void verify(byte[] jar, JsonObject asset, String version) throws Exception {
        String digest = string(asset, "digest");
        if (digest.startsWith("sha256:")) {
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
            if (!sha.equalsIgnoreCase(digest.substring(7))) {
                throw new IOException("контрольная сумма скачанного файла не совпала с GitHub");
            }
        }

        // Файл должен быть модом SPWallet той самой версии, а не чем-то другим под его именем
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (!entry.getName().equals("fabric.mod.json")) continue;
                JsonObject meta = JsonParser.parseString(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!MOD_ID.equals(string(meta, "id")) || !version.equals(string(meta, "version"))) {
                    throw new IOException("в скачанном файле не тот мод или не та версия");
                }
                return;
            }
        }
        throw new IOException("скачанный файл — не мод Fabric");
    }

    /**
     * При выходе из игры убирает старый jar и ставит новый. В Windows открытый jar удалить нельзя,
     * пока игра не закрылась, поэтому там это делает маленький скрипт, который дожидается выхода.
     */
    private static synchronized void installOnExit(Path target) {
        if (hookInstalled) return;
        hookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (state == State.CANCELLED) {
                deletePending();
                return;
            }
            if (state != State.READY || pending == null || !Files.exists(pending)) return;
            try {
                Files.delete(current);
                Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException locked) {
                swapAfterExit(current, pending, target);
            }
        }, "SPWallet-Update-Install"));
    }

    private static void swapAfterExit(Path old, Path file, Path target) {
        try {
            long pid = ProcessHandle.current().pid();
            String script = "$ErrorActionPreference='SilentlyContinue';"
                    + "Wait-Process -Id " + pid + " -Timeout 120;"
                    + "for($i=0;$i -lt 20;$i++){Remove-Item -LiteralPath " + ps(old) + ";if(-not(Test-Path -LiteralPath " + ps(old) + ")){break};Start-Sleep -Milliseconds 500};"
                    + "if(-not(Test-Path -LiteralPath " + ps(old) + ")){Move-Item -LiteralPath " + ps(file) + " -Destination " + ps(target) + "}";
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            LOGGER.warn("Не удалось запланировать установку обновления: {}", e.toString());
        }
    }

    private static String ps(Path path) {
        return "'" + path.toAbsolutePath().toString().replace("'", "''") + "'";
    }

    private static void deletePending() {
        try {
            if (pending != null) Files.deleteIfExists(pending);
        } catch (IOException e) {
            LOGGER.warn("Не удалось удалить {}: {}", pending, e.toString());
        }
    }

    /** Недоустановленные файлы прошлых запусков: обновление проверится и скачается заново. */
    private static void cleanLeftovers(Path mods) {
        try (var files = Files.list(mods)) {
            files.filter(f -> f.getFileName().toString().startsWith(MOD_ID + "-") && f.getFileName().toString().endsWith(".jar.pending"))
                    .forEach(f -> {
                        try {
                            Files.deleteIfExists(f);
                        } catch (IOException ignored) {
                            // занят — уберётся в следующий раз
                        }
                    });
        } catch (IOException ignored) {
            // папку mods не прочитать — значит, и обновлять некуда, это выяснится дальше
        }
    }

    private static @Nullable Path ownJar(ModContainer mod) {
        if (mod.getOrigin().getKind() != ModOrigin.Kind.PATH) return null;
        for (Path path : mod.getOrigin().getPaths()) {
            if (path.toString().endsWith(".jar") && Files.isRegularFile(path)) return path;
        }
        return null;
    }

    /** owner/repo из ссылки на исходники в fabric.mod.json. */
    private static String repo(ModContainer mod) {
        return mod.getMetadata().getContact().get("sources")
                .map(url -> url.replaceFirst("^https://github\\.com/", "").replaceFirst("/$", ""))
                .filter(r -> r.matches("[\\w.-]+/[\\w.-]+"))
                .orElse(FALLBACK_REPO);
    }

    private static String get(String url) throws Exception {
        HttpResponse<String> res = HTTP.send(request(url).header("Accept", "application/vnd.github+json").build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("GitHub ответил " + res.statusCode());
        return res.body();
    }

    private static byte[] download(String url) throws Exception {
        if (!url.startsWith("https://github.com/")) throw new IOException("неожиданная ссылка на файл: " + url);
        HttpResponse<byte[]> res = HTTP.send(request(url).build(), HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new IOException("скачивание: HTTP " + res.statusCode());
        if (res.body().length > MAX_SIZE) throw new IOException("файл слишком большой");
        return res.body();
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", "SPWallet-Updater");
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static String shorten(String body) {
        String text = body.replaceAll("(?m)^#+\\s*", "").replaceAll("\\*\\*", "").trim();
        String[] lines = text.split("\\R");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line.isBlank()) continue;
            if (out.length() + line.length() > 300) break;
            if (!out.isEmpty()) out.append('\n');
            out.append(line.trim());
        }
        return out.toString();
    }
}
