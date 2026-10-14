package ru.pic.pic_visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** HUD: watermark pill (user / fps / time / ping) and an "Information" panel (BPS / TPS / XYZ / PING). */
public final class Hud {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Hud() {}

    private static int ping(MinecraftClient mc) {
        ClientPlayNetworkHandler h = mc.getNetworkHandler();
        if (h != null && mc.player != null) {
            PlayerListEntry e = h.getPlayerListEntry(mc.player.getUuid());
            if (e != null) return e.getLatency();
        }
        return 0;
    }

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        Config c = Config.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (!c.hudWatermark && !c.hudInfo) return;

        Window win = mc.getWindow();
        float sf = (float) win.getScaleFactor();
        float u = win.getFramebufferHeight() / 1080f * c.hudScale;

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.scale(1f / sf, 1f / sf, 1f);
        int x = Math.round(18 * u), y = Math.round(18 * u);
        int bottom = y;
        if (c.hudWatermark) bottom = watermark(ctx, mc, x, y, u);
        if (c.hudInfo) info(ctx, mc, x, bottom + Math.round(14 * u), u);
        ms.pop();
    }

    private static int watermark(DrawContext ctx, MinecraftClient mc, int x, int y, float u) {
        Config c = Config.get();
        String user = mc.getSession().getUsername();
        String fps = mc.getCurrentFps() + "fps";
        String time = LocalTime.now().format(TIME);
        String ping = ping(mc) + "ms";
        String[] texts = {user, fps, time, ping};

        float k = 1.65f * u;
        int h = Math.round(54 * u);
        int pad = Math.round(18 * u), iconW = Math.round(20 * u), iconGap = Math.round(9 * u), seg = Math.round(26 * u);
        int logo = Math.round(32 * u);

        int w = pad + logo + seg;
        for (int i = 0; i < 4; i++) {
            w += iconW + iconGap + Math.round(Gfx.tw(texts[i], k, false));
            if (i < 3) w += seg;
        }
        w += pad;

        int r = Math.round(20 * u);
        Gfx.rrect(ctx, x, y, w, h, r, 0xA8101012);
        Gfx.rrectRing(ctx, x, y, w, h, r, Math.max(1, Math.round(u)), 0x26FFFFFF);

        // logo
        int lx = x + pad, ly = y + (h - logo) / 2;
        Gfx.rrect(ctx, lx, ly, logo, logo, Math.round(9 * u), 0x30FFFFFF);
        Gfx.ctext(ctx, "P", lx + logo / 2f, ly + logo / 2f - Gfx.CAP * 1.9f * u / 2f, 1.9f * u, 0xFFE8E8EC, true);
        int tx = x + pad + logo + seg;
        int cy = y + h / 2;
        int iconS = Math.round(8 * u);
        int ic = 0xFF7C7C84, tc = 0xFFC9C9CE;
        for (int i = 0; i < 4; i++) {
            int icx = tx + iconW / 2;
            switch (i) {
                case 0 -> Icons.user(ctx, icx, cy, iconS, ic);
                case 1 -> Icons.fps(ctx, icx, cy, iconS, ic);
                case 2 -> Icons.clock(ctx, icx, cy, iconS, ic);
                default -> Icons.ping(ctx, icx, cy, iconS, ic);
            }
            tx += iconW + iconGap;
            Gfx.textC(ctx, texts[i], tx, cy, k, tc, false);
            tx += Math.round(Gfx.tw(texts[i], k, false)) + seg;
        }
        return y + h;
    }

    private static void info(DrawContext ctx, MinecraftClient mc, int x, int y, float u) {
        String[] labels = {"BPS", "TPS", "XYZ", "PING"};
        String[] values = {
                String.format(Locale.ROOT, "%.2f", Stats.bps),
                Stats.tpsKnown ? String.format(Locale.ROOT, "%.1f", Stats.tps) : "--",
                mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ(),
                ping(mc) + " ms"
        };
        float kt = 1.55f * u, kr = 1.45f * u;
        int pad = Math.round(20 * u), rowH = Math.round(27 * u);
        int titleH = Math.round(56 * u);

        int maxRow = 0;
        for (int i = 0; i < 4; i++) {
            maxRow = Math.max(maxRow, Math.round(Gfx.tw(labels[i], kr, false) + Gfx.tw(values[i], kr, false)));
        }
        int w = Math.max(Math.round(196 * u), pad * 2 + maxRow + Math.round(44 * u));
        int h = titleH + 4 * rowH + Math.round(8 * u);
        int r = Math.round(20 * u);

        Gfx.rrect(ctx, x, y, w, h, r, 0xA8101012);
        Gfx.rrectRing(ctx, x, y, w, h, r, Math.max(1, Math.round(u)), 0x26FFFFFF);

        Gfx.textC(ctx, "Information", x + pad, y + titleH * 0.5f + 1 * u, kt, 0xFFA6A6AE, true);
        Icons.ring(ctx, x + w - pad - Math.round(5 * u), y + Math.round(titleH * 0.5f), Math.round(7 * u), Math.max(2, Math.round(2.4f * u)), 0xFF7C7C84);
        Gfx.circle(ctx, x + w - pad - Math.round(5 * u), y + Math.round(titleH * 0.5f), Math.max(1, Math.round(2.2f * u)), 0xFF7C7C84);

        for (int i = 0; i < 4; i++) {
            float cy = y + titleH + i * rowH + rowH * 0.5f;
            Gfx.textC(ctx, labels[i], x + pad, cy, kr, 0xFF6C6C74, false);
            Gfx.textC(ctx, values[i], x + w - pad - Gfx.tw(values[i], kr, false), cy, kr, 0xFFD2D2D8, false);
        }
    }
}
