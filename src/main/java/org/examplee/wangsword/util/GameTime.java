package org.examplee.wangsword.util;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 统一的计时源。
 *
 * <p>别再用 {@code player.getWorld().getTime()} 存跨会话的时间戳：那是「当前维度」的
 * gameTime，拿来和存档里的旧值比长短，换过维度就可能对不上。主世界的 gameTime 单调递增、
 * 跟着存档走、所有维度共享，适合做冷却和超时判定。
 */
public final class GameTime {

    private GameTime() {
    }

    public static long now(ServerPlayerEntity player) {
        return player.getServer().getOverworld().getTime();
    }
}
