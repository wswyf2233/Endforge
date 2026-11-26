package org.examplee.wangsword.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 誓约的祝福：同时带着誓约胜利之剑与阿瓦隆时获得的联动增益。
 *
 * <p>攻击伤害 +4、护甲 +4，每秒回复 1 点生命。
 */
public class OathBlessingEffect extends StatusEffect {

    public OathBlessingEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xFFD166);

        this.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                "8f1c6a2e-3b7d-4f5a-9c1e-2d4b6a8c0e11",
                4.0D, EntityAttributeModifier.Operation.ADDITION);
        this.addAttributeModifier(EntityAttributes.GENERIC_ARMOR,
                "8f1c6a2e-3b7d-4f5a-9c1e-2d4b6a8c0e12",
                4.0D, EntityAttributeModifier.Operation.ADDITION);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(1.0F);
        }
    }
}
