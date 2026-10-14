package ru.pic.pic_visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/** Small 2D toolkit: anti-aliased rounded rects, smooth TTF text, clipping, lines. */
public final class Gfx {
    /** Global alpha multiplier (menu fade-in). */
    public static float ga = 1f;
    public static float dt = 0.016f;

    public static boolean clip = false;
    public static int clipY0, clipY1;

    private static final Identifier F_REG = Identifier.of("pic_visuals", "ui");
    private static final Identifier F_BOLD = Identifier.of("pic_visuals", "ui_bold");
    private static final Map<String, Text> REG = new HashMap<>();
    private static final Map<String, Text> BOLD = new HashMap<>();

    /** Height of a capital letter in font units (multiply by text scale). */
    public static final float CAP = 7f;

    private Gfx() {}

    public static void setClip(int y0, int y1) { clip = true; clipY0 = y0; clipY1 = y1; }
    public static void clearClip() { clip = false; }

    public static int mul(int c) {
        if (ga >= 0.999f) return c;
        int a = (int) ((c >>> 24) * ga);
        return (a << 24) | (c & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ fills

    public static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int c) {
        int m = mul(c);
        if ((m >>> 24) == 0) return;
        if (clip) {
            if (y1 < clipY0) y1 = clipY0;
            if (y2 > clipY1) y2 = clipY1;
        }
        if (x2 <= x1 || y2 <= y1) return;
        ctx.fill(x1, y1, x2, y2, m);
    }

    public static void grad(DrawContext ctx, int x1, int y1, int x2, int y2, int c1, int c2) {
        if (x2 <= x1 || y2 <= y1) return;
        int ny1 = y1, ny2 = y2, n1 = c1, n2 = c2;
        if (clip) {
            float h = y2 - y1;
            if (y1 < clipY0) { ny1 = clipY0; n1 = Colors.lerp(c1, c2, (clipY0 - y1) / h); }
            if (y2 > clipY1) { ny2 = clipY1; n2 = Colors.lerp(c1, c2, (clipY1 - y1) / h); }
            if (ny2 <= ny1) return;
        }
        ctx.fillGradient(x1, ny1, x2, ny2, mul(n1), mul(n2));
    }

    public static void hgrad(DrawContext ctx, int x, int y, int w, int h, int c1, int c2) {
        int step = 2;
        for (int i = 0; i < w; i += step) {
            float t = w <= 1 ? 0 : i / (float) (w - 1);
            fill(ctx, x + i, y, Math.min(x + i + step, x + w), y + h, Colors.lerp(c1, c2, t));
        }
    }

    private static int inset(int r, float yc) {
        float dy = r - yc;
        return Math.round(r - (float) Math.sqrt(Math.max(0f, r * (float) r - dy * dy)));
    }

    /** Smooth rounded rectangle (with a soft anti-aliasing fringe on the corners). */
    public static void rrect(DrawContext ctx, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 1) { fill(ctx, x, y, x + w, y + h, c); return; }
        fill(ctx, x, y + r, x + w, y + h - r, c);
        int aa = Colors.mulAlpha(c, 0.38f);
        int prev = r;
        for (int i = 0; i < r; i++) {
            int in = inset(r, i + 0.5f);
            fill(ctx, x + in, y + i, x + w - in, y + i + 1, c);
            fill(ctx, x + in, y + h - 1 - i, x + w - in, y + h - i, c);
            if (in > 0) {
                int f = Math.max(1, Math.min(3, prev - in));
                fill(ctx, x + in - f, y + i, x + in, y + i + 1, aa);
                fill(ctx, x + w - in, y + i, x + w - in + f, y + i + 1, aa);
                fill(ctx, x + in - f, y + h - 1 - i, x + in, y + h - i, aa);
                fill(ctx, x + w - in, y + h - 1 - i, x + w - in + f, y + h - i, aa);
            }
            prev = in;
        }
    }

    /** Rounded outline only (border of width bw). */
    public static void rrectRing(DrawContext ctx, int x, int y, int w, int h, int r, int bw, int c) {
        if (w <= 0 || h <= 0 || bw <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 1) {
            fill(ctx, x, y, x + w, y + bw, c);
            fill(ctx, x, y + h - bw, x + w, y + h, c);
            fill(ctx, x, y + bw, x + bw, y + h - bw, c);
            fill(ctx, x + w - bw, y + bw, x + w, y + h - bw, c);
            return;
        }
        int ri = r - bw;
        for (int i = 0; i < r; i++) {
            int oIn = inset(r, i + 0.5f);
            if (i < bw) {
                fill(ctx, x + oIn, y + i, x + w - oIn, y + i + 1, c);
                fill(ctx, x + oIn, y + h - 1 - i, x + w - oIn, y + h - i, c);
            } else {
                int iL = bw + (ri > 0 ? inset(ri, (i - bw) + 0.5f) : 0);
                fill(ctx, x + oIn, y + i, x + iL, y + i + 1, c);
                fill(ctx, x + w - iL, y + i, x + w - oIn, y + i + 1, c);
                fill(ctx, x + oIn, y + h - 1 - i, x + iL, y + h - i, c);
                fill(ctx, x + w - iL, y + h - 1 - i, x + w - oIn, y + h - i, c);
            }
        }
        fill(ctx, x, y + r, x + bw, y + h - r, c);
        fill(ctx, x + w - bw, y + r, x + w, y + h - r, c);
    }

    public static void circle(DrawContext ctx, int cx, int cy, int r, int c) {
        rrect(ctx, cx - r, cy - r, r * 2, r * 2, r, c);
    }

    // ------------------------------------------------------------------ text

    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    private static Text txt(String s, boolean bold) {
        Map<String, Text> m = bold ? BOLD : REG;
        if (m.size() > 700) m.clear();
        Text t = m.get(s);
        if (t == null) {
            t = Text.literal(s).setStyle(Style.EMPTY.withFont(bold ? F_BOLD : F_REG));
            m.put(s, t);
        }
        return t;
    }

    public static float tw(String s, float k, boolean bold) {
        return tr().getWidth(txt(s, bold)) * k;
    }

    /** Draws text with its top at y. k = scale (1 = ~7px capital letters). */
    public static void text(DrawContext ctx, String s, float x, float y, float k, int c, boolean bold) {
        int m = mul(c);
        if ((m >>> 24) < 8) return;
        if (clip && (y < clipY0 || y + 9.5f * k > clipY1)) return;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x, y, 0f);
        ms.scale(k, k, 1f);
        ctx.drawText(tr(), txt(s, bold), 0, 0, m, false);
        ms.pop();
    }

    public static void ctext(DrawContext ctx, String s, float cx, float y, float k, int c, boolean bold) {
        text(ctx, s, cx - tw(s, k, bold) / 2f, y, k, c, bold);
    }

    /** Text vertically centred on cy (by capital height). */
    public static void textC(DrawContext ctx, String s, float x, float cy, float k, int c, boolean bold) {
        text(ctx, s, x, cy - CAP * k / 2f, k, c, bold);
    }

    // ------------------------------------------------------------------ float helpers (ESP)

    public static void rectF(DrawContext ctx, float x, float y, float w, float h, int c) {
        if (w <= 0 || h <= 0) return;
        int m = mul(c);
        if ((m >>> 24) == 0) return;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x, y, 0f);
        ms.scale(w, h, 1f);
        ctx.fill(0, 0, 1, 1, m);
        ms.pop();
    }

    public static void gradF(DrawContext ctx, float x, float y, float w, float h, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x, y, 0f);
        ms.scale(w, h, 1f);
        ctx.fillGradient(0, 0, 1, 1, mul(c1), mul(c2));
        ms.pop();
    }

    public static void lineF(DrawContext ctx, float x1, float y1, float x2, float y2, float th, int c) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        int m = mul(c);
        if ((m >>> 24) == 0) return;
        float ang = (float) Math.atan2(dy, dx);
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x1, y1, 0f);
        ms.multiply(new Quaternionf().rotateZ(ang));
        ms.translate(0f, -th / 2f, 0f);
        ms.scale(len, th, 1f);
        ctx.fill(0, 0, 1, 1, m);
        ms.pop();
    }
}
