package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 凋灵杀手命中凋灵后的「牵引」：2 秒内持续把它拉向玩家，方便连续输出。
 *
 * <p>只在被这把剑命中后生效，不会影响其它武器的手感。
 */
public final class WitherTetherHandler {

    /** 牵引持续时间：2 秒 */
    private static final int DURATION = 40;
    /** 每 tick 牵引速度 */
    private static final double PULL = 0.28D;
    /** 超过这个距离就放弃牵引 */
    private static final double MAX_RANGE = 32.0D;
    /** 目标点：停在玩家前方这么多格 */
    private static final double KEEP_DISTANCE = 2.5D;

    private static final class Tether {
        UUID attacker;
        long endTick;
    }

    private static final Map<UUID, Tether> ACTIVE = new HashMap<>();

    private WitherTetherHandler() {
    }

    /** 命中时调用，刷新牵引窗口 */
    public static void mark(WitherEntity wither, ServerPlayerEntity attacker) {
        Tether tether = ACTIVE.computeIfAbsent(wither.getUuid(), id -> new Tether());
        tether.attacker = attacker.getUuid();
        tether.endTick = wither.getWorld().getTime() + DURATION;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ACTIVE.isEmpty()) {
                return;
            }
            Iterator<Map.Entry<UUID, Tether>> it = ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Tether> entry = it.next();
                Tether tether = entry.getValue();
                ServerPlayerEntity attacker = server.getPlayerManager().getPlayer(tether.attacker);
                if (attacker == null || attacker.isDead()) {
                    it.remove();
                    continue;
                }
                ServerWorld world = attacker.getServerWorld();
                net.minecraft.entity.Entity entity = world.getEntity(entry.getKey());
                if (!(entity instanceof WitherEntity wither) || !wither.isAlive()) {
                    it.remove();
                    continue;
                }
                if (wither.getWorld().getTime() >= tether.endTick) {
                    it.remove();
                    continue;
                }
                double distance = wither.getPos().distanceTo(attacker.getPos());
                if (distance > MAX_RANGE) {
                    it.remove();
                    continue;
                }
                if (distance <= KEEP_DISTANCE) {
                    continue;
                }

                Vec3d target = attacker.getEyePos();
                Vec3d dir = target.subtract(wither.getPos().add(0.0D, wither.getHeight() * 0.5D, 0.0D));
                if (dir.lengthSquared() < 0.01D) {
                    continue;
                }
                Vec3d velocity = dir.normalize().multiply(PULL);
                wither.setVelocity(velocity.x, velocity.y * 0.5D, velocity.z);
                wither.velocityModified = true;
            }
        });
    }
}