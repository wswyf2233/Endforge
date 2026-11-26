package org.examplee.wangsword.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * 状态效果工具。
 *
 * <p>常用效果对照（Yarn 1.20.1）：
 * <pre>
 * 饱和        StatusEffects.SATURATION
 * 村庄英雄    StatusEffects.HERO_OF_THE_VILLAGE
 * 幸运        StatusEffects.LUCK
 * 海豚的恩惠  StatusEffects.DOLPHINS_GRACE
 * 潮涌能量    StatusEffects.CONDUIT_POWER
 * 抗火        StatusEffects.FIRE_RESISTANCE
 * 缓降        StatusEffects.SLOW_FALLING
 * 急迫        StatusEffects.HASTE
 * 抗性提升    StatusEffects.RESISTANCE
 * 力量        StatusEffects.STRENGTH
 * 伤害吸收    StatusEffects.ABSORPTION
 * 生命恢复    StatusEffects.REGENERATION
 * 生命提升    StatusEffects.HEALTH_BOOST
 * 水下呼吸    StatusEffects.WATER_BREATHING
 * 瞬间治疗    StatusEffects.INSTANT_HEALTH
 * 速度        StatusEffects.SPEED
 * 跳跃提升    StatusEffects.JUMP_BOOST
 * 夜视        StatusEffects.NIGHT_VISION
 * 隐身        StatusEffects.INVISIBILITY
 * </pre>
 */
public final class EffectHelper {

    private EffectHelper() {
    }

    /**
     * 给实体挂效果。
     *
     * @param seconds   持续时间（秒），内部乘 20 转成 tick
     * @param amplifier 等级，0 = I 级，1 = II 级
     */
    public static StatusEffectInstance give(LivingEntity entity, StatusEffect effect,
                                            int seconds, int amplifier) {
        StatusEffectInstance instance =
                new StatusEffectInstance(effect, seconds * 20, amplifier);
        entity.addStatusEffect(instance);
        return instance;
    }

    /** 挂 I 级效果 */
    public static void give(LivingEntity entity, StatusEffect effect, int seconds) {
        give(entity, effect, seconds, 0);
    }

    /** 永久效果（约 100 万秒），适合饰品/装备持续生效 */
    public static void giveInfinite(LivingEntity entity, StatusEffect effect, int amplifier) {
        give(entity, effect, 1_000_000, amplifier);
    }

    public static void remove(LivingEntity entity, StatusEffect effect) {
        entity.removeStatusEffect(effect);
    }

    public static boolean has(LivingEntity entity, StatusEffect effect) {
        return entity.hasStatusEffect(effect);
    }
}
