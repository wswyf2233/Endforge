package org.examplee.wangsword.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import org.examplee.wangsword.registry.EffectRegistry;

/**
 * 冰封：让生物彻底定在原地，不能走动、不能被打飞。
 *
 * <p>原版没有任何「不能移动」的状态效果（{@code 缓慢} 只是降速，7 级以上生物照样能转身、
 * 能攻击、能被击退），所以这里做了两层：
 * <ol>
 *   <li><b>属性层</b>：给 {@code generic.movement_speed} 挂 {@code MULTIPLY_TOTAL -1} 的修饰符，
 *       最终移动速度被属性下限钳到 0。这一层是持久的、由属性系统统一结算，寻路 AI 也会
 *       读到 0 速度，所以生物不会原地踏步；</li>
 *   <li><b>速度层</b>：每 tick 把水平速度清零。属性只能管「自己想走多快」，管不了被爆炸、
 *       击退附魔、水流、粘液块、活塞推出去的速度，所以还要这一层兜底。</li>
 * </ol>
 *
 * <p>竖直速度不归零，被冻住的生物仍会正常下落，不会悬在空中。
 *
 * <p><b>温和定身</b>：除了锁移动，还会让生物「锁不上新目标」——由
 * {@code FrozenTargetMixin} 在 {@code MobEntity.setTarget} 入口丢弃非空 target，
 * 效果生效瞬间也会把已有目标摘掉。所以被冻住的生物不会追你、不会起手近战、
 * 不会重新架弓，但<em>已经在飞行的箭矢、已经点燃的苦力怕引信照旧生效</em>，
 * AI 本身也没停（仍会转身、仍会尝试寻路，只是因为速度为零走不动）。
 *
 * <p><b>Boss 分级</b>：对 Boss 级生物这套会把战斗变成打木桩（剑冢守卫全部技能都挂在
 * {@code getTarget()} 上，监守者的音波走 brain 记忆），所以 {@link #freeze} 会给它们换成
 * 只减速的 {@link FrozenBossEffect}——保留 AI、保留攻击、保留被击退，只把移动速度压到 45%。
 * 末影龙和凋灵仍然完全免疫控制，理由见 {@link #canFreeze}。
 */
public class FrozenEffect extends StatusEffect {

    /** 移动速度乘数：-100%，钳制后即为 0 */
    private static final double SPEED_MULTIPLIER = -1.0D;

    public FrozenEffect() {
        super(StatusEffectCategory.HARMFUL, 0x8FD3F4);

        this.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                "b7d2e4f9-1a63-4c58-93d0-6f8b2e5c1a47",
                SPEED_MULTIPLIER, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // 每 tick 都要压速度，降频就会出现「抖一下往前挪一格」
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // 只清水平分量，保留竖直速度：冻住的生物该掉还是会掉
        entity.setVelocity(0.0D, entity.getVelocity().y, 0.0D);
        // 玩家必须置位，否则客户端预测会把他拽回去，表现为原地抽搐
        entity.velocityModified = true;

        // 停止当前寻路，避免生物对着墙一直「原地踏步」
        if (entity instanceof MobEntity mob) {
            mob.getNavigation().stop();
        }
        entity.setJumping(false);

        // 身上持续挂霜
        if (entity.getWorld() instanceof ServerWorld world && entity.age % 3 == 0) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    entity.getX(), entity.getBodyY(0.5D), entity.getZ(),
                    3,
                    entity.getWidth() * 0.5D, entity.getHeight() * 0.4D, entity.getWidth() * 0.5D,
                    0.0D);
        }
    }

    /**
     * 终局 Boss 免控的硬门槛：这两个的位移由阶段/飞行逻辑写死，不读移动速度属性，
     * 就算降级成霜缚也压不动它们，挂上去只会显示一个假图标。所以直接不给挂。
     * 旁观者同样不该被挂 debuff。
     */
    public static boolean canFreeze(LivingEntity target) {
        return !(target instanceof EnderDragonEntity)
                && !(target instanceof WitherEntity)
                && !target.isSpectator();
    }

    /** 血量到这个数就算 Boss 级，用来兜住模组/整合包里的自定义 Boss */
    public static final float BOSS_HEALTH_THRESHOLD = 100.0F;

    /**
     * 是不是该吃「霜缚」而不是「冰封」。
     *
     * <p>显式点名的是血量不到阈值但地位属于 Boss 的（远古守卫者 80），
     * 其余靠血量兜底：剑冢守卫 250、监守者 500、劫掠兽 100 都会被判成 Boss 级。
     */
    public static boolean isBossLevel(LivingEntity target) {
        if (target instanceof EnderDragonEntity
                || target instanceof WitherEntity
                || target instanceof WardenEntity
                || target instanceof ElderGuardianEntity
                || target instanceof RavagerEntity) {
            return true;
        }
        return target.getMaxHealth() >= BOSS_HEALTH_THRESHOLD;
    }

    /** 给目标挂上冰封，秒数由调用方决定；同一时间只会刷新，不会叠加。 */
    public static void freeze(LivingEntity target, int seconds) {
        freezeTicks(target, seconds * 20);
    }

    /**
     * 按 tick 挂冰封——霜环这类技能要的是 1.5 秒这种半秒精度，用秒数会丢。
     *
     * <p>Boss 级在这里降级成霜缚，所以所有调用方都不需要自己判断分级。
     */
    public static void freezeTicks(LivingEntity target, int ticks) {
        if (!canFreeze(target)) {
            return;
        }

        // Boss 只减速：定身 + 锁目标会把 Boss 战变成打木桩
        if (isBossLevel(target)) {
            target.addStatusEffect(new StatusEffectInstance(
                    EffectRegistry.FROSTBOUND, ticks, 0, false, true, true));
            return;
        }

        target.addStatusEffect(new StatusEffectInstance(
                EffectRegistry.FROZEN, ticks, 0, false, true, true));

        // 已有的目标要主动摘掉：setTarget 只拦「新设置」，拦不住冻结前就锁好的那个。
        // 放在 addStatusEffect 之后，顺序反过来的话下一 tick 又会被 AI 设回去。
        if (target instanceof MobEntity mob) {
            mob.setTarget(null);
        }
    }
}
