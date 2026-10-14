package ru.pic.pic_visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    public enum EspStyle {
        GLASS("Glass 3D"), WIRE("Wire 3D"), BOX("Box 2D"), CORNERS("Corners");
        public final String label;
        EspStyle(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    public enum ColorMode {
        STATIC("Static"), GRADIENT("Gradient"), RAINBOW("Rainbow"), HEALTH("Health");
        public final String label;
        ColorMode(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    public int version = 2;

    // Modules
    public boolean espEnabled = true;
    public boolean showInvisible = true;
    public boolean showSelf = false;
    public boolean nametag = true;
    public boolean healthBar = true;
    public boolean tracers = false;
    public boolean showItem = false;

    // ESP
    public EspStyle espStyle = EspStyle.GLASS;
    public float espThickness = 2f;
    public float fillOpacity = 0.30f;
    public boolean espFill = true;
    public boolean espGlow = true;
    public boolean espOutline = true;

    // Nametags
    public boolean nameHealth = true;
    public boolean nameDistance = true;
    public boolean nameBg = true;
    public float nametagScale = 1f;

    // HUD
    public boolean hudWatermark = true;
    public boolean hudInfo = true;
    public float hudScale = 1f;

    // Theme
    public float maxDistance = 256f;
    public float animSpeed = 1f;
    public ColorMode colorMode = ColorMode.STATIC;
    public int colorA = 0xFFC8F7D4;
    public int colorB = 0xFFFFFFFF;
    public int invisibleColor = 0xFFFF8CC0;
    public boolean invisibleOwnColor = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public static Config get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("pic_visuals.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null && c.version == 2) {
                    if (c.espStyle == null) c.espStyle = EspStyle.GLASS;
                    if (c.colorMode == null) c.colorMode = ColorMode.STATIC;
                    instance = c;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (Exception ignored) {
        }
    }
}
