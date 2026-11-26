package org.examplee.wangsword.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
 * 熔岩蝠怪：熔核沉坛里成群的低空飞行精英怪。
 *
 * <p>资源全部是 Blockbench 导出的 Bedrock 格式：
 * {@code geo/caldera.geo.json}、{@code animations/caldera.animation.json}、
 * {@code textures/entity/caldera.png}。动画名就是文件里的顶层键
 * （{@code idle / walk / attack / charge / hurt}），改名的话这里要同步。
 *
 * <p>设计取向是「打不疼但很烦」：单次伤害低，但火球射得快、冲撞频繁、
 * 所有攻击都带点燃。它只在熔核沉坛范围内出现，由唤石方块实体负责补员。
 */
public class CalderaBatEntity extends HostileEntity implements GeoEntity {

    /** 常驻：空中悬停扇翼 */
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    /** 移动：低空滑翔 */
    private static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("walk");
    /** 一次性：喷火球（1.35 秒） */
    private static final RawAnimation SHOOT_ANIM = RawAnimation.begin().thenPlay("attack");
    /** 一次性：俯冲撞击（1.1 秒） */
    private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenPlay("charge");
    /** 一次性：受击（0.45 秒） */
    private static final RawAnimation HURT_ANIM = RawAnimation.begin().thenPlay("hurt");

    /** 喷火球：0.54 秒核心亮到最大，就是这一刻吐出去（11 tick） */
    private static final int SHOOT_RELEASE = 11;
    private static final int SHOOT_END = 27;
    /** 冲撞：5~14 tick 实打实往前扑，22 tick 收势 */
    private static final int CHARGE_DASH_START = 5;
    private static final int CHARGE_DASH_END = 14;
    private static final int CHARGE_END = 22;
    private static final double CHARGE_SPEED = 0.62D;
    /** 冲撞倍率：基础 5 点 → 8 点，撞上不算疼，但会被点着 */
    private static final float CHARGE_DAMAGE = 8.0F;
    /** 火球速度（格/tick） */
    private static final double FIREBALL_SPEED = 0.85D;
    /** 每次命中点燃多少秒 */
    private static final int FIRE_SECONDS = 4;
    /** 熔岩蝠晶的基础掉落概率 */
    private static final float BASE_CRYSTAL_CHANCE = 0.5F;
    /** 抢夺每级额外加的概率 */
    private static final float CRYSTAL_CHANCE_PER_LOOTING = 0.1F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 火球动作起始 age，-1 = 没在放 */
    private int shootStartAge = -1;
    /** 冲撞动作起始 age，-1 = 没在冲 */
    private int chargeStartAge = -1;
    /** 冲撞锁定的方向 */
    private Vec3d chargeDir = Vec3d.ZERO;
    /** 这次冲撞已经撞到过人没有 */
    private boolean chargeHit;
    /** 这次喷火球已经吐出去了没有 */
    private boolean shootDone;

    private int shootCooldown;
    private int chargeCooldown;

    public CalderaBatEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 8;
        // 飞行生物：不吃重力，飞行控制器 + 空中寻路
        this.setNoGravity(true);
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.navigation = new BirdNavigation(this, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                // 血薄、伤害低，但速度快、射得快，靠数量烦人
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 26.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.46D)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new ChargeGoal(this));
        this.goalSelector.add(1, new ShootGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.9D));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 16.0F));
        this.goalSelector.add(5, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ------------------------------------------------------------------
    // 基础属性
    // ------------------------------------------------------------------

    /** 一身熔渣，火免疫 */
    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_BLAZE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_BLAZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_BLAZE_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    /** 空中滑翔不该有脚步声 */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    /**
     * 掉落：岩浆膏 0~2 个 + 熔岩蝠晶。
     *
     * <p>蝠晶基础 50%，抢夺每级 +10%，到 5 级必掉。它是方块实体里的
     * {@link ItemStack}，战利品表读不到，所以在这里手动掉。
     */
    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        if (this.getWorld().isClient() || !this.getWorld().getGameRules()
                .getBoolean(net.minecraft.world.GameRules.DO_MOB_LOOT)) {
            return;
        }
        int cream = this.random.nextInt(3);
        if (cream > 0) {
            this.dropStack(new ItemStack(net.minecraft.item.Items.MAGMA_CREAM, cream));
        }

        int looting = causedByPlayer && source.getAttacker() instanceof LivingEntity killer
                ? net.minecraft.enchantment.EnchantmentHelper.getLooting(killer)
                : 0;
        float chance = Math.min(1.0F, BASE_CRYSTAL_CHANCE + CRYSTAL_CHANCE_PER_LOOTING * looting);
        if (this.random.nextFloat() < chance) {
            this.dropStack(new ItemStack(
                    org.examplee.wangsword.registry.ItemsRegistry.WS_CALDERA_CRYSTAL));
        }
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }
        if (this.shootCooldown > 0) {
            this.shootCooldown--;
        }
        if (this.chargeCooldown > 0) {
            this.chargeCooldown--;
        }
        // 撞到方块侧面时给一点上抬力，不然会顶着墙原地扇翅膀
        if (this.horizontalCollision && !this.isBusy()) {
            this.setVelocity(this.getVelocity().add(0.0D, 0.06D, 0.0D));
        }
        this.tickSkills();
    }

    /**
     * 技能推进。
     *
     * <p>冲撞的位移放在 {@link #tickMovement()} 里，这里只处理时间轴上的判定位。
     */
    private void tickSkills() {
        if (this.shootStartAge >= 0) {
            int t = this.age - this.shootStartAge;
            if (!this.shootDone && t >= SHOOT_RELEASE) {
                this.shootDone = true;
                this.spitFireball();
            }
            if (t >= SHOOT_END) {
                this.shootStartAge = -1;
            }
        }
        if (this.chargeStartAge >= 0) {
            int t = this.age - this.chargeStartAge;
            // 起冲那一帧锁方向；卡帧导致跳过某一 tick 时这里也能补上
            if (t >= CHARGE_DASH_START && this.chargeDir.lengthSquared() < 1.0E-6D) {
                this.chargeDir = horizontalFacing();
                this.chargeHit = false;
            }
            if (t > CHARGE_DASH_START && t <= CHARGE_DASH_END + 2) {
                this.checkChargeImpact();
            }
            if (t >= CHARGE_END) {
                this.chargeStartAge = -1;
            }
        }
    }

    /** 冲撞期间额外位移：直接 move，不和飞行控制器抢速度 */
    @Override
    public void tickMovement() {
        super.tickMovement();
        if (this.getWorld().isClient() || this.chargeStartAge < 0) {
            return;
        }
        int t = this.age - this.chargeStartAge;
        if (t <= CHARGE_DASH_START || t > CHARGE_DASH_END) {
            return;
        }
        if (this.chargeDir.lengthSquared() < 1.0E-6D) {
            return;
        }
        this.move(MovementType.SELF, this.chargeDir.multiply(CHARGE_SPEED));
        this.fallDistance = 0.0F;
        if (this.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.FLAME,
                    this.getX(), this.getY() + 0.6D, this.getZ(), 3, 0.35D, 0.25D, 0.35D, 0.02D);
        }
    }

    /** 冲撞命中：贴到人就结算一次，然后立刻收势 */
    private void checkChargeImpact() {
        if (this.chargeHit || !(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Box hitBox = this.getBoundingBox().expand(0.5D);
        for (Entity entity : world.getOtherEntities(this, hitBox)) {
            if (!(entity instanceof LivingEntity target) || target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && player.isSpectator()) {
                continue;
            }
            // 蝠怪之间不互相撞，免得一冲就误伤同伴
            if (target instanceof CalderaBatEntity) {
                continue;
            }
            this.chargeHit = true;
            target.damage(this.getDamageSources().mobAttack(this), CHARGE_DAMAGE);
            target.setOnFireFor(FIRE_SECONDS + 1);
            Vec3d push = target.getPos().subtract(this.getPos());
            if (push.lengthSquared() > 1.0E-4D) {
                push = push.normalize().multiply(0.55D);
                target.addVelocity(push.x, 0.3D, push.z);
                target.velocityModified = true;
            }
            world.spawnParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getHeight() * 0.6D, target.getZ(),
                    10, 0.3D, 0.3D, 0.3D, 0.08D);
            this.playSound(SoundEvents.ENTITY_BLAZE_HURT, 1.0F, 0.7F);
            // 撞完就停：把剩下的冲刺帧掐掉
            this.chargeStartAge = this.age - CHARGE_DASH_END - 1;
            break;
        }
    }

    /** 近身啃咬也带火 */
    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            target.setOnFireFor(FIRE_SECONDS);
        }
        return hit;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hit = super.damage(source, amount);
        if (hit && !this.getWorld().isClient()) {
            this.triggerAnim("main", "hurt");
        }
        return hit;
    }

    // ------------------------------------------------------------------
    // 动作
    // ------------------------------------------------------------------

    private boolean isBusy() {
        return this.shootStartAge >= 0 || this.chargeStartAge >= 0;
    }

    /** 开始喷火球，返回是否起手成功 */
    private boolean beginShoot() {
        if (this.isBusy()) {
            return false;
        }
        this.shootStartAge = this.age;
        this.shootDone = false;
        this.triggerAnim("main", "attack");
        return true;
    }

    /** 开始俯冲冲撞 */
    private boolean beginCharge() {
        if (this.isBusy()) {
            return false;
        }
        this.chargeStartAge = this.age;
        this.chargeDir = Vec3d.ZERO;
        this.chargeHit = false;
        this.triggerAnim("main", "charge");
        this.playSound(SoundEvents.ENTITY_BLAZE_BURN, 1.0F, 0.75F);
        return true;
    }

    /** 从头顶吐出一颗火球，方向锁在开火那一刻 */
    private void spitFireball() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        // 模型缩到 0.8 倍后口部高度约 0.6 格
        Vec3d origin = new Vec3d(this.getX(), this.getY() + 0.6D, this.getZ());
        Vec3d aim = new Vec3d(
                target.getX() - origin.x,
                target.getY() + target.getHeight() * 0.5D - origin.y,
                target.getZ() - origin.z);
        if (aim.lengthSquared() < 1.0E-4D) {
            return;
        }
        Vec3d dir = aim.normalize();
        world.spawnEntity(CinderFireballEntity.create(world, this, origin, dir, FIREBALL_SPEED));
        world.spawnParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z,
                12, 0.3D, 0.3D, 0.3D, 0.04D);
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 0.9F, 1.35F);
    }

    private Vec3d horizontalFacing() {
        Vec3d look = this.getRotationVec(1.0F);
        Vec3d flat = new Vec3d(look.x, 0.0D, look.z);
        return flat.lengthSquared() < 1.0E-6D ? new Vec3d(0.0D, 0.0D, 1.0D) : flat.normalize();
    }

    // ------------------------------------------------------------------
    // 动画
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::mainController)
                .triggerableAnim("attack", SHOOT_ANIM)
                .triggerableAnim("charge", CHARGE_ANIM)
                .triggerableAnim("hurt", HURT_ANIM));
    }

    private PlayState mainController(AnimationState<CalderaBatEntity> state) {
        if (state.isMoving() || state.getLimbSwingAmount() > 0.01F) {
            return state.setAndContinue(FLY_ANIM);
        }
        return state.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------------
    // 技能 Goal
    // ------------------------------------------------------------------

    /** 冲撞：3~14 格之间、看得见、冷却好了就俯冲过去 */
    private static class ChargeGoal extends Goal {

        private static final double MIN_RANGE = 3.0D;
        private static final double MAX_RANGE = 14.0D;
        private static final int MIN_COOLDOWN = 90;
        private static final int MAX_COOLDOWN = 130;

        private final CalderaBatEntity mob;

        ChargeGoal(CalderaBatEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canStart() {
            if (this.mob.getWorld().isClient() || this.mob.isBusy() || this.mob.chargeCooldown > 0) {
                return false;
            }
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distSq = this.mob.squaredDistanceTo(target);
            return distSq >= MIN_RANGE * MIN_RANGE && distSq <= MAX_RANGE * MAX_RANGE;
        }

        @Override
        public void start() {
            if (this.mob.beginCharge()) {
                this.mob.chargeCooldown = MIN_COOLDOWN
                        + this.mob.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN + 1);
            }
        }

        @Override
        public boolean shouldContinue() {
            return this.mob.chargeStartAge >= 0;
        }

        @Override
        public java.util.EnumSet<Control> getControls() {
            // 冲刺期间不许别的 Goal 抢移动和朝向
            return java.util.EnumSet.of(Control.MOVE, Control.LOOK);
        }
    }

    /** 火球：4~20 格之间、看得见就吐，冷却短，射得勤 */
    private static class ShootGoal extends Goal {

        private static final double MIN_RANGE = 4.0D;
        private static final double MAX_RANGE = 20.0D;
        private static final int MIN_COOLDOWN = 45;
        private static final int MAX_COOLDOWN = 70;

        private final CalderaBatEntity mob;

        ShootGoal(CalderaBatEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canStart() {
            if (this.mob.getWorld().isClient() || this.mob.isBusy() || this.mob.shootCooldown > 0) {
                return false;
            }
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distSq = this.mob.squaredDistanceTo(target);
            if (distSq < MIN_RANGE * MIN_RANGE || distSq > MAX_RANGE * MAX_RANGE) {
                return false;
            }
            return this.mob.canSee(target);
        }

        @Override
        public void start() {
            if (this.mob.beginShoot()) {
                this.mob.shootCooldown = MIN_COOLDOWN
                        + this.mob.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN + 1);
            }
        }

        @Override
        public boolean shouldContinue() {
            return this.mob.shootStartAge >= 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target != null) {
                this.mob.getLookControl().lookAt(target, 30.0F, 30.0F);
            }
        }

        @Override
        public java.util.EnumSet<Control> getControls() {
            return java.util.EnumSet.of(Control.LOOK);
        }
    }
}
