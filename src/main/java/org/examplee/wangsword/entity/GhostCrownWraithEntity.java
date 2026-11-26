package org.examplee.wangsword.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 裂冠幽灵：精英级亡灵，剑冢一带的低空游荡者。
 *
 * <p>模型、动画、贴图三份资源都是 Blockbench 直接导出的 Bedrock 格式：
 * <ul>
 *   <li>{@code geo/ghost_crown_wraith.geo.json}</li>
 *   <li>{@code animations/ghost_crown_wraith.animation.json}（待机 / 行走 / 攻击三段）</li>
 *   <li>{@code textures/entity/ghost_crown_wraith.png}</li>
 * </ul>
 * 动画名是模型里原来的中文，改名前这里必须跟着改。
 *
 * <p>归入亡灵：{@link EntityGroup#UNDEAD}，亡灵杀手对它加伤，治疗药水伤它。
 */
public class GhostCrownWraithEntity extends HostileEntity implements GeoEntity {

    /** 常驻：原地悬浮 */
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("待机 · 悬浮");
    /** 移动：低空滑行 */
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("行走 · 低空滑行");
    /** 一次性：俯冲攻击，播完自动回待机 */
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("攻击 · 俯冲");

    /** 击杀经验：比普通怪物高一档 */
    private static final int XP_TO_DROP = 30;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GhostCrownWraithEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = XP_TO_DROP;
        // 飞行生物：不吃重力，用飞行移动控制器 + 空中寻路，撞到墙就往上窜
        this.setNoGravity(true);
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.navigation = new BirdNavigation(this, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 9.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
                // FlightMoveControl 每 tick 都会读这个属性，缺了会直接抛
                // "Can't find attribute minecraft:generic.flying_speed"
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.30D)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0D)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 2.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.35D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void initGoals() {
        // 远程优先：只占 LOOK，不占 MOVE，所以它在远处开火的同时近战 Goal 仍会继续追
        this.goalSelector.add(0, new OrbAttackGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8D));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
        this.goalSelector.add(4, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    /**
     * 掉落：裂冠残片 1~2 个，抢夺每级再 +0~1 个。
     *
     * <p>没用掉落表——1.20.1 的 {@code MobEntity.getLootTable()} 是 final，
     * 而 {@code EntityType.Builder} 也没有设置掉落表的入口，只能在这里手动掉。
     */
    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        if (this.getWorld().isClient()
                || !this.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_LOOT)) {
            return;
        }

        int count = 1 + this.random.nextInt(2);
        if (causedByPlayer && source.getAttacker() instanceof LivingEntity killer) {
            int looting = net.minecraft.enchantment.EnchantmentHelper.getLooting(killer);
            if (looting > 0) {
                count += this.random.nextInt(looting + 1);
            }
        }
        this.dropStack(new net.minecraft.item.ItemStack(
                org.examplee.wangsword.registry.ItemsRegistry.WS_CROWN_SHARD, count));
    }

    @Override
    public EntityGroup getGroup() {
        return EntityGroup.UNDEAD;
    }

    // ------------------------------------------------------------------
    // 音效
    // ------------------------------------------------------------------

    /** 平时：幻翼那种飘忽的低鸣，隔一阵才响一次 */
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PHANTOM_DEATH;
    }

    /** 贴地滑行，不该有脚步声 */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // 空实现：不给滑行加脚步声
    }

    /** 飘着的东西不该摔伤 */
    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }
        // 飞行怪撞到墙/方块侧面时会一直顶着不动，给一点上抬力让它翻过去
        if (this.horizontalCollision) {
            this.setVelocity(this.getVelocity().add(0.0D, 0.08D, 0.0D));
        }
    }

    /** 每次真的打到人时播一次俯冲动作 */
    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            this.triggerAnim("main", "attack");
        }
        return hit;
    }

    // ------------------------------------------------------------------
    // 动画
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::mainController)
                .triggerableAnim("attack", ATTACK_ANIM));
    }

    private PlayState mainController(AnimationState<GhostCrownWraithEntity> state) {
        // 客户端上速度有时是 0，limbSwingAmount 是原版按位移算的踏步量，更可靠
        if (state.isMoving() || state.getLimbSwingAmount() > 0.01F) {
            return state.setAndContinue(WALK_ANIM);
        }
        return state.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------------
    // 远程攻击
    // ------------------------------------------------------------------

    /**
     * 光弹攻击：目标在 3.5~20 格且能看见时，隔一段时间射一发。
     *
     * <p>只占 LOOK 控制，MOVE 留给近战 Goal，所以它是"边追边射"，
     * 不会站在远处变成固定炮台。攻击没有动画，模型保持当前姿势。
     */
    private static class OrbAttackGoal extends Goal {

        private static final double MIN_RANGE = 3.5D;
        private static final double MAX_RANGE = 20.0D;
        /** 两次开火的间隔（tick） */
        private static final int MIN_COOLDOWN = 50;
        private static final int MAX_COOLDOWN = 80;

        private final GhostCrownWraithEntity mob;
        private int cooldown;

        OrbAttackGoal(GhostCrownWraithEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canStart() {
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distance = this.mob.squaredDistanceTo(target);
            if (distance < MIN_RANGE * MIN_RANGE || distance > MAX_RANGE * MAX_RANGE) {
                return false;
            }
            return this.mob.canSee(target);
        }

        @Override
        public boolean shouldContinue() {
            return this.canStart();
        }

        @Override
        public void start() {
            this.cooldown = 15 + this.mob.getRandom().nextInt(15);
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return;
            }
            this.mob.getLookControl().lookAt(target, 30.0F, 30.0F);
            if (--this.cooldown > 0) {
                return;
            }
            this.shoot(target);
            this.cooldown = MIN_COOLDOWN + this.mob.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN + 1);
        }

        /** 只抢朝向，不抢移动 */
        @Override
        public java.util.EnumSet<Control> getControls() {
            return java.util.EnumSet.of(Control.LOOK);
        }

        private void shoot(LivingEntity target) {
            World world = this.mob.getWorld();
            if (world.isClient()) {
                return;
            }
            Vec3d from = new Vec3d(
                    this.mob.getX(),
                    this.mob.getY() + this.mob.getHeight() * 0.65D,
                    this.mob.getZ());
            Vec3d aim = new Vec3d(
                    target.getX(),
                    target.getY() + target.getHeight() * 0.5D,
                    target.getZ());
            Vec3d dir = aim.subtract(from);
            if (dir.lengthSquared() < 1.0E-4D) {
                return;
            }
            world.spawnEntity(GhostOrbEntity.create(world, this.mob, dir));
            world.playSound(null, from.x, from.y, from.z,
                    SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.HOSTILE, 0.9F, 1.15F);
        }
    }
}
