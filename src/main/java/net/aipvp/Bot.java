package net.aipvp;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class Bot {
    private static boolean on = false;
    private static volatile boolean inFlight = false;
    private static volatile JsonObject decision = null;
    private static volatile long decisionAt = 0;
    private static long lastAsk = 0;
    private static volatile String lastError = null;

    public static boolean isOn() { return on; }

    public static void toggle() {
        on = !on;
        if (!on) releaseKeys();
        decision = null; lastError = null;
        msg(on ? "§aAI PvP: включён" : "§cAI PvP: выключен");
        if (on && Config.I.apiKey.isBlank()) msg("§eAPI ключ не задан (открой настройки на Ё)");
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }

    private static void releaseKeys() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false); mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false); mc.options.keySprint.setDown(false);
        mc.options.keyUse.setDown(false);
    }

    private static boolean ignored(String name) {
        for (String s : Config.I.ignoreNames.split(",")) if (!s.isBlank() && s.trim().equalsIgnoreCase(name)) return true;
        return false;
    }

    private static List<Player> nearby() {
        Minecraft mc = Minecraft.getInstance();
        List<Player> out = new ArrayList<>();
        for (Player p : mc.level.players())
            if (p != mc.player && !p.isSpectator() && !ignored(p.getName().getString()) && mc.player.distanceTo(p) <= 20) out.add(p);
        out.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));
        return out;
    }

    private static float wrap(float a) { a %= 360f; if (a > 180) a -= 360; if (a < -180) a += 360; return a; }

    private static float[] angles(Player t) {
        Minecraft mc = Minecraft.getInstance();
        double dx = t.getX() - mc.player.getX();
        double dy = (t.getY() + t.getBbHeight() * 0.6) - mc.player.getEyeY();
        double dz = t.getZ() - mc.player.getZ();
        return new float[]{(float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0),
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    private static String buildState(List<Player> near) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject s = new JsonObject();
        s.addProperty("health", mc.player.getHealth());
        s.addProperty("food", mc.player.getFoodData().getFoodLevel());
        s.addProperty("selected_slot", mc.player.getInventory().getSelectedSlot());
        JsonArray hb = new JsonArray();
        for (int i = 0; i < 9; i++) {
            var st = mc.player.getInventory().getItem(i);
            hb.add(i + ": " + (st.isEmpty() ? "empty" : st.getHoverName().getString() + " x" + st.getCount()));
        }
        s.add("hotbar", hb);
        JsonArray pl = new JsonArray();
        for (Player p : near) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getName().getString());
            o.addProperty("distance", Math.round(mc.player.distanceTo(p) * 10) / 10.0);
            o.addProperty("health", p.getHealth());
            o.addProperty("angle_off_crosshair", Math.round(wrap(angles(p)[0] - mc.player.getYRot())));
            o.addProperty("holding", p.getMainHandItem().getHoverName().getString());
            pl.add(o);
        }
        s.add("players", pl);
        s.addProperty("ignored", Config.I.ignoreNames);
        return s.toString();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!on) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) { on = false; return; }
        if (mc.screen != null || mc.player.isDeadOrDying()) { releaseKeys(); return; }

        List<Player> near = nearby();
        long now = System.currentTimeMillis();

        if (!inFlight && now - lastAsk >= Config.I.intervalMs && !near.isEmpty() && !Config.I.apiKey.isBlank()) {
            inFlight = true; lastAsk = now;
            AiClient.ask(buildState(near), d -> { decision = d; decisionAt = System.currentTimeMillis(); inFlight = false; },
                    e -> { lastError = e; inFlight = false; });
        }
        if (lastError != null) { msg("§cAI ошибка: " + lastError); lastError = null; }

        JsonObject d = decision;
        if (d == null || now - decisionAt > 3000 || near.isEmpty()) { releaseKeys(); return; }

        Player target = null;
        String tn = d.has("target") && !d.get("target").isJsonNull() ? d.get("target").getAsString() : null;
        for (Player p : near) if (tn != null && p.getName().getString().equalsIgnoreCase(tn)) target = p;
        if (target == null) { releaseKeys(); return; }

        float[] a = angles(target);
        float sp = Config.I.aimSpeed;
        float dyaw = Math.max(-sp, Math.min(sp, wrap(a[0] - mc.player.getYRot())));
        float dpit = Math.max(-sp, Math.min(sp, a[1] - mc.player.getXRot()));
        mc.player.setYRot(mc.player.getYRot() + dyaw);
        mc.player.setXRot(Math.max(-90f, Math.min(90f, mc.player.getXRot() + dpit)));

        int slot = d.has("slot") ? d.get("slot").getAsInt() : -1;
        if (slot >= 0 && slot <= 8) mc.player.getInventory().setSelectedSlot(slot);

        String mv = str(d, "move"), st = str(d, "strafe");
        mc.options.keyUp.setDown(mv.equals("forward"));
        mc.options.keyDown.setDown(mv.equals("back"));
        mc.options.keyLeft.setDown(st.equals("left"));
        mc.options.keyRight.setDown(st.equals("right"));
        mc.options.keyJump.setDown(bool(d, "jump"));
        mc.options.keySprint.setDown(bool(d, "sprint"));
        mc.options.keyUse.setDown(bool(d, "eat"));

        if (bool(d, "attack") && !bool(d, "eat") && mc.player.distanceTo(target) <= Config.I.reach + 0.5
                && Math.abs(wrap(a[0] - mc.player.getYRot())) < 15 && mc.player.getAttackStrengthScale(0f) >= 0.95f) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static String str(JsonObject d, String k) {
        return d.has(k) && !d.get(k).isJsonNull() ? d.get(k).getAsString().toLowerCase() : "none";
    }
    private static boolean bool(JsonObject d, String k) {
        return d.has(k) && !d.get(k).isJsonNull() && d.get(k).getAsBoolean();
    }
}
