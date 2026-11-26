package org.examplee.wangsword.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/**
 * 霜缚：冰封的 Boss 级降级版——只减速，不锁移动、不锁目标。
 *
 * <p>{@link FrozenEffect} 对普通生物是「定身 + 锁目标」，但套在 Boss 上会直接把战斗变成
 * 打木桩：剑冢守卫全部技能挂在 goal 的 {@code getTarget()} 上，监守者的音波走 brain 记忆，
 * 一冻就彻底不还手。所以 Boss 级只吃这一档：
 * <ul>
 *   <li>移动速度 −55%，冲锋、绕后、拉距离都变慢，但 AI、寻路、攻击判定完全保留；</li>
 *   <li>不清水平速度，所以击退、爆炸冲击照常生效（原版 Boss 本来就该有这种抗性）；</li>
 *   <li>不减攻速、不减攻击力，Boss 该打多重还是多重。</li>
 * </ul>
 *
 * <p>和冰封共用「霜」的视觉语言，只是粒子更稀，读起来像「被霜缠住」而不是「被冻成冰块」。
 */
public class FrozenBossEffect extends StatusEffect {

    /** 移动速度乘数：−55%，减速但肉眼可见还能走 */
    private static final double SPEED_MULTIPLIER = -0.55D;

    public FrozenBossEffect() {
        super(StatusEffectCategory.HARMFUL, 0x9CC7E6);

        this.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                "c41f8a07-2d95-4b73-8e61-0a7d3c5f9b28",
                SPEED_MULTIPLIER, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // 每 tick 都要跑：粒子节奏靠 age 取模控制
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }

        // 比冰封更稀：4 tick 一次、两粒，避免把 Boss 糊成一团白点
        if (entity.age % 4 == 0) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    entity.getX(), entity.getBodyY(0.55D), entity.getZ(),
                    2,
                    entity.getWidth() * 0.45D, entity.getHeight() * 0.35D, entity.getWidth() * 0.45D,
                    0.0D);
        }
    }
}
