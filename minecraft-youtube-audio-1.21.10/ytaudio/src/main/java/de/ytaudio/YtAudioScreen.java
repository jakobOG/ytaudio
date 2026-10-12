package de.ytaudio;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Menü „Minecraft YouTube Audio“. */
public class YtAudioScreen extends Screen {
    private static final int ROW_H = 14;
    private static String urlDraft = "";
    private static int page = 0;

    private TextFieldWidget urlField;
    private int leftX, leftW, rightX, rightW;
    private int infoY, listY, rows;

    public YtAudioScreen() {
        super(Text.literal("Minecraft YouTube Audio"));
    }

    private ButtonWidget btn(String text, int x, int y, int w, int h, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.literal(text), action).dimensions(x, y, w, h).build();
    }

    private static String onOff(String name, boolean on) {
        return name + ": " + (on ? "EIN" : "AUS");
    }

    private static String dbText(double v) {
        long db = Math.round((v - 0.5) * 24);
        return (db > 0 ? "+" : "") + db + " dB";
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int totalW = Math.min(width - 16, 400);
        int x0 = (width - totalW) / 2;
        leftX = x0;
        leftW = (int) (totalW * 0.58);
        rightX = x0 + leftW + 8;
        rightW = totalW - leftW - 8;

        // ---------------- linke Spalte: URL, Steuerung, Playlist
        int y = 24;
        urlField = new TextFieldWidget(textRenderer, leftX, y, leftW - 72, 20, Text.literal("YouTube-URL"));
        urlField.setMaxLength(300);
        urlField.setPlaceholder(Text.literal("YouTube-Link einfügen"));
        urlField.setText(urlDraft);
        urlField.setChangedListener(s -> urlDraft = s);
        addDrawableChild(urlField);
        addDrawableChild(btn("Hinzufügen", leftX + leftW - 70, y, 70, 20, b -> {
            Playlist.add(urlDraft);
            urlDraft = "";
            urlField.setText("");
        }));

        int cy = y + 24;
        int bw = (leftW - 8) / 5;
        addDrawableChild(btn("|<", leftX, cy, bw, 20, b -> AudioEngine.previous()));
        addDrawableChild(btn("Play", leftX + (bw + 2), cy, bw, 20, b -> AudioEngine.playOrResume()));
        addDrawableChild(btn("Pause", leftX + 2 * (bw + 2), cy, bw, 20, b -> AudioEngine.pause()));
        addDrawableChild(btn("Stop", leftX + 3 * (bw + 2), cy, bw, 20, b -> AudioEngine.stop()));
        addDrawableChild(btn(">|", leftX + 4 * (bw + 2), cy, bw, 20, b -> AudioEngine.next()));

        infoY = cy + 24;
        listY = infoY + 40;
        rows = Math.max(1, (height - listY - 26) / ROW_H);

        List<Config.Entry> snap;
        synchronized (c) {
            snap = new ArrayList<>(c.playlist);
        }
        int pages = Math.max(1, (snap.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * rows;
        int to = Math.min(snap.size(), from + rows);
        for (int i = from; i < to; i++) {
            final int idx = i;
            String prefix = (AudioEngine.currentIndex == i ? "> " : "") + (i + 1) + ". ";
            String label = textRenderer.trimToWidth(prefix + snap.get(i).title, leftW - 28);
            int ry = listY + (i - from) * ROW_H;
            addDrawableChild(btn(label, leftX, ry, leftW - 16, ROW_H - 1, b -> AudioEngine.play(idx)));
            addDrawableChild(btn("x", leftX + leftW - 14, ry, 14, ROW_H - 1, b -> Playlist.remove(idx)));
        }
        int py = height - 22;
        addDrawableChild(btn("<", leftX, py, 30, 16, b -> { page--; clearAndInit(); }));
        addDrawableChild(btn(">", leftX + leftW - 30, py, 30, 16, b -> { page++; clearAndInit(); }));

        // ---------------- rechte Spalte: Lautstärke und Effekte
        int ry = 24;
        int step = 22;
        addDrawableChild(new ValueSlider(rightX, ry, rightW, 20, "Lautstärke", c.volume,
                v -> Math.round(v * 100) + " %", v -> { c.volume = (float) v; Config.touch(); }));
        ry += step;
        addDrawableChild(btn(onOff("Bass-Boost", c.bassBoost), rightX, ry, rightW, 20, b -> {
            c.bassBoost = !c.bassBoost;
            Config.save();
            b.setMessage(Text.literal(onOff("Bass-Boost", c.bassBoost)));
        }));
        ry += step;
        addDrawableChild(new ValueSlider(rightX, ry, rightW, 20, "Bass-Stärke", c.bassIntensity,
                v -> Math.round(v * 100) + " %", v -> { c.bassIntensity = (float) v; Config.touch(); }));
        ry += step;
        addDrawableChild(new ValueSlider(rightX, ry, rightW, 20, "EQ Bass", c.eqBass,
                YtAudioScreen::dbText, v -> { c.eqBass = (float) v; Config.touch(); }));
        ry += step;
        addDrawableChild(new ValueSlider(rightX, ry, rightW, 20, "EQ Mitten", c.eqMid,
                YtAudioScreen::dbText, v -> { c.eqMid = (float) v; Config.touch(); }));
        ry += step;
        addDrawableChild(new ValueSlider(rightX, ry, rightW, 20, "EQ Höhen", c.eqTreble,
                YtAudioScreen::dbText, v -> { c.eqTreble = (float) v; Config.touch(); }));
        ry += step;
        addDrawableChild(btn(onOff("Normalisieren", c.normalize), rightX, ry, rightW, 20, b -> {
            c.normalize = !c.normalize;
            Config.save();
            b.setMessage(Text.literal(onOff("Normalisieren", c.normalize)));
        }));
        ry += step;
        addDrawableChild(btn(onOff("Selbst mithören", c.monitor), rightX, ry, rightW, 20, b -> {
            c.monitor = !c.monitor;
            Config.save();
            b.setMessage(Text.literal(onOff("Selbst mithören", c.monitor)));
        }));
        ry += step;
        addDrawableChild(btn("yt-dlp aktualisieren", rightX, ry, rightW, 20,
                b -> AudioEngine.async(Tools::update)));
    }

    @Override
    public void tick() {
        if (Playlist.dirty) {
            Playlist.dirty = false;
            clearAndInit();
        }
    }

    private static String time(double sec) {
        int s = (int) Math.max(0, sec);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, this.title, width / 2, 8, 0xFFFFFFFF);

        // Aktueller Titel
        String t = AudioEngine.active ? AudioEngine.currentTitle : "Kein Titel aktiv";
        if (AudioEngine.active && AudioEngine.paused) t += " (pausiert)";
        ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(t, leftW)),
                leftX, infoY, 0xFFFFFFFF);

        // Fortschrittsbalken
        int barY = infoY + 12;
        ctx.fill(leftX, barY, leftX + leftW, barY + 4, 0xFF404040);
        if (AudioEngine.active && AudioEngine.durationSec > 0) {
            double pos = AudioEngine.positionSec();
            double ratio = Math.min(1.0, pos / AudioEngine.durationSec);
            ctx.fill(leftX, barY, leftX + (int) (leftW * ratio), barY + 4, 0xFF55FF55);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(time(pos) + " / " + time(AudioEngine.durationSec)),
                    leftX, barY + 7, 0xFFAAAAAA);
        } else if (AudioEngine.active) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(time(AudioEngine.positionSec())),
                    leftX, barY + 7, 0xFFAAAAAA);
        }

        // Statusmeldung
        String s = Status.msg;
        if (!s.isEmpty()) {
            int color = s.startsWith("Fehler") ? 0xFFFF5555 : 0xFFAAAAAA;
            ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(s, leftW)),
                    leftX, barY + 18, color);
        }

        // Seitenanzeige
        Config c = Config.get();
        int size;
        synchronized (c) { size = c.playlist.size(); }
        int pages = Math.max(1, (size + rows - 1) / rows);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Seite " + (page + 1) + "/" + pages),
                leftX + leftW / 2, height - 18, 0xFFAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Config.save();
    }
}
