package de.ytaudio;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;

/**
 * Simple-Voice-Chat-Plugin: mischt das YouTube-Audio in das Mikrofonsignal,
 * bevor der Voice-Chat es kodiert und an den Server sendet.
 */
public class YtAudioPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "ytaudio";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MergeClientSoundEvent.class, event -> {
            short[] frame = AudioEngine.nextFrame();
            if (frame != null) {
                event.mergeAudio(frame);
            }
        });
    }
}
