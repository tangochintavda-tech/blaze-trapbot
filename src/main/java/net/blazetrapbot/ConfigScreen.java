package net.blazetrapbot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
    private EditBox trap, target, toggle, delay;

    public ConfigScreen() { super(Component.literal("Blaze Trapbot")); }

    private EditBox box(int y, String value) {
        EditBox b = new EditBox(this.font, this.width / 2 - 100, y, 200, 20, Component.empty());
        b.setMaxLength(64);
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        trap = box(40, Config.I.trapHome);
        target = box(78, Config.I.targetHome);
        toggle = box(116, Config.I.toggleKey);
        delay = box(154, String.valueOf(Config.I.tpDelaySeconds));
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> { save(); onClose(); })
                .bounds(this.width / 2 - 100, 188, 200, 20).build());
    }

    private void save() {
        Config.I.trapHome = trap.getValue().replace("/home", "").trim();
        Config.I.targetHome = target.getValue().replace("/home", "").trim();
        Config.I.toggleKey = toggle.getValue().trim();
        try { Config.I.tpDelaySeconds = Math.max(1, Integer.parseInt(delay.getValue().trim())); } catch (Exception ignored) {}
        Config.save();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        super.render(g, mx, my, d);
        int x = this.width / 2 - 100;
        g.drawString(this.font, "Blaze Trapbot", x, 12, 0xFFFFFF55);
        g.drawString(this.font, "/home трапки (внутри)", x, 29, 0xFFFFFFFF);
        g.drawString(this.font, "/home таргета (место убийства)", x, 67, 0xFFFFFFFF);
        g.drawString(this.font, "Клавиша вкл/выкл (напр. Z, F6)", x, 105, 0xFFFFFFFF);
        g.drawString(this.font, "Задержка после /home (сек)", x, 143, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
