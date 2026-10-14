package ru.pic.pic_visuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.pic.pic_visuals.Widgets.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Main menu. Laid out in a fixed 1300x800 "design" space that is scaled to fit the screen. */
public class MenuScreen extends Screen {
    private static final int DW = 1300, DH = 800, PV_W = 410;

    private static final class Module {
        final String name, desc;
        final BooleanSupplier on;
        final Consumer<Boolean> set;
        final List<W> settings;
        float anim = -1f, hov = 0f;

        Module(String name, String desc, BooleanSupplier on, Consumer<Boolean> set, List<W> settings) {
            this.name = name;
            this.desc = desc;
            this.on = on;
            this.set = set;
            this.settings = settings;
            if (settings != null) settings.add(0, new Toggle("Enabled", on, set));
        }
    }

    private static final class Category {
        final String name;
        final int icon; // 0 eye, 1 hud, 2 theme
        final List<Module> mods;
        final List<W> direct;

        Category(String name, int icon, List<Module> mods, List<W> direct) {
            this.name = name;
            this.icon = icon;
            this.mods = mods;
            this.direct = direct;
        }
    }

    private final long openedAt = System.currentTimeMillis();
    private long lastNs = System.nanoTime();
    private float open = 0f;
    private int catIdx = 0;
    private float selY = -1f;
    private Module openMod = null;
    private float scroll = 0f, scrollT = 0f;
    private int contentH = 0;
    private W dragging = null;
    private String query = "";
    private boolean searchFocus = false;

    private float s = 1f, ox = 0f, oy = 0f, sf = 1f;
    private int cx0, cx1, cy0, cy1;
    private final List<Category> cats = new ArrayList<>();
    private final List<int[]> cardRects = new ArrayList<>();
    private final List<Module> cardMods = new ArrayList<>();
    private final int[] searchRect = new int[4];
    private final int[] crumbCat = new int[4];
    private final EspRenderer.Shape previewShape = new EspRenderer.Shape();

    public MenuScreen() {
        super(Text.literal("Pic Visuals"));
        Config c = Config.get();

        List<Module> visuals = new ArrayList<>();

        List<W> esp = new ArrayList<>();
        esp.add(new Header("Box"));
        esp.add(new Cycle<>("Style", Config.EspStyle.values(), () -> c.espStyle, v -> c.espStyle = v));
        esp.add(new Toggle("Fill", () -> c.espFill, v -> c.espFill = v));
        esp.add(new Slider("Fill opacity", 0.05f, 0.7f, 0.01f, "%.2f", () -> c.fillOpacity, v -> c.fillOpacity = v));
        esp.add(new Slider("Edge thickness", 1f, 5f, 0.5f, "%.1f px", () -> c.espThickness, v -> c.espThickness = v));
        esp.add(new Toggle("Soft glow", () -> c.espGlow, v -> c.espGlow = v));
        esp.add(new Toggle("Dark outline (2D styles)", () -> c.espOutline, v -> c.espOutline = v));
        visuals.add(new Module("ESP", "Glass 3D box around every player, visible through walls.",
                () -> c.espEnabled, v -> c.espEnabled = v, esp));

        List<W> inv = new ArrayList<>();
        inv.add(new Toggle("Own color", () -> c.invisibleOwnColor, v -> c.invisibleOwnColor = v));
        inv.add(new ColorW("Invisible color", () -> c.invisibleColor, v -> c.invisibleColor = v));
        visuals.add(new Module("Invisible", "Reveals invisible players and can give them their own color.",
                () -> c.showInvisible, v -> c.showInvisible = v, inv));

        List<W> tags = new ArrayList<>();
        tags.add(new Toggle("Show health", () -> c.nameHealth, v -> c.nameHealth = v));
        tags.add(new Toggle("Show distance", () -> c.nameDistance, v -> c.nameDistance = v));
        tags.add(new Toggle("Background", () -> c.nameBg, v -> c.nameBg = v));
        tags.add(new Slider("Scale", 0.6f, 2f, 0.05f, "%.2fx", () -> c.nametagScale, v -> c.nametagScale = v));
        visuals.add(new Module("Nametags", "Name, health and distance above each player.",
                () -> c.nametag, v -> c.nametag = v, tags));

        visuals.add(new Module("Health Bar", "Slim health bar next to the box.",
                () -> c.healthBar, v -> c.healthBar = v, null));
        visuals.add(new Module("Tracers", "Line from the bottom of the screen to each player.",
                () -> c.tracers, v -> c.tracers = v, null));
        visuals.add(new Module("Held Item", "Shows the item a player is holding under the box.",
                () -> c.showItem, v -> c.showItem = v, null));
        visuals.add(new Module("Self", "Draw the ESP on yourself in third person.",
                () -> c.showSelf, v -> c.showSelf = v, null));
        cats.add(new Category("Visuals", 0, visuals, null));

        List<Module> hud = new ArrayList<>();
        List<W> wm = new ArrayList<>();
        wm.add(new Slider("HUD scale", 0.6f, 1.6f, 0.05f, "%.2fx", () -> c.hudScale, v -> c.hudScale = v));
        hud.add(new Module("Watermark", "Pill with your name, FPS, time and ping.",
                () -> c.hudWatermark, v -> c.hudWatermark = v, wm));
        hud.add(new Module("Information", "Panel with BPS, TPS, coordinates and ping.",
                () -> c.hudInfo, v -> c.hudInfo = v, null));
        cats.add(new Category("HUD", 1, hud, null));

        List<W> theme = new ArrayList<>();
        theme.add(new Header("Presets"));
        theme.add(new Presets());
        theme.add(new Header("Colors"));
        theme.add(new Cycle<>("Color mode", Config.ColorMode.values(), () -> c.colorMode, v -> c.colorMode = v));
        theme.add(new ColorW("Primary color", () -> c.colorA, v -> c.colorA = v));
        theme.add(new ColorW("Secondary color", () -> c.colorB, v -> c.colorB = v));
        theme.add(new Header("Range and animation"));
        theme.add(new Slider("Max distance", 16f, 512f, 8f, "%.0f m", () -> c.maxDistance, v -> c.maxDistance = v));
        theme.add(new Slider("Animation speed", 0.2f, 3f, 0.1f, "%.1fx", () -> c.animSpeed, v -> c.animSpeed = v));
        cats.add(new Category("Theme", 2, null, theme));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        Gfx.ga = 1f;
        Gfx.clearClip();
        Config.save();
    }

    // ------------------------------------------------------------------ coordinate helpers

    private double dx(double mx) { return (mx * sf - ox) / s; }
    private double dy(double my) { return (my * sf - oy) / s; }

    private boolean inContent(double x, double y) {
        return x >= cx0 && x < cx1 + 4 && y >= cy0 && y < cy1;
    }

    private Category cat() { return cats.get(catIdx); }

    private List<Module> visibleModules() {
        List<Module> out = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            for (Category ca : cats) {
                if (ca.mods == null) continue;
                for (Module m : ca.mods) {
                    if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.desc.toLowerCase(Locale.ROOT).contains(q)) out.add(m);
                }
            }
        } else if (cat().mods != null) {
            out.addAll(cat().mods);
        }
        return out;
    }

    private List<W> pageWidgets() {
        if (openMod != null) return openMod.settings;
        if (cat().direct != null && query.trim().isEmpty()) return cat().direct;
        return null;
    }

    private boolean showPreview() {
        return (openMod != null && catIdx == 0) || (openMod == null && cat().direct != null && query.trim().isEmpty());
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        Gfx.dt = dt;
        open += (1f - open) * (1f - (float) Math.exp(-dt * 12f));

        // Vanilla background blur (strength comes from Options > Video > Menu Background Blur).
        this.renderBackground(ctx, mx, my, delta);

        Gfx.ga = open;
        try {
            Gfx.grad(ctx, 0, 0, width, height, 0x30000000, 0x66000000);
            draw(ctx, mx, my);
        } finally {
            Gfx.ga = 1f;
            Gfx.clearClip();
        }
    }

    private void draw(DrawContext ctx, int mx, int my) {
        Window win = client.getWindow();
        int fbW = win.getFramebufferWidth(), fbH = win.getFramebufferHeight();
        sf = (float) win.getScaleFactor();
        s = Math.min(1f, Math.min(fbW * 0.94f / DW, fbH * 0.92f / DH)) * (0.95f + 0.05f * open);
        ox = (fbW - DW * s) / 2f;
        oy = (fbH - DH * s) / 2f;

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.scale(1f / sf, 1f / sf, 1f);
        ms.translate(ox, oy, 0f);
        ms.scale(s, s, 1f);
        try {
            drawPanel(ctx, (int) dx(mx), (int) dy(my));
        } finally {
            ms.pop();
        }
    }

    private void drawPanel(DrawContext ctx, int mx, int my) {
        Config c = Config.get();
        int A = c.colorA | 0xFF000000;

        // soft shadow + body
        for (int i = 5; i >= 1; i--) Gfx.rrect(ctx, -i * 5, -i * 5 + 8, DW + i * 10, DH + i * 10, 46 + i * 4, 0x0C000000);
        Gfx.rrect(ctx, 0, 0, DW, DH, 46, 0xF0191A1D);
        Gfx.rrectRing(ctx, 0, 0, DW, DH, 46, 2, 0xFF2A2B30);

        // sidebar
        Gfx.rrect(ctx, 14, 14, 114, DH - 28, 32, 0xFF111214);
        // header + body
        Gfx.rrect(ctx, 142, 14, DW - 156, 78, 30, 0xFF1B1C1F);
        Gfx.rrect(ctx, 142, 106, DW - 156, DH - 120, 30, 0xFF131416);

        cx0 = 168;
        cx1 = DW - 14 - 26;
        cy0 = 106 + 24;
        cy1 = DH - 14 - 24;

        drawSidebar(ctx, mx, my, A);
        drawHeader(ctx, mx, my);
        drawContent(ctx, mx, my);
    }

    private void drawSidebar(DrawContext ctx, int mx, int my, int A) {
        // logo tile
        Gfx.rrect(ctx, 36, 32, 70, 70, 22, 0xFF1F2023);
        Gfx.rrectRing(ctx, 36, 32, 70, 70, 22, 2, 0xFF303136);
        Gfx.ctext(ctx, "P", 71, 67 - Gfx.CAP * 3.2f / 2f, 3.2f, 0xFFF0F0F4, true);

        float target = 150 + catIdx * 90;
        if (selY < 0) selY = target;
        selY += (target - selY) * (1f - (float) Math.exp(-Gfx.dt * 18f));
        Gfx.rrect(ctx, 36, Math.round(selY), 70, 70, 22, 0xFF37383D);
        Gfx.rrectRing(ctx, 36, Math.round(selY), 70, 70, 22, 2, 0xFF4B4C52);

        for (int i = 0; i < cats.size(); i++) {
            int by = 150 + i * 90;
            boolean hv = mx >= 36 && mx < 106 && my >= by && my < by + 70;
            float sel = Math.max(0f, 1f - Math.abs(selY - by) / 70f);
            int col = Colors.lerp(hv ? 0xFFA8A8B0 : 0xFF70717A, 0xFFF4F4F8, sel);
            int cx = 71, cy = by + 35;
            switch (cats.get(i).icon) {
                case 0 -> Icons.eye(ctx, cx, cy, 17, col);
                case 1 -> Icons.hud(ctx, cx, cy, 16, col);
                default -> Icons.theme(ctx, cx, cy, 15, col);
            }
        }
        // profile circle
        int py = DH - 14 - 20 - 35;
        Gfx.circle(ctx, 71, py, 35, 0xFF3A3B40);
        Gfx.circle(ctx, 71, py, 33, 0xFF16171A);
        Icons.user(ctx, 71, py, 15, 0xFFE4E4EA);
    }

    private void drawHeader(DrawContext ctx, int mx, int my) {
        int cy = 53;
        Gfx.rrect(ctx, 166, 32, 42, 42, 14, 0xFF2A2B2F);
        Gfx.ctext(ctx, "P", 187, cy - Gfx.CAP * 2f / 2f, 2f, 0xFFF0F0F4, true);

        float x = 226;
        float k = 2.05f;
        Gfx.textC(ctx, "Pic Visuals", x, cy, k, 0xFF8E8F98, true);
        x += Gfx.tw("Pic Visuals", k, true) + 16;
        Gfx.textC(ctx, ">", x, cy, 1.7f, 0xFF5C5D66, true);
        x += Gfx.tw(">", 1.7f, true) + 16;
        String cn = cat().name;
        boolean last = openMod == null;
        crumbCat[0] = (int) x; crumbCat[1] = 28; crumbCat[2] = (int) Gfx.tw(cn, k, true); crumbCat[3] = 50;
        Gfx.textC(ctx, cn, x, cy, k, last ? 0xFFFFFFFF : 0xFFB8B9C2, true);
        x += Gfx.tw(cn, k, true) + 16;
        if (openMod != null) {
            Gfx.textC(ctx, ">", x, cy, 1.7f, 0xFF5C5D66, true);
            x += Gfx.tw(">", 1.7f, true) + 16;
            Gfx.textC(ctx, openMod.name, x, cy, k, 0xFFFFFFFF, true);
        }

        // key pill
        int kx = DW - 30 - 360 - 16 - 130, ky = 27;
        Gfx.rrect(ctx, kx, ky, 130, 52, 18, 0xFF222326);
        Gfx.textC(ctx, "H", kx + 30, ky + 26, 1.9f, 0xFFF0F0F4, true);
        Icons.dots(ctx, kx + 130 - 34, ky + 26, 9, 0xFF8E8F98);

        // search
        int sx = DW - 30 - 360, sy = 27, sw = 360, sh = 52;
        searchRect[0] = sx; searchRect[1] = sy; searchRect[2] = sw; searchRect[3] = sh;
        Gfx.rrect(ctx, sx, sy, sw, sh, 18, 0xFF222326);
        if (searchFocus) Gfx.rrectRing(ctx, sx, sy, sw, sh, 18, 2, 0xFF55565E);
        Icons.search(ctx, sx + 32, ky + 26, 12, 0xFF8E8F98);
        if (query.isEmpty() && !searchFocus) {
            Gfx.textC(ctx, "Search...", sx + 62, ky + 26, 1.85f, 0xFF7C7D86, false);
        } else {
            Gfx.textC(ctx, query, sx + 62, ky + 26, 1.85f, 0xFFF0F0F4, false);
            if (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cxp = sx + 62 + Math.round(Gfx.tw(query, 1.85f, false)) + 3;
                Gfx.fill(ctx, cxp, ky + 14, cxp + 2, ky + 38, 0xFFF0F0F4);
            }
        }
    }

    private void drawContent(DrawContext ctx, int mx, int my) {
        int viewH = cy1 - cy0;
        float maxScroll = Math.max(0, contentH - viewH);
        scrollT = Math.max(0f, Math.min(maxScroll, scrollT));
        scroll += (scrollT - scroll) * (1f - (float) Math.exp(-Gfx.dt * 16f));

        boolean hoverOk = inContent(mx, my);
        int hmx = hoverOk ? mx : -9999, hmy = hoverOk ? my : -9999;

        cardRects.clear();
        cardMods.clear();
        List<W> page = pageWidgets();
        boolean preview = showPreview();

        Gfx.setClip(cy0, cy1);
        int startY = cy0 - Math.round(scroll);
        int y = startY;
        if (page != null) {
            int colW = preview ? (cx1 - cx0 - PV_W - 24) : (cx1 - cx0);
            for (W w : page) {
                w.x = cx0;
                w.y = y;
                w.w = colW;
                w.render(ctx, hmx, hmy);
                y += w.height();
            }
        } else {
            List<Module> list = visibleModules();
            int gap = 16, cw = (cx1 - cx0 - gap) / 2, ch = 126;
            for (int i = 0; i < list.size(); i++) {
                int x = cx0 + (i % 2) * (cw + gap);
                int cyy = startY + (i / 2) * (ch + gap);
                boolean hv = hmx >= x && hmx < x + cw && hmy >= cyy && hmy < cyy + ch;
                drawCard(ctx, list.get(i), x, cyy, cw, ch, hv);
                cardRects.add(new int[]{x, cyy, cw, ch});
                cardMods.add(list.get(i));
            }
            int rows = (list.size() + 1) / 2;
            y = startY + rows * (ch + gap) - (rows > 0 ? gap : 0);
            if (list.isEmpty()) {
                Gfx.ctext(ctx, "No modules found", (cx0 + cx1) / 2f, cy0 + 40, 1.9f, 0xFF7C7D86, false);
            }
        }
        contentH = y - startY;
        Gfx.clearClip();

        if (contentH > viewH) {
            int bh = Math.max(36, viewH * viewH / contentH);
            int by = cy0 + Math.round((viewH - bh) * (scroll / Math.max(1f, contentH - viewH)));
            Gfx.rrect(ctx, cx1 + 12, by, 5, bh, 2, 0xFF55565E);
        }
        if (preview) drawPreview(ctx, cx1 - PV_W, cy0, PV_W, viewH);
    }

    // ------------------------------------------------------------------ cards

    private List<String> wrap(String text, float k, int maxW) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String t = line.isEmpty() ? word : line + " " + word;
            if (Gfx.tw(t, k, false) > maxW && !line.isEmpty()) {
                lines.add(line);
                line = word;
            } else {
                line = t;
            }
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    private void drawCard(DrawContext ctx, Module m, int x, int y, int w, int h, boolean hv) {
        boolean on = m.on.getAsBoolean();
        float target = on ? 1f : 0f;
        if (m.anim < 0) m.anim = target;
        m.anim = Widgets.approach(m.anim, target);
        m.hov = Widgets.approach(m.hov, hv ? 1f : 0f);

        Gfx.rrect(ctx, x, y, w, h, 26, Colors.lerp(0xFF1B1C1F, 0xFF212226, m.hov));
        Gfx.rrectRing(ctx, x, y, w, h, 26, 2, Colors.lerp(0xFF2A2B2F, 0xFF3C3D43, m.anim));

        int nx = x + 32 + Math.round(18 * m.anim);
        if (m.anim > 0.02f) Gfx.rrect(ctx, x + 32, y + 28, 4, 28, 2, Colors.mulAlpha(0xFFEAEAEE, m.anim));
        Gfx.text(ctx, m.name, nx, y + 26, 2.35f, Colors.lerp(0xFF7E7F88, 0xFFFFFFFF, m.anim), true);

        int descCol = Colors.lerp(0xFF666770, 0xFF9C9DA6, m.anim);
        List<String> lines = wrap(m.desc, 1.6f, w - (nx - x) - 40);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            Gfx.text(ctx, lines.get(i), nx, y + 70 + i * 26, 1.6f, descCol, false);
        }

        Widgets.drawSwitch(ctx, x + w - 32 - 62, y + 26, 62, 32, m.anim);
        if (m.settings != null) {
            Icons.gear(ctx, x + w - 32 - 62 - 40, y + 42, 15, Colors.lerp(0xFF6A6B74, 0xFFB4B5BE, Math.max(m.anim, m.hov)));
        }
    }

    // ------------------------------------------------------------------ preview

    private void drawPreview(DrawContext ctx, int x, int y, int w, int h) {
        Config c = Config.get();
        Gfx.rrect(ctx, x, y, w, h, 28, 0xFF17181B);
        Gfx.rrectRing(ctx, x, y, w, h, 28, 2, 0xFF2A2B2F);
        Gfx.text(ctx, "PREVIEW", x + 30, y + 30, 1.4f, 0xFF7C7D86, true);

        float k = 6.0f;
        float fw = 16 * k, fh = 32 * k;
        float fx = x + w / 2f - fw / 2f - 14, fy = y + h / 2f - fh / 2f + 26;
        int a = 0x45000000;
        Gfx.rectF(ctx, fx + 4 * k, fy, 8 * k, 8 * k, a | 0xB98A65);
        Gfx.rectF(ctx, fx + 4 * k, fy + 8 * k, 8 * k, 12 * k, a | 0x3FA6A6);
        Gfx.rectF(ctx, fx, fy + 8 * k, 4 * k, 12 * k, a | 0xB98A65);
        Gfx.rectF(ctx, fx + 12 * k, fy + 8 * k, 4 * k, 12 * k, a | 0xB98A65);
        Gfx.rectF(ctx, fx + 4 * k, fy + 20 * k, 4 * k, 12 * k, a | 0x3B3B8A);
        Gfx.rectF(ctx, fx + 8 * k, fy + 20 * k, 4 * k, 12 * k, a | 0x3B3B8A);

        int col = c.invisibleOwnColor ? (c.invisibleColor | 0xFF000000)
                : Colors.byMode(c.colorMode, c.colorA, c.colorB, 0f, 0.8f, c.animSpeed);

        float bx1 = fx - 22, bx2 = fx + fw + 22, by1 = fy - 20, by2 = fy + fh + 20;
        float top;
        if (c.espStyle == Config.EspStyle.BOX || c.espStyle == Config.EspStyle.CORNERS) {
            EspRenderer.drawStyle2D(ctx, c, bx1, by1, bx2, by2, col, 1f);
            top = by1;
        } else {
            float d = 40f;
            // front face = bx1..bx2 / by1..by2, back face is shifted by (+d, -d)
            EspRenderer.buildPreview(previewShape, bx1, by1 - d, bx2 + d, by2, d);
            EspRenderer.drawShape(ctx, c, previewShape, col, 1f);
            top = by1 - d;
        }
        if (c.healthBar) EspRenderer.healthBar(ctx, c, bx1, by1, by2, 0.8f, 1f);
        if (c.nametag) {
            EspRenderer.drawTag(ctx, (bx1 + bx2) / 2f, top - 10, "Steve", 16f, 0.8f, 42, true, col, c, 1f);
        }
        Gfx.ctext(ctx, "invisible player", x + w / 2f, y + h - 44, 1.5f, 0xFF6C6D76, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mxRaw, double myRaw, int btn) {
        double mx = dx(mxRaw), my = dy(myRaw);

        // search box
        if (mx >= searchRect[0] && mx < searchRect[0] + searchRect[2] && my >= searchRect[1] && my < searchRect[1] + searchRect[3]) {
            searchFocus = true;
            return true;
        }
        searchFocus = false;

        // breadcrumb: go back from a module page
        if (openMod != null && mx >= crumbCat[0] && mx < crumbCat[0] + crumbCat[2] && my >= crumbCat[1] && my < crumbCat[1] + crumbCat[3]) {
            openMod = null;
            scroll = 0; scrollT = 0;
            return true;
        }

        // sidebar
        for (int i = 0; i < cats.size(); i++) {
            int by = 150 + i * 90;
            if (mx >= 36 && mx < 106 && my >= by && my < by + 70) {
                if (catIdx != i || openMod != null || !query.isEmpty()) {
                    catIdx = i;
                    openMod = null;
                    query = "";
                    scroll = 0; scrollT = 0;
                }
                return true;
            }
        }

        if (inContent(mx, my)) {
            List<W> page = pageWidgets();
            if (page != null) {
                for (W w : page) {
                    if (w.hover(mx, my) && w.click(mx, my, btn)) {
                        dragging = w;
                        return true;
                    }
                }
            } else {
                for (int i = 0; i < cardRects.size(); i++) {
                    int[] r = cardRects.get(i);
                    if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                        Module m = cardMods.get(i);
                        double gx = r[0] + r[2] - 32 - 62 - 40, gy = r[1] + 42;
                        boolean gear = m.settings != null && Math.abs(mx - gx) < 30 && Math.abs(my - gy) < 30;
                        if (gear || (btn == 1 && m.settings != null)) {
                            openMod = m;
                            for (int ci = 0; ci < cats.size(); ci++) {
                                if (cats.get(ci).mods != null && cats.get(ci).mods.contains(m)) catIdx = ci;
                            }
                            query = "";
                            scroll = 0; scrollT = 0;
                        } else if (btn == 0) {
                            m.set.accept(!m.on.getAsBoolean());
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mxRaw, myRaw, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double ddx, double ddy) {
        if (dragging != null) {
            dragging.drag(dx(mx), dy(my));
            return true;
        }
        return super.mouseDragged(mx, my, btn, ddx, ddy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (dragging != null) {
            dragging.release();
            dragging = null;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inContent(dx(mx), dy(my))) {
            scrollT -= (float) vertical * 70f;
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocus && chr >= 32 && chr != 127 && query.length() < 24) {
            query += chr;
            openMod = null;
            scroll = 0; scrollT = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocus) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocus = false;
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_H && System.currentTimeMillis() - openedAt > 300) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
