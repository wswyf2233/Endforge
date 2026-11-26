package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 凋灵「坠地」状态：被凋零杀手的断空斩命中后，一段时间内无法维持飞行高度。
 *
 * <p>每 tick 给它一个向下的速度，配合玩家自己的输出窗口使用。
 */
public final class WitherFlightDisable {

    /** 坠地持续时间（tick） */
    public static final int DURATION = 100;
    /** 每 tick 施加的下坠速度 */
    private static final double SINK_SPEED = 0.55D;

    private static final Map<UUID, Long> DISABLED = new HashMap<>();

    private WitherFlightDisable() {
    }

    public static void disable(WitherEntity wither) {
        DISABLED.put(wither.getUuid(), wither.getWorld().getTime() + DURATION);
    }

    public static boolean isDisabled(WitherEntity wither) {
        Long end = DISABLED.get(wither.getUuid());
        return end != null && wither.getWorld().getTime() < end;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            if (DISABLED.isEmpty()) {
                return;
            }
            Iterator<Map.Entry<UUID, Long>> it = DISABLED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                ServerWorld world = server.getOverworld();
                // 在世界列表里找这个实体：凋灵通常在主世界，找不到就移除
                net.minecraft.entity.Entity entity = null;
                for (ServerWorld w : server.getWorlds()) {
                    entity = w.getEntity(entry.getKey());
                    if (entity != null) {
                        break;
                    }
                }
                if (!(entity instanceof WitherEntity wither) || !wither.isAlive()) {
                    it.remove();
                    continue;
                }
                if (wither.getWorld().getTime() >= entry.getValue()) {
                    it.remove();
                    continue;
                }
                Vec3d v = wither.getVelocity();
                wither.setVelocity(v.x * 0.5D, -SINK_SPEED, v.z * 0.5D);
                wither.velocityModified = true;
            }
        });
    }
}