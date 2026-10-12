package de.ytaudio;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class YtAudioClient implements ClientModInitializer {
    public static final String MOD_ID = "ytaudio";
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.get();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ytaudio.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new YtAudioScreen());
                }
            }
        });

        // Beim Verlassen des Servers die Wiedergabe stoppen
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AudioEngine.stop());
    }
}
