package net.blazetrapbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class Bot {
    enum S { OFF, GO_TRAP, TP_TRAP, PICK, WAIT_VICTIM, GO_TARGET, TP_TARGET, ATTACK }

    private static final double MOVED_BLOCKS = 0.5;
    private static final int MAX_TRIES = 8;

    private static S state = S.OFF;
    private static int t = 0;
    private static String victim = null;
    private static final Set<String> recent = new HashSet<>();
    private static final Random RNG = new Random();

    // состояние для надёжной телепортации через /home (с повторами)
    private static double sx, sy, sz;
    private static int tries = 0, lastSend = 0;

    public static boolean isOn() { return state != S.OFF; }

    public static void toggle() {
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

    private static boolean ignored(String name) {
        String list = Config.I.ignoreNames == null ? "" : Config.I.ignoreNames;
        for (String s : list.split("[,;\\s]+")) if (!s.isBlank() && s.trim().equalsIgnoreCase(name)) return true;
        return false;
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
            if (n != null && n.matches("[A-Za-z0-9_]{3,16}") && !n.equalsIgnoreCase(me) && !ignored(n)) all.add(n);
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

    private static void beginHome(S next) {
        Minecraft mc = Minecraft.getInstance();
        sx = mc.player.getX(); sy = mc.player.getY(); sz = mc.player.getZ();
        tries = 0; lastSend = -100000; t = 0;
        state = next;
    }

    private static boolean moved() {
        Minecraft mc = Minecraft.getInstance();
        double dx = mc.player.getX() - sx, dy = mc.player.getY() - sy, dz = mc.player.getZ() - sz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) > MOVED_BLOCKS;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (state == S.OFF) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) { state = S.OFF; return; }

        if (mc.player.isDeadOrDying()) {
            mc.player.respawn();
            state = S.GO_TRAP; t = 0;
            return;
        }

        t++;
        int delay = Math.max(1, Config.I.tpDelaySeconds) * 20;
        switch (state) {
            case GO_TRAP -> beginHome(S.TP_TRAP);
            case TP_TRAP -> {
                // повторяем /home пока реально не телепортировались (кулдауны, отмена телепорта и т.п.)
                if (t - lastSend >= delay) {
                    if (tries >= 2 && !moved()) { state = S.PICK; t = 0; return; } // уже стоим в трапке
                    cmd("home " + Config.I.trapHome);
                    tries++; lastSend = t;
                }
                if (moved() && t - lastSend >= 10) { state = S.PICK; t = 0; }
            }
            case PICK -> {
                victim = randomName();
                if (victim == null) { msg("§eНет подходящих игроков в табе, жду..."); t = 0; return; }
                cmd("tpahere " + victim);
                msg("§eЖертва: " + victim);
                state = S.WAIT_VICTIM; t = 0;
            }
            case WAIT_VICTIM -> {
                Player p = find(victim);
                if (p != null && mc.player.distanceTo(p) <= 6 && t >= 20) { state = S.GO_TARGET; t = 0; }
                else if (t > Config.I.tpaTimeoutSeconds * 20) { recent.add(victim); state = S.PICK; t = 0; }
            }
            case GO_TARGET -> beginHome(S.TP_TARGET);
            case TP_TARGET -> {
                if (t - lastSend >= delay) {
                    if (tries >= MAX_TRIES) { msg("§c/home " + Config.I.targetHome + " не сработал, начинаю заново"); state = S.GO_TRAP; t = 0; return; }
                    cmd("home " + Config.I.targetHome);
                    tries++; lastSend = t;
                }
                if (moved() && t - lastSend >= 10) { state = S.ATTACK; t = 0; }
            }
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
