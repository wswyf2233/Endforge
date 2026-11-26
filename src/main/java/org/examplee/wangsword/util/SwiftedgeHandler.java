package org.examplee.wangsword.util;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * 迅锋项链：冲刺时额外提速。
 *
 * <p>固定的 +12% 攻速写在 {@code SwiftedgeNecklaceItem#getDynamicModifiers} 里，
 * 由 Accessories 随装备状态自动挂/摘；这里只管「冲刺」那一档动态加成。
 *
 * <p>之所以不记账、不设计时器：冲刺是每 tick 都能读到的瞬时状态，
 * 直接比对「该不该有修正器」比维护一份状态表更不容易出错
 * （换维度、死亡重生会重建属性表，记账版会留下错误状态）。
 */
public final class SwiftedgeHandler {

    /** 冲刺时的额外攻速（MULTIPLY_TOTAL） */
    private static final double SPRINT_BONUS = 0.18D;

    private static final String MODIFIER_NAME = "ws_swiftedge_sprint";

    private static final UUID MODIFIER_ID = UUID.fromString("6b1d4a07-2e58-4c93-9a07-3f5be2c814d6");

    private SwiftedgeHandler() {
    }

    /** 每 tick 由项链的 {@code Accessory#tick} 调用。 */
    public static void tick(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (instance == null) {
            return;
        }
        boolean sprinting = player.isSprinting();
        EntityAttributeModifier existing = instance.getModifier(MODIFIER_ID);
        if (sprinting && existing == null) {
            instance.addTemporaryModifier(new EntityAttributeModifier(
                    MODIFIER_ID, MODIFIER_NAME, SPRINT_BONUS,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!sprinting && existing != null) {
            instance.removeModifier(existing);
        }
    }

    /** 摘下项链：冲刺加成立刻撤掉。 */
    public static void clear(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (instance == null) {
            return;
        }
        EntityAttributeModifier existing = instance.getModifier(MODIFIER_ID);
        if (existing != null) {
            instance.removeModifier(existing);
        }
    }
}
