package ru.pic.pic_visuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Player ESP. Each player's bounding box is projected to the screen and drawn as a glass-like 3D box
 * (translucent faces + glowing edges) over everything, so it shows through walls (invisible players too).
 */
public final class EspRenderer {
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static final Quaternionf QUAT = new Quaternionf();
    private static final float E = 0.05f;
    private static final float[] R = new float[16 * 4];
    private static Vec3d camPos = Vec3d.ZERO;
    private static boolean projReady = false;

    private static final int[][] FACES = {{0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}};
    private static final float[] FACE_MUL = {0.80f, 0.80f, 0.65f, 1.00f, 0.90f, 0.90f};

    /** Screen-space geometry of one box. */
    public static final class Shape {
        final float[] edges = new float[12 * 4];
        final boolean[] eOk = new boolean[12];
        final float[][] faces = new float[6][12];
        final int[] fN = new int[6];
        float x1, y1, x2, y2;
        boolean valid;
    }

    private static final class Target {
        AbstractClientPlayerEntity p;
        Shape shape = new Shape();
        int color;
        double dist;
    }

    private static final List<Target> TARGETS = new ArrayList<>();

    private EspRenderer() {}

    public static void captureProjection(WorldRenderContext ctx) {
        PROJ.set(ctx.projectionMatrix());
        projReady = true;
    }

    private static void toClip(double x, double y, double z, float[] out) {
        TMP.set((float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z), 1f);
        VIEW.transform(TMP);
        PROJ.transform(TMP);
        out[0] = TMP.x; out[1] = TMP.y; out[2] = TMP.z; out[3] = TMP.w;
    }

    // ------------------------------------------------------------------ main render

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        Config c = Config.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.espEnabled || !projReady || mc.world == null || mc.player == null || mc.options.hudHidden) return;

        Camera cam = mc.gameRenderer.getCamera();
        camPos = cam.getPos();
        VIEW.identity().rotation(cam.getRotation().conjugate(QUAT));

        float td = tc.getTickDelta(true);
        Window win = mc.getWindow();
        int fbW = win.getFramebufferWidth(), fbH = win.getFramebufferHeight();
        float sf = (float) win.getScaleFactor();
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();

        TARGETS.clear();
        float[][] cl = new float[8][4];
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && (!c.showSelf || firstPerson)) continue;
            if (!p.isAlive() || p.isSpectator()) continue;
            if (p.isInvisible() && !c.showInvisible) continue;
            double dist = mc.player.distanceTo(p);
            if (dist > c.maxDistance) continue;

            Vec3d lp = p.getLerpedPos(td);
            Box b = p.getBoundingBox().offset(lp.x - p.getX(), lp.y - p.getY(), lp.z - p.getZ());
            for (int i = 0; i < 8; i++) {
                double x = (i & 1) == 0 ? b.minX : b.maxX;
                double y = (i & 2) == 0 ? b.minY : b.maxY;
                double z = (i & 4) == 0 ? b.minZ : b.maxZ;
                toClip(x, y, z, cl[i]);
            }
            Target t = new Target();
            if (!build(t.shape, cl, fbW, fbH)) continue;
            Shape s = t.shape;
            if (s.x2 < 0 || s.x1 > fbW || s.y2 < 0 || s.y1 > fbH) continue;
            t.p = p;
            t.dist = dist;
            t.color = Colors.playerColor(c, p);
            TARGETS.add(t);
        }
        if (TARGETS.isEmpty()) return;

        float u = fbH / 1080f;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.scale(1f / sf, 1f / sf, 1f);
        for (Target t : TARGETS) {
            Shape s = t.shape;
            if (c.espStyle == Config.EspStyle.BOX || c.espStyle == Config.EspStyle.CORNERS) {
                drawStyle2D(ctx, c, s.x1, s.y1, s.x2, s.y2, t.color, 1f);
            } else {
                drawShape(ctx, c, s, t.color, 1f);
            }
            if (c.healthBar) healthBar(ctx, c, s.x1, s.y1, s.y2, Colors.hpRatio(t.p), 1f);
            if (c.tracers) {
                float sx = fbW / 2f, sy = fbH;
                float ex = (s.x1 + s.x2) / 2f, ey = s.y2;
                Gfx.lineF(ctx, sx, sy, ex, ey, c.espThickness + 2f, 0x90000000);
                Gfx.lineF(ctx, sx, sy, ex, ey, c.espThickness, Colors.withAlpha(t.color, 0.85f));
            }
        }
        for (Target t : TARGETS) {
            Shape s = t.shape;
            float cx = (s.x1 + s.x2) * 0.5f;
            if (c.nametag) {
                drawTag(ctx, cx, s.y1 - 10f * u, t.p.getName().getString(), t.p.getHealth() + t.p.getAbsorptionAmount(),
                        Colors.hpRatio(t.p), (int) t.dist, t.p.isInvisible(), t.color, c, u);
            }
            if (c.showItem) {
                ItemStack st = t.p.getMainHandStack();
                if (!st.isEmpty()) {
                    Gfx.ctext(ctx, st.getName().getString(), cx, s.y2 + 8f * u, 1.45f * u, 0xFFE4E4EA, false);
                }
            }
        }
        ms.pop();
    }

    // ------------------------------------------------------------------ geometry building

    private static boolean build(Shape s, float[][] cl, int fbW, int fbH) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean any = false;
        int ei = 0;
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) != 0) continue;
                int j = i | bit;
                boolean ok = clipEdge(cl[i], cl[j], s.edges, ei * 4, fbW, fbH);
                s.eOk[ei] = ok;
                if (ok) {
                    any = true;
                    for (int k = 0; k < 2; k++) {
                        float x = s.edges[ei * 4 + k * 2], y = s.edges[ei * 4 + k * 2 + 1];
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                }
                ei++;
            }
        }
        for (int f = 0; f < 6; f++) s.fN[f] = clipFace(cl, FACES[f], s.faces[f], fbW, fbH);
        s.valid = any;
        s.x1 = minX; s.y1 = minY; s.x2 = maxX; s.y2 = maxY;
        return any;
    }

    private static boolean clipEdge(float[] a, float[] b, float[] out, int o, int fbW, int fbH) {
        float ax = a[0], ay = a[1], aw = a[3], bx = b[0], by = b[1], bw = b[3];
        if (aw < E && bw < E) return false;
        if (aw < E) {
            float t = (E - aw) / (bw - aw);
            ax = ax + (bx - ax) * t;
            ay = ay + (by - ay) * t;
            aw = E;
        } else if (bw < E) {
            float t = (E - aw) / (bw - aw);
            bx = ax + (bx - ax) * t;
            by = ay + (by - ay) * t;
            bw = E;
        }
        out[o] = (ax / aw * 0.5f + 0.5f) * fbW;
        out[o + 1] = (1f - (ay / aw * 0.5f + 0.5f)) * fbH;
        out[o + 2] = (bx / bw * 0.5f + 0.5f) * fbW;
        out[o + 3] = (1f - (by / bw * 0.5f + 0.5f)) * fbH;
        return true;
    }

    private static int clipFace(float[][] cl, int[] idx, float[] out, int fbW, int fbH) {
        float[][] res = new float[8][];
        int n = 0;
        for (int i = 0; i < 4; i++) {
            float[] cur = cl[idx[i]], nxt = cl[idx[(i + 1) % 4]];
            boolean ci = cur[3] >= E, ni = nxt[3] >= E;
            if (ci) res[n++] = cur;
            if (ci != ni) {
                float t = (E - cur[3]) / (nxt[3] - cur[3]);
                res[n++] = new float[]{cur[0] + (nxt[0] - cur[0]) * t, cur[1] + (nxt[1] - cur[1]) * t, 0f, E};
            }
        }
        if (n < 3) return 0;
        for (int i = 0; i < n; i++) {
            float w = res[i][3];
            out[i * 2] = (res[i][0] / w * 0.5f + 0.5f) * fbW;
            out[i * 2 + 1] = (1f - (res[i][1] / w * 0.5f + 0.5f)) * fbH;
        }
        return n;
    }

    /** Fake projected box for the menu preview (corner bits: x, y-up, z-back). */
    public static void buildPreview(Shape s, float x1, float y1, float x2, float y2, float d) {
        float[] p = new float[16];
        for (int i = 0; i < 8; i++) {
            boolean right = (i & 1) != 0, up = (i & 2) != 0, back = (i & 4) != 0;
            float x = right ? x2 - d : x1;
            float y = up ? y1 + d : y2;
            if (back) { x += d; y -= d; }
            p[i * 2] = x;
            p[i * 2 + 1] = y;
        }
        int ei = 0;
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) != 0) continue;
                int j = i | bit;
                s.edges[ei * 4] = p[i * 2]; s.edges[ei * 4 + 1] = p[i * 2 + 1];
                s.edges[ei * 4 + 2] = p[j * 2]; s.edges[ei * 4 + 3] = p[j * 2 + 1];
                s.eOk[ei] = true;
                ei++;
            }
        }
        for (int f = 0; f < 6; f++) {
            for (int k = 0; k < 4; k++) {
                s.faces[f][k * 2] = p[FACES[f][k] * 2];
                s.faces[f][k * 2 + 1] = p[FACES[f][k] * 2 + 1];
            }
            s.fN[f] = 4;
        }
        s.x1 = x1; s.y1 = y1 - d; s.x2 = x2 + d; s.y2 = y2;
        s.valid = true;
    }

    // ------------------------------------------------------------------ drawing

    public static void drawShape(DrawContext ctx, Config c, Shape s, int col, float px) {
        if (!s.valid) return;
        Config.EspStyle st = c.espStyle;
        col |= 0xFF000000;
        int edgeCol = Colors.lerp(col, 0xFFFFFFFF, 0.78f);
        float th = Math.max(1f, c.espThickness) * px;

        if (st == Config.EspStyle.GLASS && c.espFill) {
            for (int f = 0; f < 6; f++) {
                if (s.fN[f] < 3) continue;
                fillPoly(ctx, s.faces[f], s.fN[f],
                        Colors.withAlpha(col, c.fillOpacity * 0.55f * FACE_MUL[f]),
                        Colors.withAlpha(col, c.fillOpacity * FACE_MUL[f]), s.y1, s.y2);
            }
        }
        boolean glow = st == Config.EspStyle.WIRE || c.espGlow;
        if (glow) {
            float[] widths = {th + 8f * px, th + 4.5f * px, th + 2f * px};
            float[] alphas = {0.05f, 0.09f, 0.17f};
            for (int g = 0; g < 3; g++) {
                int gc = Colors.withAlpha(col, alphas[g]);
                for (int e = 0; e < 12; e++) {
                    if (!s.eOk[e]) continue;
                    Gfx.lineF(ctx, s.edges[e * 4], s.edges[e * 4 + 1], s.edges[e * 4 + 2], s.edges[e * 4 + 3], widths[g], gc);
                }
            }
        }
        int core = Colors.withAlpha(edgeCol, 0.96f);
        for (int e = 0; e < 12; e++) {
            if (!s.eOk[e]) continue;
            Gfx.lineF(ctx, s.edges[e * 4], s.edges[e * 4 + 1], s.edges[e * 4 + 2], s.edges[e * 4 + 3], th, core);
        }
    }

    /** Fills a convex polygon with scanlines and a vertical alpha gradient. */
    private static void fillPoly(DrawContext ctx, float[] p, int n, int colTop, int colBot, float yTop, float yBot) {
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, p[i * 2 + 1]);
            maxY = Math.max(maxY, p[i * 2 + 1]);
        }
        float h = maxY - minY;
        int step = h > 320 ? 3 : (h > 120 ? 2 : 1);
        float span = Math.max(1f, yBot - yTop);
        int ys = (int) Math.floor(minY), ye = (int) Math.ceil(maxY);
        for (int y = ys; y < ye; y += step) {
            float yc = y + step * 0.5f;
            float xl = Float.MAX_VALUE, xr = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float y0 = p[i * 2 + 1], y1 = p[j * 2 + 1];
                if ((y0 <= yc && y1 > yc) || (y1 <= yc && y0 > yc)) {
                    float t = (yc - y0) / (y1 - y0);
                    float x = p[i * 2] + t * (p[j * 2] - p[i * 2]);
                    if (x < xl) xl = x;
                    if (x > xr) xr = x;
                }
            }
            if (xr > xl) {
                int col = Colors.lerp(colTop, colBot, MathHelper.clamp((yc - yTop) / span, 0f, 1f));
                Gfx.fill(ctx, (int) Math.floor(xl), y, (int) Math.ceil(xr), y + step, col);
            }
        }
    }

    // ---- classic 2D styles -------------------------------------------------

    public static void drawStyle2D(DrawContext ctx, Config c, float x1, float y1, float x2, float y2, int col, float px) {
        col |= 0xFF000000;
        float th = Math.max(1f, c.espThickness) * px;
        float o = c.espOutline ? px : 0f;
        if (c.espFill) {
            Gfx.gradF(ctx, x1, y1, x2 - x1, y2 - y1,
                    Colors.withAlpha(col, c.fillOpacity * 0.15f), Colors.withAlpha(col, c.fillOpacity));
        }
        if (c.espGlow) {
            for (int k = 3; k >= 1; k--) {
                float e = k * 2f * px;
                ring(ctx, x1 - e, y1 - e, x2 + e, y2 + e, 2f * px, Colors.withAlpha(col, 0.07f + 0.03f * (4 - k)));
            }
        }
        int n = c.espStyle == Config.EspStyle.CORNERS ? cornerPieces(x1, y1, x2, y2, th) : boxPieces(x1, y1, x2, y2, th);
        if (o > 0) {
            for (int i = 0; i < n; i++)
                Gfx.rectF(ctx, R[i * 4] - o, R[i * 4 + 1] - o, R[i * 4 + 2] + 2 * o, R[i * 4 + 3] + 2 * o, 0xC8000000);
        }
        int core = Colors.lerp(col, 0xFFFFFFFF, 0.35f);
        for (int i = 0; i < n; i++) Gfx.rectF(ctx, R[i * 4], R[i * 4 + 1], R[i * 4 + 2], R[i * 4 + 3], core);
    }

    private static void ring(DrawContext ctx, float x1, float y1, float x2, float y2, float t, int col) {
        Gfx.rectF(ctx, x1, y1, x2 - x1, t, col);
        Gfx.rectF(ctx, x1, y2 - t, x2 - x1, t, col);
        Gfx.rectF(ctx, x1, y1 + t, t, y2 - y1 - 2 * t, col);
        Gfx.rectF(ctx, x2 - t, y1 + t, t, y2 - y1 - 2 * t, col);
    }

    private static void put(int i, float x, float y, float w, float h) {
        R[i * 4] = x; R[i * 4 + 1] = y; R[i * 4 + 2] = w; R[i * 4 + 3] = h;
    }

    private static int boxPieces(float x1, float y1, float x2, float y2, float th) {
        float w = x2 - x1, h = y2 - y1;
        put(0, x1, y1, w, th);
        put(1, x1, y2 - th, w, th);
        put(2, x1, y1 + th, th, h - 2 * th);
        put(3, x2 - th, y1 + th, th, h - 2 * th);
        return 4;
    }

    private static int cornerPieces(float x1, float y1, float x2, float y2, float th) {
        float w = x2 - x1, h = y2 - y1;
        float lx = Math.max(th * 2, w / 3.5f), ly = Math.max(th * 2, h / 4.5f);
        put(0, x1, y1, lx, th);           put(1, x1, y1, th, ly);
        put(2, x2 - lx, y1, lx, th);      put(3, x2 - th, y1, th, ly);
        put(4, x1, y2 - th, lx, th);      put(5, x1, y2 - ly, th, ly);
        put(6, x2 - lx, y2 - th, lx, th); put(7, x2 - th, y2 - ly, th, ly);
        return 8;
    }

    public static void healthBar(DrawContext ctx, Config c, float x1, float y1, float y2, float ratio, float px) {
        float h = y2 - y1;
        float bw = Math.max(3f, c.espThickness + 1.5f) * px;
        float bx = x1 - 6f * px - bw;
        Gfx.rectF(ctx, bx - px, y1 - px, bw + 2 * px, h + 2 * px, 0xA0000000);
        float fh = h * MathHelper.clamp(ratio, 0f, 1f);
        int top = Colors.hsv(0.33f * ratio, 0.45f, 1f);
        int bot = Colors.hsv(0.33f * ratio, 0.85f, 0.95f);
        Gfx.gradF(ctx, bx, y2 - fh, bw, fh, top, bot);
    }

    // ------------------------------------------------------------------ nametag

    /** by = bottom edge of the tag, cx = centre x. u = overall UI scale. */
    public static void drawTag(DrawContext ctx, float cx, float by, String name, float hp, float hpRatio,
                               int dist, boolean invis, int accent, Config c, float u) {
        float sc = Math.max(0.4f, u * c.nametagScale);
        float k = 1.7f * sc;
        int h = Math.round(36 * sc), pad = Math.round(15 * sc), gap = Math.round(12 * sc), r = Math.round(11 * sc);
        String hs = String.format(Locale.ROOT, "%.1f", hp);
        String ds = dist + "m";
        String inv = "INVIS";
        int wInv = invis ? Math.round(Gfx.tw(inv, k, true)) + gap : 0;
        int wName = Math.round(Gfx.tw(name, k, true));
        int wHp = c.nameHealth ? gap + Math.round(Gfx.tw(hs, k, false)) : 0;
        int wDist = c.nameDistance ? gap + Math.round(Gfx.tw(ds, k, false)) : 0;
        int w = pad * 2 + wInv + wName + wHp + wDist;
        int x = Math.round(cx - w / 2f), y = Math.round(by - h);

        if (c.nameBg) {
            Gfx.rrect(ctx, x, y, w, h, r, 0xD2111113);
            Gfx.rrectRing(ctx, x, y, w, h, r, Math.max(1, Math.round(sc)), 0x2AFFFFFF);
            Gfx.rrect(ctx, x + r, y + h - Math.max(2, Math.round(2.5f * sc)), w - r * 2, Math.max(2, Math.round(2.5f * sc)),
                    1, Colors.withAlpha(accent, 0.9f));
        }
        float tx = x + pad;
        float cy = y + h * 0.5f - 1f * sc;
        if (invis) {
            Gfx.textC(ctx, inv, tx, cy, k, 0xFFFF8CC0, true);
            tx += wInv;
        }
        Gfx.textC(ctx, name, tx, cy, k, 0xFFFFFFFF, true);
        tx += wName;
        if (c.nameHealth) {
            tx += gap;
            Gfx.textC(ctx, hs, tx, cy, k, Colors.health(hpRatio), false);
            tx += Gfx.tw(hs, k, false);
        }
        if (c.nameDistance) {
            tx += gap;
            Gfx.textC(ctx, ds, tx, cy, k, 0xFF9C9CA8, false);
        }
    }
}
