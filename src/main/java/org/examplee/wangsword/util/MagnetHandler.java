package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 磁铁：手持时把周围 5 格内的掉落物吸向玩家。
 *
 * <p>每 tick 给范围内的掉落物一个朝玩家的速度，不影响正常拾取判定。
 */
public final class MagnetHandler {

    /** 吸附半径 */
    private static final double RANGE = 5.0D;
    /** 每 tick 加速度 */
    private static final double PULL = 0.45D;
    /** 垂直方向阻尼（避免物品乱飞） */
    private static final double VERTICAL_DAMPING = 0.6D;

    private MagnetHandler() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!holdsMagnet(player)) {
                    continue;
                }
                ServerWorld world = player.getServerWorld();
                Box box = player.getBoundingBox().expand(RANGE);
                for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, ItemEntity::isAlive)) {
                    if (item.squaredDistanceTo(player) > RANGE * RANGE) {
                        continue;
                    }
                    Vec3d target = player.getPos().add(0.0D, 0.6D, 0.0D);
                    Vec3d dir = target.subtract(item.getPos());
                    if (dir.lengthSquared() < 0.04D) {
                        continue;
                    }
                    Vec3d velocity = dir.normalize().multiply(PULL);
                    item.setVelocity(velocity.x, velocity.y * VERTICAL_DAMPING + 0.06D, velocity.z);
                    // 服务端改了速度必须标记，否则不同步
                    item.velocityModified = true;
                }
            }
        });
    }

    /** 手持磁铁，或饰品栏里戴着磁吸项链 */
    private static boolean holdsMagnet(PlayerEntity player) {
        if (player.getMainHandStack().isOf(ItemsRegistry.WS_MAGNET)
                || player.getOffHandStack().isOf(ItemsRegistry.WS_MAGNET)) {
            return true;
        }
        return io.wispforest.accessories.api.AccessoriesCapability.getOptionally(player)
                .map(capability -> capability.isEquipped(ItemsRegistry.WS_MAGNET_NECKLACE))
                .orElse(false);
    }
}