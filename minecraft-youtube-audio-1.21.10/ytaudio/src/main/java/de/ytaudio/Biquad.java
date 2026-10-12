package de.ytaudio;

/** Einfacher Biquad-Filter (RBJ Audio EQ Cookbook) für Bass-Boost und Equalizer. */
final class Biquad {
    private static final float FS = 48000f;
    private float b0 = 1, b1, b2, a1, a2;
    private float x1, x2, y1, y2;
    boolean enabled = false;

    private void set(double nb0, double nb1, double nb2, double na0, double na1, double na2) {
        b0 = (float) (nb0 / na0);
        b1 = (float) (nb1 / na0);
        b2 = (float) (nb2 / na0);
        a1 = (float) (na1 / na0);
        a2 = (float) (na2 / na0);
    }

    void lowShelf(double freq, double gainDb) {
        enabled = Math.abs(gainDb) > 0.1;
        if (!enabled) return;
        double A = Math.pow(10, gainDb / 40), w = 2 * Math.PI * freq / FS;
        double cos = Math.cos(w), alpha = Math.sin(w) / 2 * Math.sqrt(2), sq = 2 * Math.sqrt(A) * alpha;
        set(A * ((A + 1) - (A - 1) * cos + sq),
            2 * A * ((A - 1) - (A + 1) * cos),
            A * ((A + 1) - (A - 1) * cos - sq),
            (A + 1) + (A - 1) * cos + sq,
            -2 * ((A - 1) + (A + 1) * cos),
            (A + 1) + (A - 1) * cos - sq);
    }

    void highShelf(double freq, double gainDb) {
        enabled = Math.abs(gainDb) > 0.1;
        if (!enabled) return;
        double A = Math.pow(10, gainDb / 40), w = 2 * Math.PI * freq / FS;
        double cos = Math.cos(w), alpha = Math.sin(w) / 2 * Math.sqrt(2), sq = 2 * Math.sqrt(A) * alpha;
        set(A * ((A + 1) + (A - 1) * cos + sq),
            -2 * A * ((A - 1) + (A + 1) * cos),
            A * ((A + 1) + (A - 1) * cos - sq),
            (A + 1) - (A - 1) * cos + sq,
            2 * ((A - 1) - (A + 1) * cos),
            (A + 1) - (A - 1) * cos - sq);
    }

    void peaking(double freq, double q, double gainDb) {
        enabled = Math.abs(gainDb) > 0.1;
        if (!enabled) return;
        double A = Math.pow(10, gainDb / 40), w = 2 * Math.PI * freq / FS;
        double cos = Math.cos(w), alpha = Math.sin(w) / (2 * q);
        set(1 + alpha * A, -2 * cos, 1 - alpha * A, 1 + alpha / A, -2 * cos, 1 - alpha / A);
    }

    float process(float x) {
        float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1; x1 = x;
        y2 = y1; y1 = y;
        return y;
    }
}
