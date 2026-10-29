package net.aipvp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class ConfigScreen extends Screen {
    private EditBox provider, key, model, toggle, ignore;

    public ConfigScreen() { super(Component.literal("AI PvP Bot")); }

    private EditBox box(int y, String v, int max) {
        EditBox b = new EditBox(this.font, this.width / 2 - 110, y, 220, 18, Component.empty());
        b.setMaxLength(max); b.setValue(v); addRenderableWidget(b); return b;
    }

    @Override
    protected void init() {
        provider = box(32, Config.I.provider, 20);
        key = box(64, Config.I.apiKey, 300);
        key.addFormatter((s, i) -> FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY));
        model = box(96, Config.I.model, 80);
        toggle = box(128, Config.I.toggleKey, 10);
        ignore = box(160, Config.I.ignoreNames, 200);
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> { save(); onClose(); })
                .bounds(this.width / 2 - 110, 188, 220, 20).build());
    }

    private void save() {
        Config.I.provider = provider.getValue().trim().toLowerCase();
        Config.I.apiKey = key.getValue().trim();
        Config.I.model = model.getValue().trim();
        Config.I.toggleKey = toggle.getValue().trim();
        Config.I.ignoreNames = ignore.getValue().trim();
        Config.save();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        super.render(g, mx, my, d);
        int x = this.width / 2 - 110;
        g.drawString(this.font, "AI PvP Bot", x, 8, 0xFFFFFF55);
        g.drawString(this.font, "Провайдер: anthropic или openai", x, 21, 0xFFFFFFFF);
        g.drawString(this.font, "API ключ", x, 53, 0xFFFFFFFF);
        g.drawString(this.font, "Модель", x, 85, 0xFFFFFFFF);
        g.drawString(this.font, "Клавиша вкл/выкл (Z, F6...)", x, 117, 0xFFFFFFFF);
        g.drawString(this.font, "Друзья (через запятую) - не трогать", x, 149, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
