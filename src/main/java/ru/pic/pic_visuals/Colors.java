package ru.pic.pic_visuals;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

public final class Colors {
    private Colors() {}

    public static float time() {
        return (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f;
    }

    public static int withAlpha(int c, float a) {
        int al = MathHelper.clamp((int) (a * 255f), 0, 255);
        return (al << 24) | (c & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    public static int mulAlpha(int c, float f) {
        int a = MathHelper.clamp((int) ((c >>> 24) * f), 0, 255);
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        return 0xFF000000 | MathHelper.hsvToRgb(h, MathHelper.clamp(s, 0f, 1f), MathHelper.clamp(v, 0f, 1f));
    }

    public static void toHsb(int c, float[] out) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        out[0] = h;
        out[1] = max == 0 ? 0 : d / max;
        out[2] = max;
    }

    public static int health(float ratio) {
        ratio = MathHelper.clamp(ratio, 0f, 1f);
        return hsv(0.33f * ratio, 0.85f, 1f);
    }

    public static int wave(int a, int b, float t) {
        return lerp(a, b, 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2));
    }

    public static int byMode(Config.ColorMode m, int a, int b, float phase, float hp, float speed) {
        float t = time() * speed;
        return switch (m) {
            case STATIC -> a | 0xFF000000;
            case RAINBOW -> hsv(t * 0.15f + phase, 0.65f, 1f);
            case GRADIENT -> lerp(a | 0xFF000000, b | 0xFF000000, 0.5f + 0.5f * (float) Math.sin(t * 1.6f + phase * 5f));
            case HEALTH -> health(hp);
        };
    }

    public static float hpRatio(PlayerEntity p) {
        return MathHelper.clamp(p.getHealth() / Math.max(1f, p.getMaxHealth()), 0f, 1f);
    }

    public static int playerColor(Config c, PlayerEntity p) {
        if (p.isInvisible() && c.invisibleOwnColor) return c.invisibleColor | 0xFF000000;
        return byMode(c.colorMode, c.colorA, c.colorB, (p.getId() % 16) * 0.06f, hpRatio(p), c.animSpeed);
    }
}
