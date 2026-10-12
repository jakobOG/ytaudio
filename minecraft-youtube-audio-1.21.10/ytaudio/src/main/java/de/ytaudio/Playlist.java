package de.ytaudio;

import java.util.regex.Pattern;

/** Hinzufügen und Löschen von Playlist-Einträgen. */
public final class Playlist {
    /** Menü neu aufbauen, sobald sich die Playlist geändert hat. */
    public static volatile boolean dirty = false;

    private static final Pattern YT = Pattern.compile(
            "^(https?://)?([\\w-]+\\.)?(youtube\\.com|youtu\\.be|youtube-nocookie\\.com)/\\S+$",
            Pattern.CASE_INSENSITIVE);

    private Playlist() {}

    public static void add(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            Status.set("Fehler: Bitte zuerst einen YouTube-Link einfügen.");
            return;
        }
        if (!YT.matcher(url).matches()) {
            Status.set("Fehler: Das ist kein gültiger YouTube-Link.");
            return;
        }
        if (!url.toLowerCase().startsWith("http")) url = "https://" + url;
        if (url.contains("/playlist?")) {
            Status.set("Fehler: Playlist-Links werden nicht unterstützt, bitte Video-Links einzeln hinzufügen.");
            return;
        }
        final String u = url;
        Status.set("Prüfe Link ...");
        AudioEngine.async(() -> {
            try {
                Tools.Info i = Tools.resolve(u, false);
                Config c = Config.get();
                Config.Entry e = new Config.Entry();
                e.url = u;
                e.title = i.title;
                e.duration = i.duration;
                synchronized (c) { c.playlist.add(e); }
                Config.save();
                dirty = true;
                Status.set("Hinzugefügt: " + i.title);
            } catch (Exception ex) {
                Status.set("Fehler: " + Tools.friendly(ex));
            }
        });
    }

    public static void remove(int index) {
        Config c = Config.get();
        synchronized (c) {
            if (index < 0 || index >= c.playlist.size()) return;
            c.playlist.remove(index);
        }
        Config.save();
        AudioEngine.onRemoved(index);
        dirty = true;
    }
}
