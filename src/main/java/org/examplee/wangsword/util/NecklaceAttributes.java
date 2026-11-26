package org.examplee.wangsword.util;

import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Identifier;

/**
 * 所有项链共用的一条基础属性：最大生命 +10。
 *
 * <p>赤金项链（无功能版）和磁吸、承魂、猎魂、残响、迅锋都挂这一条，佩戴哪条都拿到同样的
 * 生命加成，不会因为换了功能性项链而掉血上限。
 *
 * <p>用同一个修饰符 id（{@code wangsword:necklace_health}）+ {@code addStackable}：
 * necklace 槽只有 1 格，正常情况下只有一条生效；即使将来槽位变多，同 id 也只会取一份，
 * 不会叠成两层。
 */
public final class NecklaceAttributes {

    /** 最大生命加成 */
    public static final double MAX_HEALTH_BONUS = 10.0D;

    private static final Identifier HEALTH_ID = new Identifier("wangsword", "necklace_health");

    private NecklaceAttributes() {
    }

    /** 往饰品属性表里挂 +10 最大生命 */
    public static void applyMaxHealth(AccessoryAttributeBuilder builder) {
        builder.addStackable(EntityAttributes.GENERIC_MAX_HEALTH,
                HEALTH_ID, MAX_HEALTH_BONUS,
                EntityAttributeModifier.Operation.ADDITION);
    }
}
