package de.ytaudio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dekodiert YouTube-Audio mit FFmpeg zu 48 kHz / 16 Bit / Mono und liefert es in 20-ms-Frames
 * (960 Samples) an den Simple Voice Chat.
 */
public final class AudioEngine {
    public static final int FRAME = 960;
    private static final int FRAME_BYTES = FRAME * 2;

    private static final BlockingQueue<short[]> QUEUE = new ArrayBlockingQueue<>(150); // ca. 3 s Puffer
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ytaudio-worker");
        t.setDaemon(true);
        return t;
    });

    private static volatile int generation = 0;
    private static volatile Process ffmpegProc;

    public static volatile boolean active = false;
    public static volatile boolean paused = false;
    public static volatile int currentIndex = -1;
    public static volatile String currentTitle = "";
    public static volatile int durationSec = 0;
    private static volatile long consumed = 0;

    private static final Object MON_LOCK = new Object();
    private static SourceDataLine monitorLine;

    private AudioEngine() {}

    public static void async(Runnable r) { POOL.execute(r); }

    public static double positionSec() { return consumed * (double) FRAME / 48000.0; }

    // ---------------------------------------------------------------- Steuerung

    public static synchronized void play(int index) {
        Config c = Config.get();
        Config.Entry e;
        synchronized (c) {
            if (index < 0 || index >= c.playlist.size()) return;
            e = c.playlist.get(index);
        }
        stopInternal();
        final int gen = ++generation;
        active = true;
        paused = false;
        currentIndex = index;
        currentTitle = e.title;
        durationSec = e.duration;
        consumed = 0;
        c.lastIndex = index;
        POOL.execute(() -> runTrack(gen, index, e));
    }

    public static synchronized void stop() {
        stopInternal();
        Status.set("");
    }

    private static void stopInternal() {
        generation++;
        active = false;
        paused = false;
        currentIndex = -1;
        currentTitle = "";
        durationSec = 0;
        consumed = 0;
        QUEUE.clear();
        Process p = ffmpegProc;
        if (p != null) p.destroyForcibly();
        ffmpegProc = null;
        closeMonitor();
    }

    public static void pause() { if (active) paused = true; }

    /** Play-Taste: pausierten Titel fortsetzen oder gewählten Titel starten. */
    public static void playOrResume() {
        if (active && paused) { paused = false; return; }
        if (active) return;
        Config c = Config.get();
        int idx;
        synchronized (c) {
            idx = c.playlist.isEmpty() ? -1 : Math.max(0, Math.min(c.lastIndex, c.playlist.size() - 1));
        }
        if (idx >= 0) play(idx); else Status.set("Fehler: Die Playlist ist leer.");
    }

    public static void next() { step(1); }
    public static void previous() { step(-1); }

    private static void step(int d) {
        Config c = Config.get();
        int size;
        synchronized (c) { size = c.playlist.size(); }
        if (size == 0) return;
        int base = currentIndex >= 0 ? currentIndex : c.lastIndex;
        play(((base + d) % size + size) % size);
    }

    /** Wird aufgerufen, wenn ein Eintrag aus der Playlist gelöscht wurde. */
    public static synchronized void onRemoved(int index) {
        if (currentIndex == index) stopInternal();
        else if (currentIndex > index) currentIndex--;
    }

    // ---------------------------------------------------------------- Dekodieren

    private static void runTrack(int gen, int index, Config.Entry e) {
        try {
            Status.set("Lade: " + e.title + " ...");
            Tools.Info info = Tools.resolve(e.url, true);
            if (gen != generation) return;

            ProcessBuilder pb = new ProcessBuilder(
                    Tools.ffmpeg(), "-hide_banner", "-loglevel", "error",
                    "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
                    "-i", info.streamUrl,
                    "-vn", "-f", "s16le", "-ar", "48000", "-ac", "1", "pipe:1");
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            ffmpegProc = p;
            if (gen != generation) { p.destroyForcibly(); return; }
            Status.set("");

            long frames = 0;
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[FRAME_BYTES];
                while (gen == generation) {
                    int n = 0;
                    while (n < buf.length) {
                        int r = in.read(buf, n, buf.length - n);
                        if (r < 0) break;
                        n += r;
                    }
                    if (n == 0) break;
                    boolean partial = n < buf.length;
                    if (partial) Arrays.fill(buf, n, buf.length, (byte) 0);
                    short[] f = new short[FRAME];
                    ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(f);
                    while (gen == generation && !QUEUE.offer(f, 100, TimeUnit.MILLISECONDS)) { /* warten */ }
                    frames++;
                    if (partial) break;
                }
            }
            if (gen != generation) return;
            if (frames == 0) {
                Status.set("Fehler: FFmpeg konnte kein Audio lesen.");
                synchronized (AudioEngine.class) { if (gen == generation) stopInternal(); }
                return;
            }
            while (gen == generation && !QUEUE.isEmpty()) Thread.sleep(50);
            if (gen != generation) return;
            advance(gen, index);
        } catch (Exception ex) {
            if (gen == generation) {
                Status.set("Fehler: " + Tools.friendly(ex));
                synchronized (AudioEngine.class) { if (gen == generation) stopInternal(); }
            }
        }
    }

    private static synchronized void advance(int gen, int index) {
        if (gen != generation) return;
        Config c = Config.get();
        int size;
        synchronized (c) { size = c.playlist.size(); }
        if (index + 1 < size) {
            play(index + 1);
        } else {
            stopInternal();
            Status.set("Playlist beendet.");
        }
    }

    // ---------------------------------------------------------------- Voice-Chat-Seite

    /** Wird vom Voice-Chat alle 20 ms aufgerufen. Gibt null zurück, wenn nichts zu senden ist. */
    public static short[] nextFrame() {
        if (!active || paused) return null;
        short[] f = QUEUE.poll();
        if (f == null) return null;
        consumed++;
        Effects.process(f);
        monitor(f);
        return f;
    }

    // ---------------------------------------------------------------- Lokales Mithören

    private static void monitor(short[] f) {
        if (!Config.get().monitor) {
            closeMonitor();
            return;
        }
        synchronized (MON_LOCK) {
            try {
                if (monitorLine == null) {
                    AudioFormat fmt = new AudioFormat(48000f, 16, 1, true, false);
                    monitorLine = AudioSystem.getSourceDataLine(fmt);
                    monitorLine.open(fmt, FRAME_BYTES * 8);
                    monitorLine.start();
                }
                byte[] b = new byte[FRAME_BYTES];
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(f);
                if (monitorLine.available() >= b.length) monitorLine.write(b, 0, b.length);
            } catch (Exception e) {
                Config.get().monitor = false;
                monitorLine = null;
                Status.set("Fehler: Lokales Mithören nicht möglich, wurde deaktiviert.");
            }
        }
    }

    private static void closeMonitor() {
        synchronized (MON_LOCK) {
            if (monitorLine != null) {
                try { monitorLine.stop(); monitorLine.close(); } catch (Exception ignored) { }
                monitorLine = null;
            }
        }
    }
}
