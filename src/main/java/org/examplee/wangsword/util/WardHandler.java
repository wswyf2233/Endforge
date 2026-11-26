package org.examplee.wangsword.util;

import io.wispforest.accessories.api.AccessoriesCapability;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import org.examplee.wangsword.item.unique.WardCharmItem;
import org.examplee.wangsword.item.unique.WardType;

/**
 * 护符的免疫结算。
 *
 * <p>所有护符共用一条 {@code ALLOW_DAMAGE}：先看玩家戴了哪几枚，再逐枚问「这次伤害归不归你管」，
 * 第一个认领的直接取消伤害并播反馈。所以同一次伤害只会有一枚护符生效，不会叠加出奇怪的结果。
 *
 * <p>唯一的例外是掉虚空：那一类伤害谁都免疫不了。
 */
public final class WardHandler {

    private static WardCharmItem[] wards = new WardCharmItem[0];

    private WardHandler() {
    }

    /** 由主类在物品注册完之后调用，把五枚护符交给事件 */
    public static void init(WardCharmItem... items) {
        wards = items;
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) {
                return true;
            }
            // 掉虚空不救：那是世界边界，不是伤害
            if (source.isOf(DamageTypes.OUT_OF_WORLD)) {
                return true;
            }
            var capability = AccessoriesCapability.getOptionally(player).orElse(null);
            if (capability == null) {
                return true;
            }
            for (WardCharmItem charm : wards) {
                if (!capability.isEquipped(charm)) {
                    continue;
                }
                if (!charm.ward().matches(source)) {
                    continue;
                }
                charm.ward().onAbsorb(player, source, amount);
                return false;
            }
            return true;
        });
    }

    /** 护符每 tick 的常驻效果（淬火加速、氧气、冻伤条） */
    public static void tickWard(ServerPlayerEntity player, WardType ward) {
        if (player.isDead()) {
            return;
        }
        ward.tick(player);
    }

    /** 摘下护符：如果身上已经没有同一族的护符，就把常驻加成收掉 */
    public static void onUnequip(ServerPlayerEntity player, WardType ward) {
        if (player.isDead() || isWearing(player, ward)) {
            return;
        }
        WardType.clearBonuses(player);
    }

    /** 玩家当前是否戴着某一族护符 */
    public static boolean isWearing(ServerPlayerEntity player, WardType type) {
        var capability = AccessoriesCapability.getOptionally(player).orElse(null);
        if (capability == null) {
            return false;
        }
        for (WardCharmItem charm : wards) {
            if (charm.ward() == type && capability.isEquipped(charm)) {
                return true;
            }
        }
        return false;
    }

    /** 这一跳攒下的最高下落高度，着地时清掉 */
    private static final java.util.Map<java.util.UUID, Float> FALL_PEAK = new java.util.HashMap<>();

    /**
     * 坠星符的下落记账。
     *
     * <p>不靠伤害事件里的 {@code fallDistance}：触发坠落伤害那一刻原版可能已经把它清零，
     * 拿到的会一直是 0。这里每 tick 记一次峰值，着地那一 tick 再结算。
     */
    public static void trackFall(ServerPlayerEntity player) {
        boolean airborne = !player.isOnGround()
                && !player.isSpectator()
                && !player.getAbilities().flying;
        float current = player.fallDistance;
        if (airborne && current > 0.0F) {
            FALL_PEAK.merge(player.getUuid(), current, Math::max);
            return;
        }
        Float peak = FALL_PEAK.remove(player.getUuid());
        if (peak == null || !player.isOnGround() || player.isSpectator()) {
            return;
        }
        if (peak < WardType.MIN_SLAM_FALL) {
            return;
        }
        WardType.slam(player, peak);
    }
}
