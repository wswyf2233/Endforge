package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;

/**
 * 终焉姿态的客户端状态（仅用于本地攻击距离预测）。
 *
 * <p>服务端真实判定仍以姿态状态为准；这里让客户端在姿态期间的攻击距离同步 +1.5 格。
 */
public final class TaiguStanceClient {

    /** 姿态持续时间（tick），与服务端一致 */
    private static final int DURATION = 200;
    /** 额外攻击距离 */
    private static final double EXTRA_RANGE = 1.5D;

    private static long endTick;

    private TaiguStanceClient() {
    }

    public static void start() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        endTick = client.world.getTime() + DURATION;
    }

    public static boolean isActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.world.getTime() < endTick;
    }

    public static double extraRange() {
        return isActive() ? EXTRA_RANGE : 0.0D;
    }
}