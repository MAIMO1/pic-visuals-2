package ru.pic.pic_visuals;

import net.minecraft.client.gui.DrawContext;

/** Tiny vector icons built from rounded rects, so they stay smooth at any size. */
public final class Icons {
    private Icons() {}

    public static void ring(DrawContext ctx, int cx, int cy, int r, int bw, int col) {
        Gfx.rrectRing(ctx, cx - r, cy - r, r * 2, r * 2, r, bw, col);
    }

    public static void gear(DrawContext ctx, int cx, int cy, int r, int col) {
        if (Gfx.clip && (cy - r < Gfx.clipY0 || cy + r > Gfx.clipY1)) return;
        float body = r * 0.76f;
        ring(ctx, cx, cy, Math.round(body), Math.max(2, Math.round(r * 0.34f)), col);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4.0;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            Gfx.lineF(ctx, cx + c * body * 0.85f, cy + s * body * 0.85f, cx + c * r, cy + s * r, r * 0.42f, col);
        }
    }

    /** Eye: s = half width. */
    public static void eye(DrawContext ctx, int cx, int cy, int s, int col) {
        int w = Math.round(s * 2f), h = Math.round(s * 1.15f);
        Gfx.rrectRing(ctx, cx - w / 2, cy - h / 2, w, h, h / 2, Math.max(2, s / 6), col);
        Gfx.circle(ctx, cx, cy, Math.max(2, Math.round(s * 0.24f)), col);
    }

    public static void hud(DrawContext ctx, int cx, int cy, int s, int col) {
        int w = Math.round(s * 1.9f), h = Math.round(s * 1.4f);
        int bw = Math.max(2, s / 6);
        Gfx.rrectRing(ctx, cx - w / 2, cy - h / 2, w, h, Math.round(s * 0.3f), bw, col);
        Gfx.fill(ctx, cx - w / 2 + bw * 2, cy - h / 2 + bw * 2, cx + 1, cy - h / 2 + bw * 3, col);
        Gfx.fill(ctx, cx - w / 2 + bw * 2, cy - h / 2 + bw * 4, cx - w / 2 + bw * 6, cy - h / 2 + bw * 5, col);
    }

    public static void theme(DrawContext ctx, int cx, int cy, int s, int col) {
        ring(ctx, cx, cy, s, Math.max(2, s / 6), col);
        int d = Math.max(2, s / 5);
        Gfx.circle(ctx, cx - s / 3, cy - s / 5, d, col);
        Gfx.circle(ctx, cx + s / 4, cy - s / 3, d, col);
        Gfx.circle(ctx, cx + s / 3, cy + s / 5, d, col);
    }

    public static void user(DrawContext ctx, int cx, int cy, int s, int col) {
        Gfx.circle(ctx, cx, cy - Math.round(s * 0.34f), Math.max(2, Math.round(s * 0.3f)), col);
        Gfx.rrect(ctx, cx - Math.round(s * 0.62f), cy + Math.round(s * 0.1f), Math.round(s * 1.24f), Math.round(s * 0.62f),
                Math.round(s * 0.3f), col);
    }

    public static void fps(DrawContext ctx, int cx, int cy, int s, int col) {
        int bw = Math.max(2, Math.round(s * 0.32f));
        for (int i = 0; i < 3; i++) {
            int h = Math.round(s * (0.6f + 0.45f * i));
            Gfx.rrect(ctx, cx - Math.round(s * 0.62f) + i * (bw + Math.round(s * 0.2f)), cy + Math.round(s * 0.7f) - h, bw, h, bw / 2, col);
        }
    }

    public static void clock(DrawContext ctx, int cx, int cy, int s, int col) {
        ring(ctx, cx, cy, s, Math.max(2, s / 5), col);
        Gfx.fill(ctx, cx, cy - Math.round(s * 0.6f), cx + Math.max(1, s / 6), cy + 1, col);
        Gfx.fill(ctx, cx, cy, cx + Math.round(s * 0.5f), cy + Math.max(1, s / 6), col);
    }

    public static void ping(DrawContext ctx, int cx, int cy, int s, int col) {
        int bw = Math.max(2, Math.round(s * 0.3f));
        int gap = Math.max(1, Math.round(s * 0.18f));
        for (int i = 0; i < 4; i++) {
            int h = Math.round(s * (0.4f + 0.38f * i));
            Gfx.rrect(ctx, cx - Math.round(s * 0.8f) + i * (bw + gap), cy + Math.round(s * 0.7f) - h, bw, h, bw / 2, col);
        }
    }

    public static void search(DrawContext ctx, int cx, int cy, int s, int col) {
        ring(ctx, cx - s / 6, cy - s / 6, Math.round(s * 0.62f), Math.max(2, s / 6), col);
        Gfx.lineF(ctx, cx + s * 0.28f, cy + s * 0.28f, cx + s * 0.7f, cy + s * 0.7f, Math.max(2f, s / 5f), col);
    }

    public static void dots(DrawContext ctx, int cx, int cy, int s, int col) {
        int d = Math.max(2, s / 5);
        Gfx.circle(ctx, cx - s / 2, cy - s / 2, d, col);
        Gfx.circle(ctx, cx + s / 2, cy - s / 2, d, col);
        Gfx.circle(ctx, cx - s / 2, cy + s / 2, d, col);
        Gfx.circle(ctx, cx + s / 2, cy + s / 2, d, col);
    }
}
