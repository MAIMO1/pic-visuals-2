package ru.pic.pic_visuals;

import net.minecraft.client.MinecraftClient;

public final class Stats {
    public static float tps = 20f;
    public static boolean tpsKnown = false;
    public static float bps = 0f;

    private static long lastNs = 0L;
    private static double lx, lz;
    private static boolean has = false;

    private Stats() {}

    public static void onTimeUpdate() {
        long now = System.nanoTime();
        if (lastNs != 0L) {
            double dt = (now - lastNs) / 1e9;
            if (dt > 0.2) {
                float inst = (float) Math.min(20.0, 20.0 / dt);
                tps = tpsKnown ? tps + (inst - tps) * 0.4f : inst;
                tpsKnown = true;
            }
        }
        lastNs = now;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) {
            has = false;
            tpsKnown = false;
            lastNs = 0L;
            bps = 0f;
            return;
        }
        double x = mc.player.getX(), z = mc.player.getZ();
        if (has) {
            float d = (float) (Math.hypot(x - lx, z - lz) * 20.0);
            if (d > 200f) d = 0f;
            bps += (d - bps) * 0.3f;
        }
        lx = x;
        lz = z;
        has = true;
    }
}
