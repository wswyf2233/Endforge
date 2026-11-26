package org.examplee.wangsword.item.unique;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * 赤金戒指（Accessories 的 ring 槽）。
 *
 * <p>佩戴时提供 +2 攻击力。
 */
public class RedGoldRingItem extends Item implements Accessory {

    /** 攻击力加成 */
    private static final double ATTACK_BONUS = 2.0D;

    public RedGoldRingItem(Settings settings) {
        super(settings);
    }

    @Override
    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        builder.addStackable(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new Identifier("wangsword", "redgold_ring"), ATTACK_BONUS,
                EntityAttributeModifier.Operation.ADDITION);
    }
}
