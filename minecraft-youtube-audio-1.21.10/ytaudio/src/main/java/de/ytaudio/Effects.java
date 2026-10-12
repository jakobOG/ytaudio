package de.ytaudio;

/** Lautstärke, Bass-Boost, Equalizer und Normalisierung (läuft pro 20-ms-Frame). */
final class Effects {
    private static int applied = -1;
    private static final Biquad bass = new Biquad();
    private static final Biquad eqLow = new Biquad();
    private static final Biquad eqMid = new Biquad();
    private static final Biquad eqHigh = new Biquad();
    private static float env = 0.0144f;   // laufende Signalleistung (Ziel-RMS 0.12 zum Start)
    private static float agcGain = 1f;

    private Effects() {}

    private static void rebuild(Config c) {
        if (c.bassBoost) bass.lowShelf(110, c.bassIntensity * 15.0); else bass.enabled = false;
        eqLow.lowShelf(250, (c.eqBass - 0.5) * 24.0);
        eqMid.peaking(1000, 0.8, (c.eqMid - 0.5) * 24.0);
        eqHigh.highShelf(4000, (c.eqTreble - 0.5) * 24.0);
    }

    static void process(short[] f) {
        Config c = Config.get();
        int v = Config.version;
        if (v != applied) {
            rebuild(c);
            applied = v;
        }
        float vol = c.volume;
        boolean norm = c.normalize;
        for (int i = 0; i < f.length; i++) {
            float x = f[i] / 32768f;
            if (bass.enabled) x = bass.process(x);
            if (eqLow.enabled) x = eqLow.process(x);
            if (eqMid.enabled) x = eqMid.process(x);
            if (eqHigh.enabled) x = eqHigh.process(x);
            if (norm) {
                env = env * 0.99995f + x * x * 0.00005f;
                float target = 0.12f / ((float) Math.sqrt(env) + 1e-4f);
                target = Math.max(0.3f, Math.min(3.0f, target));
                agcGain += (target - agcGain) * 0.0005f;
                x *= agcGain;
            }
            x *= vol;
            float ax = Math.abs(x);
            if (ax > 0.8f) {
                x = Math.signum(x) * (0.8f + 0.2f * (float) Math.tanh((ax - 0.8f) / 0.2f));
            }
            int s = Math.round(x * 32767f);
            f[i] = (short) Math.max(-32767, Math.min(32767, s));
        }
    }
}
