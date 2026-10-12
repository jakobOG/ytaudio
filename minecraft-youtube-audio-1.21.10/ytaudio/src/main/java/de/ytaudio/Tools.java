package de.ytaudio;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Verwaltet yt-dlp, FFmpeg (und Deno) und löst YouTube-Links auf. */
public final class Tools {
    private static final boolean WIN = System.getProperty("os.name").toLowerCase().contains("win");
    private static boolean denoTried = false;
    private static boolean updatedThisSession = false;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public static final class Info {
        public String title;
        public int duration;
        public String streamUrl;
    }

    private Tools() {}

    static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("ytaudio").resolve("bin");
    }

    public static String ytdlp() { return WIN ? dir().resolve("yt-dlp.exe").toString() : "yt-dlp"; }
    public static String ffmpeg() { return WIN ? dir().resolve("ffmpeg.exe").toString() : "ffmpeg"; }

    private static Path deno() {
        Path p = dir().resolve("deno.exe");
        return Files.exists(p) ? p : null;
    }

    /** Lädt fehlende Programme einmalig herunter (nur Windows). */
    public static synchronized void ensure() throws Exception {
        if (!WIN) return;
        Files.createDirectories(dir());
        Path y = dir().resolve("yt-dlp.exe");
        if (!Files.exists(y)) {
            Status.set("Lade yt-dlp herunter (einmalig) ...");
            download("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe", y);
        }
        Path f = dir().resolve("ffmpeg.exe");
        if (!Files.exists(f)) {
            Status.set("Lade FFmpeg herunter (einmalig, ca. 100 MB) ...");
            Path zip = dir().resolve("ffmpeg.zip");
            download("https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip", zip);
            extract(zip, "bin/ffmpeg.exe", f);
            Files.deleteIfExists(zip);
        }
        Path d = dir().resolve("deno.exe");
        if (!Files.exists(d) && !denoTried) {
            denoTried = true;
            try {
                Status.set("Lade Deno herunter (einmalig, für YouTube nötig) ...");
                Path zip = dir().resolve("deno.zip");
                download("https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip", zip);
                extract(zip, "deno.exe", d);
                Files.deleteIfExists(zip);
            } catch (Exception ignored) {
                // optional
            }
        }
        Status.set("");
    }

    private static void download(String url, Path target) throws Exception {
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(15))
                .header("User-Agent", "ytaudio-mod")
                .build();
        HttpResponse<Path> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofFile(tmp));
        if (resp.statusCode() != 200) {
            Files.deleteIfExists(tmp);
            throw new IOException("Download fehlgeschlagen (HTTP " + resp.statusCode() + "): " + url);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void extract(Path zip, String suffix, Path target) throws IOException {
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().replace('\\', '/').endsWith(suffix)) {
                    Files.copy(z, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException(suffix + " nicht im Archiv gefunden");
    }

    private static final class Result {
        final List<String> out = new ArrayList<>();
        String error;
        int exit;
    }

    private static Result run(List<String> cmd, int timeoutSec) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeoutSec * 1000L);
                if (p.isAlive()) p.destroyForcibly();
            } catch (InterruptedException ignored) {
            }
        }, "ytaudio-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        Result res = new Result();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty()) continue;
                if (l.startsWith("ERROR")) res.error = l; else res.out.add(l);
            }
        }
        p.waitFor(5, TimeUnit.SECONDS);
        watchdog.interrupt();
        res.exit = p.isAlive() ? -1 : p.exitValue();
        return res;
    }

    private static List<String> ytBase() {
        List<String> c = new ArrayList<>();
        c.add(ytdlp());
        c.add("--no-playlist");
        c.add("--no-warnings");
        c.add("--socket-timeout");
        c.add("15");
        Path d = deno();
        if (d != null) {
            c.add("--js-runtimes");
            c.add("deno:" + d);
        }
        return c;
    }

    /** yt-dlp aktualisieren (YouTube ändert sich oft). */
    public static synchronized void update() {
        if (!WIN) return;
        try {
            Status.set("Aktualisiere yt-dlp ...");
            Result r = run(List.of(ytdlp(), "-U"), 120);
            Status.set(r.exit == 0 ? "yt-dlp ist aktuell." : "Fehler: yt-dlp-Update fehlgeschlagen.");
        } catch (Exception e) {
            Status.set("Fehler: " + friendly(e));
        }
    }

    public static Info resolve(String url, boolean needStream) throws Exception {
        ensure();
        try {
            return tryResolve(url, needStream);
        } catch (Exception first) {
            if (updatedThisSession || !WIN) throw first;
            updatedThisSession = true;
            Status.set("Aktualisiere yt-dlp und versuche es erneut ...");
            update();
            return tryResolve(url, needStream);
        }
    }

    private static Info tryResolve(String url, boolean needStream) throws Exception {
        List<String> cmd = ytBase();
        cmd.add("--print");
        cmd.add("title");
        cmd.add("--print");
        cmd.add("duration");
        if (needStream) {
            cmd.add("-f");
            cmd.add("bestaudio/best");
            cmd.add("--print");
            cmd.add("urls");
        }
        cmd.add(url);
        Result r = run(cmd, 90);
        if (r.exit != 0 || r.out.size() < 2) {
            throw new IOException(r.error != null ? r.error : "yt-dlp konnte den Link nicht lesen.");
        }
        Info i = new Info();
        i.title = r.out.get(0);
        try {
            i.duration = (int) Double.parseDouble(r.out.get(1));
        } catch (NumberFormatException e) {
            i.duration = 0;
        }
        if (needStream) {
            for (int k = r.out.size() - 1; k >= 2; k--) {
                if (r.out.get(k).startsWith("http")) { i.streamUrl = r.out.get(k); break; }
            }
            if (i.streamUrl == null) throw new IOException("Keine Audio-URL gefunden.");
        }
        return i;
    }

    public static String friendly(Exception e) {
        String m = e.getMessage();
        if (m == null) m = e.toString();
        return m.replaceFirst("^ERROR:\\s*", "");
    }
}
