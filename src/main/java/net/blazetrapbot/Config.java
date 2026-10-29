package net.blazetrapbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("blaze_trapbot.json");
    public static Config I = new Config();

    public String trapHome = "trap";      // /home внутри трапки
    public String targetHome = "target";  // /home на месте таргета
    public String toggleKey = "Z";
    public String openKey = "GRAVE";      // Ё / ~
    public int tpDelaySeconds = 4;        // ожидание после /home
    public int tpaTimeoutSeconds = 30;    // сколько ждать жертву после /tpahere
    public int attackTimeoutSeconds = 25; // макс. время убийства одной жертвы
    public double reach = 3.0;

    public static void load() {
        try {
            if (Files.exists(FILE)) I = GSON.fromJson(Files.readString(FILE), Config.class);
        } catch (Exception e) { I = new Config(); }
        if (I == null) I = new Config();
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(I)); } catch (Exception ignored) {}
    }

    public static int parseKey(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim().toUpperCase();
        if (s.isEmpty()) return fallback;
        if (s.equals("GRAVE") || s.equals("~") || s.equals("`") || s.equals("Ё")) return GLFW.GLFW_KEY_GRAVE_ACCENT;
        if (s.length() == 1) {
            char c = s.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return c;
        }
        if (s.matches("F([1-9]|1[0-2])")) return GLFW.GLFW_KEY_F1 + Integer.parseInt(s.substring(1)) - 1;
        switch (s) {
            case "SPACE": return GLFW.GLFW_KEY_SPACE;
            case "TAB": return GLFW.GLFW_KEY_TAB;
            case "INSERT": return GLFW.GLFW_KEY_INSERT;
            case "HOME": return GLFW.GLFW_KEY_HOME;
            case "END": return GLFW.GLFW_KEY_END;
            case "RSHIFT": return GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "RCTRL": return GLFW.GLFW_KEY_RIGHT_CONTROL;
            default: return fallback;
        }
    }
}
