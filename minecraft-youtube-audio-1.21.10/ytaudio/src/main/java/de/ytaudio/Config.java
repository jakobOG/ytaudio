package de.ytaudio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Playlist und Einstellungen, gespeichert in config/ytaudio/config.json. */
public final class Config {
    public static class Entry {
        public String url;
        public String title;
        public int duration;
    }

    public List<Entry> playlist = new ArrayList<>();
    public float volume = 0.8f;
    public boolean bassBoost = false;
    public float bassIntensity = 0.5f;
    public float eqBass = 0.5f;
    public float eqMid = 0.5f;
    public float eqTreble = 0.5f;
    public boolean normalize = false;
    public boolean monitor = true;
    public int lastIndex = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Config instance;
    /** Wird bei jeder Änderung erhöht, damit die Audio-Effekte neu berechnet werden. */
    public static volatile int version = 0;

    public static Config get() {
        Config c = instance;
        if (c == null) {
            synchronized (Config.class) {
                if (instance == null) instance = load();
                c = instance;
            }
        }
        return c;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("ytaudio").resolve("config.json");
    }

    private static Config load() {
        try {
            Path p = file();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) {
                    if (c.playlist == null) c.playlist = new ArrayList<>();
                    return c;
                }
            }
        } catch (Exception ignored) {
        }
        return new Config();
    }

    /** Nur Effekte neu berechnen (ohne Datei zu schreiben, z. B. beim Ziehen eines Reglers). */
    public static void touch() {
        version++;
    }

    public static synchronized void save() {
        version++;
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(get()));
        } catch (IOException ignored) {
        }
    }
}
