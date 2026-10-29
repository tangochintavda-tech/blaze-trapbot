package net.aipvp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;

public class AiPvpClient implements ClientModInitializer {
    private boolean toggleDown = false, openDown = false;

    @Override
    public void onInitializeClient() {
        Config.load();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            long win = GLFW.glfwGetCurrentContext();
            if (win != 0 && mc.screen == null && mc.player != null) {
                boolean tg = GLFW.glfwGetKey(win, Config.parseKey(Config.I.toggleKey, GLFW.GLFW_KEY_Z)) == GLFW.GLFW_PRESS;
                if (tg && !toggleDown) Bot.toggle();
                toggleDown = tg;
                boolean op = GLFW.glfwGetKey(win, Config.parseKey(Config.I.openKey, GLFW.GLFW_KEY_GRAVE_ACCENT)) == GLFW.GLFW_PRESS;
                if (op && !openDown) mc.setScreen(new ConfigScreen());
                openDown = op;
            }
            Bot.tick();
        });
    }
}
