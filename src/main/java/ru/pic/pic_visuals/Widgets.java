package ru.pic.pic_visuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Setting rows used on the module pages. All sizes are in 1080p "design pixels". */
public final class Widgets {
    private Widgets() {}

    static int accA() { return Config.get().colorA | 0xFF000000; }
    static int accB() { return Config.get().colorB | 0xFF000000; }

    static float approach(float cur, float target) {
        return cur + (target - cur) * (1f - (float) Math.exp(-Gfx.dt * 16f));
    }

    public static void drawSwitch(DrawContext ctx, int x, int y, int w, int h, float anim) {
        int track = Colors.lerp(0xFF2E2E33, 0xFFD4D4D8, anim);
        Gfx.rrect(ctx, x, y, w, h, h / 2, track);
        int kd = h - 8;
        int kx = x + 4 + Math.round(anim * (w - kd - 8));
        int knob = Colors.lerp(0xFF8E8E95, 0xFFFFFFFF, anim);
        Gfx.rrect(ctx, kx, y + 4, kd, kd, kd / 2, knob);
    }

    static void rowBg(DrawContext ctx, int x, int y, int w, int h, boolean hv) {
        Gfx.rrect(ctx, x, y, w, h, 20, hv ? 0xFF212125 : 0xFF1C1C20);
        Gfx.rrectRing(ctx, x, y, w, h, 20, 2, 0xFF2B2B30);
    }

    public abstract static class W {
        public int x, y, w;
        public abstract int height();
        public abstract void render(DrawContext ctx, int mx, int my);
        public boolean click(double mx, double my, int btn) { return false; }
        public void drag(double mx, double my) { }
        public void release() { }
        public boolean hover(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + height();
        }
    }

    public static class Header extends W {
        private final String text;
        public Header(String t) { text = t.toUpperCase(Locale.ROOT); }
        @Override public int height() { return 46; }
        @Override public void render(DrawContext ctx, int mx, int my) {
            Gfx.textC(ctx, text, x + 8, y + 28, 1.45f, 0xFF7C7C86, true);
            int tw = Math.round(Gfx.tw(text, 1.45f, true));
            Gfx.fill(ctx, x + tw + 24, y + 28, x + w, y + 29, 0xFF26262B);
        }
    }

    public static class Note extends W {
        private final String text;
        public Note(String t) { text = t; }
        @Override public int height() { return 40; }
        @Override public void render(DrawContext ctx, int mx, int my) {
            Gfx.textC(ctx, text, x + 8, y + 16, 1.5f, 0xFF6C6C76, false);
        }
    }

    public static class Toggle extends W {
        private final String label;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private float anim = -1;
        public Toggle(String l, BooleanSupplier g, Consumer<Boolean> s) { label = l; get = g; set = s; }
        @Override public int height() { return 68; }
        @Override public void render(DrawContext ctx, int mx, int my) {
            float target = get.getAsBoolean() ? 1f : 0f;
            if (anim < 0) anim = target;
            anim = approach(anim, target);
            rowBg(ctx, x, y, w, 62, hover(mx, my));
            Gfx.textC(ctx, label, x + 28, y + 31, 1.9f, 0xFFE8E8EC, false);
            drawSwitch(ctx, x + w - 28 - 62, y + 15, 62, 32, anim);
        }
        @Override public boolean click(double mx, double my, int btn) {
            if (btn != 0) return false;
            set.accept(!get.getAsBoolean());
            return true;
        }
    }

    public static class Slider extends W {
        private final String label, fmt;
        private final float min, max, step;
        private final Supplier<Float> get;
        private final Consumer<Float> set;
        private float shown = Float.NaN;
        private boolean dragging;
        public Slider(String l, float min, float max, float step, String fmt, Supplier<Float> g, Consumer<Float> s) {
            label = l; this.min = min; this.max = max; this.step = step; this.fmt = fmt; get = g; set = s;
        }
        @Override public int height() { return 96; }
        private int tx() { return x + 28; }
        private int tw() { return w - 56; }
        @Override public void render(DrawContext ctx, int mx, int my) {
            float v = get.get();
            float frac = MathHelper.clamp((v - min) / (max - min), 0f, 1f);
            if (Float.isNaN(shown)) shown = frac;
            shown = approach(shown, frac);
            rowBg(ctx, x, y, w, 90, hover(mx, my) || dragging);
            Gfx.textC(ctx, label, x + 28, y + 28, 1.9f, 0xFFE8E8EC, false);
            String vs = String.format(Locale.ROOT, fmt, v);
            Gfx.textC(ctx, vs, x + w - 28 - Gfx.tw(vs, 1.8f, false), y + 28, 1.8f, 0xFFB4B4BE, false);
            int ty = y + 62;
            Gfx.rrect(ctx, tx(), ty, tw(), 10, 5, 0xFF2A2A30);
            int fw = Math.max(10, Math.round(tw() * shown));
            Gfx.rrect(ctx, tx(), ty, fw, 10, 5, accA());
            Gfx.hgrad(ctx, tx() + 5, ty, Math.max(0, fw - 10), 10, accA(), Colors.lerp(accA(), accB(), shown));
            Gfx.circle(ctx, tx() + fw - 5, ty + 5, 13, 0x40000000);
            Gfx.circle(ctx, tx() + fw - 5, ty + 5, 11, 0xFFFFFFFF);
        }
        private void update(double mx) {
            float f = MathHelper.clamp((float) ((mx - tx()) / tw()), 0f, 1f);
            float v = min + f * (max - min);
            v = Math.round(v / step) * step;
            set.accept(MathHelper.clamp(v, min, max));
        }
        @Override public boolean click(double mx, double my, int btn) {
            if (btn != 0) return false;
            dragging = true;
            update(mx);
            return true;
        }
        @Override public void drag(double mx, double my) { if (dragging) update(mx); }
        @Override public void release() { dragging = false; }
    }

    public static class Cycle<E extends Enum<E>> extends W {
        private final String label;
        private final E[] vals;
        private final Supplier<E> get;
        private final Consumer<E> set;
        public Cycle(String l, E[] vals, Supplier<E> g, Consumer<E> s) { label = l; this.vals = vals; get = g; set = s; }
        @Override public int height() { return 68; }
        @Override public void render(DrawContext ctx, int mx, int my) {
            rowBg(ctx, x, y, w, 62, hover(mx, my));
            Gfx.textC(ctx, label, x + 28, y + 31, 1.9f, 0xFFE8E8EC, false);
            String v = get.get().toString();
            int vw = Math.round(Gfx.tw(v, 1.8f, false)) + 96;
            int px = x + w - 20 - vw;
            Gfx.rrect(ctx, px, y + 10, vw, 42, 14, 0xFF2A2A30);
            Gfx.textC(ctx, "<", px + 20, y + 31, 1.8f, 0xFF7C7C86, true);
            Gfx.textC(ctx, ">", px + vw - 20 - Gfx.tw(">", 1.8f, true), y + 31, 1.8f, 0xFF7C7C86, true);
            Gfx.ctext(ctx, v, px + vw / 2f, y + 31 - Gfx.CAP * 1.8f / 2f, 1.8f, 0xFFFFFFFF, false);
        }
        @Override public boolean click(double mx, double my, int btn) {
            int i = get.get().ordinal();
            if (btn == 0) i = (i + 1) % vals.length;
            else if (btn == 1) i = (i + vals.length - 1) % vals.length;
            else return false;
            set.accept(vals[i]);
            return true;
        }
    }

    public static class ColorW extends W {
        private final String label;
        private final IntSupplier get;
        private final IntConsumer set;
        private boolean expanded;
        private float exp;
        private final float[] hsb = new float[3];
        private int lastSet;
        private int drag = -1;
        public ColorW(String l, IntSupplier g, IntConsumer s) {
            label = l; get = g; set = s;
            lastSet = g.getAsInt() | 0xFF000000;
            Colors.toHsb(lastSet, hsb);
        }
        @Override public int height() { return 68 + Math.round(150 * exp); }
        private int sx() { return x + 28; }
        private int sw() { return w - 56; }
        private int stripY(int i) { return y + 80 + i * 46; }
        @Override public void render(DrawContext ctx, int mx, int my) {
            int cur = get.getAsInt() | 0xFF000000;
            if (drag < 0 && cur != lastSet) { Colors.toHsb(cur, hsb); lastSet = cur; }
            exp = approach(exp, expanded ? 1f : 0f);
            if (Math.abs(exp - (expanded ? 1f : 0f)) < 0.01f) exp = expanded ? 1f : 0f;
            rowBg(ctx, x, y, w, height() - 6, hover(mx, my) && my < y + 62);
            Gfx.textC(ctx, label, x + 28, y + 31, 1.9f, 0xFFE8E8EC, false);
            Gfx.rrect(ctx, x + w - 28 - 72, y + 14, 72, 34, 12, 0xFF3A3A40);
            Gfx.rrect(ctx, x + w - 28 - 70, y + 16, 68, 30, 10, cur);
            if (exp > 0.05f) {
                float old = Gfx.ga;
                Gfx.ga = old * exp;
                for (int i = 0; i < 3; i++) {
                    int sy = stripY(i);
                    Gfx.rrect(ctx, sx() - 2, sy - 2, sw() + 4, 32, 12, 0xFF000000);
                    Gfx.rrect(ctx, sx(), sy, sw(), 28, 10, 0xFF202024);
                    for (int px = 12; px < sw() - 12; px += 3) {
                        float f = px / (float) Math.max(1, sw() - 1);
                        int col = switch (i) {
                            case 0 -> Colors.hsv(f, 1f, 1f);
                            case 1 -> Colors.hsv(hsb[0], f, 1f);
                            default -> Colors.hsv(hsb[0], hsb[1], f);
                        };
                        Gfx.fill(ctx, sx() + px, sy + 2, Math.min(sx() + px + 3, sx() + sw() - 12), sy + 26, col);
                    }
                    int kx = sx() + 12 + Math.round(hsb[i] * (sw() - 25));
                    Gfx.rrect(ctx, kx - 5, sy - 5, 10, 38, 5, 0xFF000000);
                    Gfx.rrect(ctx, kx - 3, sy - 3, 6, 34, 3, 0xFFFFFFFF);
                }
                Gfx.ga = old;
            }
        }
        private void update(double mx) {
            float f = MathHelper.clamp((float) ((mx - sx() - 12) / (sw() - 25)), 0f, 1f);
            hsb[drag] = f;
            int c = Colors.hsv(hsb[0], hsb[1], hsb[2]);
            set.accept(c);
            lastSet = c | 0xFF000000;
        }
        @Override public boolean click(double mx, double my, int btn) {
            if (btn != 0) return false;
            if (my < y + 62) { expanded = !expanded; return true; }
            if (expanded) {
                for (int i = 0; i < 3; i++) {
                    int sy = stripY(i);
                    if (my >= sy - 6 && my < sy + 34) { drag = i; update(mx); return true; }
                }
            }
            return false;
        }
        @Override public void drag(double mx, double my) { if (drag >= 0) update(mx); }
        @Override public void release() { drag = -1; }
    }

    public static class Presets extends W {
        private static final String[] NAMES = {"Mint", "Violet", "Ice", "Sunset", "Crimson"};
        private static final int[][] COLS = {
                {0xFFC8F7D4, 0xFFFFFFFF, 0xFFFF8CC0},
                {0xFF8A5CFF, 0xFF35D6FF, 0xFFFF4D9D},
                {0xFF9FE8FF, 0xFF4D8DFF, 0xFFFFE066},
                {0xFFFF7A45, 0xFFFF3D8A, 0xFF5CE1FF},
                {0xFFFF3B4A, 0xFFB000FF, 0xFF4DFFB8}
        };
        @Override public int height() { return 100; }
        private int bw() { return (w - 40 - (NAMES.length - 1) * 14) / NAMES.length; }
        @Override public void render(DrawContext ctx, int mx, int my) {
            int bw = bw();
            for (int i = 0; i < NAMES.length; i++) {
                int bx = x + 20 + i * (bw + 14);
                boolean hv = mx >= bx && mx < bx + bw && my >= y + 8 && my < y + 80;
                Gfx.rrect(ctx, bx, y + 8, bw, 72, 20, hv ? 0xFFFFFFFF : 0xFF000000);
                Gfx.rrect(ctx, bx + 2, y + 10, bw - 4, 68, 18, 0xFF000000);
                Gfx.rrect(ctx, bx + 3, y + 11, bw - 6, 66, 17, COLS[i][0]);
                for (int k = 0; k < 66; k += 2) {
                    // diagonal-ish tint: blend primary -> secondary from top to bottom
                    Gfx.fill(ctx, bx + 3 + (k < 17 ? 17 - k : 0), y + 11 + k, bx + bw - 3 - (k < 17 ? 17 - k : 0), y + 13 + k,
                            Colors.lerp(COLS[i][0], COLS[i][1], k / 66f));
                }
                Gfx.ctext(ctx, NAMES[i], bx + bw / 2f, y + 44 - Gfx.CAP * 1.7f / 2f, 1.7f, 0xFF101012, true);
            }
        }
        @Override public boolean click(double mx, double my, int btn) {
            if (btn != 0 || my < y + 8 || my >= y + 80) return false;
            int bw = bw();
            for (int i = 0; i < NAMES.length; i++) {
                int bx = x + 20 + i * (bw + 14);
                if (mx >= bx && mx < bx + bw) {
                    Config c = Config.get();
                    c.colorA = COLS[i][0];
                    c.colorB = COLS[i][1];
                    c.invisibleColor = COLS[i][2];
                    return true;
                }
            }
            return false;
        }
    }
}
