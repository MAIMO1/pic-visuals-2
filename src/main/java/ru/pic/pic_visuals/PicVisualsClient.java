package ru.pic.pic_visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class PicVisualsClient implements ClientModInitializer {
    public static KeyBinding OPEN_GUI;

    @Override
    public void onInitializeClient() {
        Config.load();

        OPEN_GUI = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pic_visuals.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.pic_visuals"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Stats.tick(mc);
            while (OPEN_GUI.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) mc.setScreen(new MenuScreen());
            }
        });

        WorldRenderEvents.END.register(EspRenderer::captureProjection);

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            Gfx.ga = 1f;
            Gfx.clearClip();
            EspRenderer.render(ctx, tickCounter);
            Hud.render(ctx, tickCounter);
        });
    }
}
