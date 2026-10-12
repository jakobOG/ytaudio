package de.ytaudio;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Regler mit Beschriftung und Wertanzeige (Wert 0.0 bis 1.0). */
final class ValueSlider extends SliderWidget {
    private final String label;
    private final DoubleFunction<String> format;
    private final DoubleConsumer onChange;

    ValueSlider(int x, int y, int w, int h, String label, double value,
                DoubleFunction<String> format, DoubleConsumer onChange) {
        super(x, y, w, h, Text.empty(), value);
        this.label = label;
        this.format = format;
        this.onChange = onChange;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        if (format == null) return;
        setMessage(Text.literal(label + ": " + format.apply(this.value)));
    }

    @Override
    protected void applyValue() {
        if (onChange != null) onChange.accept(this.value);
    }
}
