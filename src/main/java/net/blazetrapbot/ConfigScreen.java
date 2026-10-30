package net.blazetrapbot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
    private EditBox trap, target, ignore, toggle, delay;

    public ConfigScreen() { super(Component.literal("Blaze Trapbot")); }

    private EditBox box(int y, String value, int max) {
        EditBox b = new EditBox(this.font, this.width / 2 - 100, y, 200, 18, Component.empty());
        b.setMaxLength(max);
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        trap = box(30, Config.I.trapHome, 64);
        target = box(60, Config.I.targetHome, 64);
        ignore = box(90, Config.I.ignoreNames == null ? "" : Config.I.ignoreNames, 400);
        toggle = box(120, Config.I.toggleKey, 10);
        delay = box(150, String.valueOf(Config.I.tpDelaySeconds), 3);
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> { save(); onClose(); })
                .bounds(this.width / 2 - 100, 180, 200, 20).build());
    }

    private void save() {
        Config.I.trapHome = trap.getValue().replace("/home", "").trim();
        Config.I.targetHome = target.getValue().replace("/home", "").trim();
        Config.I.ignoreNames = ignore.getValue().trim();
        Config.I.toggleKey = toggle.getValue().trim();
        try { Config.I.tpDelaySeconds = Math.max(1, Integer.parseInt(delay.getValue().trim())); } catch (Exception ignored) {}
        Config.save();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        super.render(g, mx, my, d);
        int x = this.width / 2 - 100;
        g.drawString(this.font, "Blaze Trapbot", x, 6, 0xFFFFFF55);
        g.drawString(this.font, "/home трапки (внутри)", x, 19, 0xFFFFFFFF);
        g.drawString(this.font, "/home таргета (место убийства)", x, 49, 0xFFFFFFFF);
        g.drawString(this.font, "Не трогать (ники через запятую)", x, 79, 0xFFFFFFFF);
        g.drawString(this.font, "Клавиша вкл/выкл (Z, F6...)", x, 109, 0xFFFFFFFF);
        g.drawString(this.font, "Пауза/повтор /home (сек)", x, 139, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
