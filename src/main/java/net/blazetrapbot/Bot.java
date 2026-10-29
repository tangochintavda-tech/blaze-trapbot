package net.blazetrapbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class Bot {
    enum S { OFF, GO_TRAP, WAIT_TRAP, PICK, WAIT_VICTIM, GO_TARGET, WAIT_TARGET, ATTACK }

    private static S state = S.OFF;
    private static int t = 0;
    private static String victim = null;
    private static final Set<String> recent = new HashSet<>();
    private static final Random RNG = new Random();

    public static boolean isOn() { return state != S.OFF; }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (isOn()) { state = S.OFF; msg("§cBlaze Trapbot: выключен"); }
        else { state = S.GO_TRAP; t = 0; victim = null; recent.clear(); msg("§aBlaze Trapbot: включён"); }
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }

    private static void cmd(String c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(c);
    }

    private static Player find(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (name == null || mc.level == null) return null;
        for (Player p : mc.level.players()) {
            if (p != mc.player && p.getName().getString().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private static String randomName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return null;
        String me = mc.player.getName().getString();
        List<String> all = new ArrayList<>();
        for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
            String n = pi.getProfile().name();
            if (n != null && n.matches("[A-Za-z0-9_]{3,16}") && !n.equalsIgnoreCase(me)) all.add(n);
        }
        if (all.isEmpty()) return null;
        List<String> fresh = new ArrayList<>(all);
        fresh.removeAll(recent);
        if (fresh.isEmpty()) { recent.clear(); fresh = all; }
        return fresh.get(RNG.nextInt(fresh.size()));
    }

    private static void face(Player target) {
        Minecraft mc = Minecraft.getInstance();
        double dx = target.getX() - mc.player.getX();
        double dy = (target.getY() + target.getBbHeight() * 0.6) - mc.player.getEyeY();
        double dz = target.getZ() - mc.player.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (state == S.OFF) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) { state = S.OFF; return; }

        if (mc.player.isDeadOrDying()) {   // сами умерли — возрождаемся и начинаем цикл заново
            mc.player.respawn();
            state = S.GO_TRAP; t = 0;
            return;
        }

        t++;
        int delay = Config.I.tpDelaySeconds * 20;
        switch (state) {
            case GO_TRAP -> {
                cmd("home " + Config.I.trapHome);
                state = S.WAIT_TRAP; t = 0;
            }
            case WAIT_TRAP -> { if (t >= delay) state = S.PICK; }
            case PICK -> {
                victim = randomName();
                if (victim == null) { msg("§eНет игроков в табе, жду..."); state = S.WAIT_TRAP; t = delay - 40; return; }
                cmd("tpahere " + victim);
                msg("§eЖертва: " + victim);
                state = S.WAIT_VICTIM; t = 0;
            }
            case WAIT_VICTIM -> {
                Player p = find(victim);
                if (p != null && mc.player.distanceTo(p) <= 6 && t >= 20) { state = S.GO_TARGET; t = 0; }
                else if (t > Config.I.tpaTimeoutSeconds * 20) { recent.add(victim); state = S.PICK; t = 0; }
            }
            case GO_TARGET -> {
                cmd("home " + Config.I.targetHome);
                state = S.WAIT_TARGET; t = 0;
            }
            case WAIT_TARGET -> { if (t >= delay) { state = S.ATTACK; t = 0; } }
            case ATTACK -> {
                Player p = find(victim);
                boolean gone = (p == null && t > 40) || (p != null && (p.isDeadOrDying() || p.getHealth() <= 0));
                if (gone || t > Config.I.attackTimeoutSeconds * 20) {
                    recent.add(victim);
                    state = S.GO_TRAP; t = 0;
                    return;
                }
                if (p != null && mc.player.distanceTo(p) <= Config.I.reach + 1.0) {
                    face(p);
                    if (mc.player.getAttackStrengthScale(0f) >= 0.95f) {
                        mc.gameMode.attack(mc.player, p);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                    }
                }
            }
            default -> {}
        }
    }
}
