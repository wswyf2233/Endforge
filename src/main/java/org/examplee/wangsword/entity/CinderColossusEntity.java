package org.examplee.wangsword.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.examplee.wangsword.registry.BlocksRegistry;
import org.examplee.wangsword.registry.EntityRegistry;
import org.examplee.wangsword.registry.ItemsRegistry;
import org.examplee.wangsword.registry.SoundRegistry;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * 熔核巨兽：熔核沉坛的 BOSS。
 *
 * <p>亡灵生物，血量 400、护甲 12。核心机制是「只认封岩巨剑」：除了玩家手持
 * {@code ws_sealed_rock_greatsword} 打出的伤害之外，其余任何来源（别的武器、
 * 投射物、火焰、摔落、药水）都只按 20% 结算。这是把封岩巨剑的锻造链和
 * 下界 BOSS 绑在一起，不让玩家拿把普通钻石剑就磨过去。
 *
 * <p>近战有六套动作，全部走 {@code triggerableAnim} 播放在 body 层，
 * 判定由实体自己按 {@code age} 对齐动画时间轴结算（腿层在出招期间 STOP，
 * 否则 walk 的腿会和挥砍的腿打架）。所有招式命中都会点燃目标——它一身熔渣，
 * 刀风带火是设定的一部分：
 * <table border="1">
 *   <tr><th>动画</th><th>招式</th><th>特点</th></tr>
 *   <tr><td>melee</td><td>右臂重劈</td><td>19 tick 结算，落刀触发地震：全向震击 + 地裂扩散</td></tr>
 *   <tr><td>melee2</td><td>转身横扫</td><td>宽扇面、强击退</td></tr>
 *   <tr><td>melee3</td><td>践踏</td><td>1s 动作，0.7s 落地，全向震击</td></tr>
 *   <tr><td>melee4</td><td>双臂连砸</td><td>两段伤害，11 / 19 tick</td></tr>
 *   <tr><td>melee5</td><td>蓄力重砸</td><td>伤害最高，把目标挑飞</td></tr>
 *   <tr><td>melee6</td><td>长距冲锋</td><td>循环跑步动作，沿途破坏方块，撞停为止</td></tr>
 * </table>
 */
public class CinderColossusEntity extends HostileEntity implements GeoEntity {

    /** 击杀经验 */
    private static final int XP_TO_DROP = 60;

    /** 非封岩巨剑来源的伤害倍率 */
    private static final float FOREIGN_DAMAGE_MULTIPLIER = 0.2F;

    /** 血条可见半径 */
    private static final double BOSS_BAR_RANGE = 64.0D;

    /** 兜底索敌半径，和 GENERIC_FOLLOW_RANGE 对齐 */
    private static final double TARGET_RANGE = 40.0D;

    // ------------------------------------------------------------------
    // 动画
    // ------------------------------------------------------------------

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    /** 右臂重劈 */
    private static final RawAnimation SLAM_ANIM = RawAnimation.begin().thenPlay("melee");
    /** 转身横扫 */
    private static final RawAnimation SWEEP_ANIM = RawAnimation.begin().thenPlay("melee2");
    /** 践踏：抬脚砸地 */
    private static final RawAnimation STOMP_ANIM = RawAnimation.begin().thenPlay("melee3");
    /** 双臂连砸 */
    private static final RawAnimation DOUBLE_ANIM = RawAnimation.begin().thenPlay("melee4");
    /** 蓄力重砸 */
    private static final RawAnimation QUAKE_ANIM = RawAnimation.begin().thenPlay("melee5");
    /**
     * 长距冲锋。导出时勾了 loop（上半身定格前倾、腿循环跑），
     * 所以冲锋结束必须显式 stopTriggeredAnimation，否则人物会站着跑步。
     */
    private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenLoop("melee6");
    /** 残血狂暴冲锋：定格的前倾姿势，位移由代码推 */
    private static final RawAnimation EXECUTION_ANIM = RawAnimation.begin().thenLoop("melee7");
    /** attack3：双手托火再前推，hold_on_last_frame，收招要主动停 */
    private static final RawAnimation FIREBALL_ANIM = RawAnimation.begin().thenPlay("attack3");
    /** attack1：头部火焰激光。导出时是 hold_on_last_frame，收招要手动停 */
    private static final RawAnimation BEAM1_ANIM = RawAnimation.begin().thenPlayAndHold("attack1");
    /** attack2：胸口核心的粗激光 */
    private static final RawAnimation BEAM2_ANIM = RawAnimation.begin().thenPlay("attack2");

    // ------------------------------------------------------------------
    // 招式编号
    // ------------------------------------------------------------------

    private static final int STEP_SLAM = 0;
    private static final int STEP_SWEEP = 1;
    private static final int STEP_STOMP = 2;
    private static final int STEP_DOUBLE = 3;
    private static final int STEP_QUAKE = 4;
    private static final int STEP_CHARGE = 5;
    /** 远程：attack1 头部激光 */
    private static final int STEP_BEAM1 = 6;
    /** 远程：attack2 胸口核心粗激光 */
    private static final int STEP_BEAM2 = 7;
    /** 残血必杀：无视地形直冲，撞上就大爆炸 */
    private static final int STEP_EXECUTION = 8;
    /** attack3：双手前推，扇形甩火球 */
    private static final int STEP_FIREBALL = 9;

    // ------------------------------------------------------------------
    // 节奏与判定
    // ------------------------------------------------------------------

    /** 近身招式的触发距离（中心距，格） */
    private static final double NEAR_RANGE = 6.0D;
    /** 走位时停在多远（留出挥砍空间） */
    private static final double KEEP_DISTANCE = 4.2D;
    /** 追击半径，超出就不管了 */
    private static final double CHASE_RANGE = 40.0D;

    /** 一轮出完后停手多久 */
    private static final int ATTACK_COOLDOWN = 40;
    /** 践踏冷却 */
    private static final int STOMP_COOLDOWN = 110;
    /** 重砸冷却 */
    private static final int QUAKE_COOLDOWN = 170;
    /** 长距冲锋冷却 */
    private static final int CHARGE_COOLDOWN = 220;
    /** attack1 冷却（7.5s） */
    private static final int BEAM1_COOLDOWN = 150;
    /** attack2 冷却（11.5s），比 attack1 长，粗激光不能连放 */
    private static final int BEAM2_COOLDOWN = 230;

    /** attack1：2.5s = 50 tick，0.8s（16 tick）从头部开始喷 */
    private static final int BEAM1_FIRE = 16;
    private static final int BEAM1_END = 50;
    /** attack2：3s = 60 tick，1.1s（22 tick）从胸口核心开始喷 */
    private static final int BEAM2_FIRE = 22;
    private static final int BEAM2_END = 60;
    /** 远程招式的起手距离：太近了挥刀更划算，太远了射线也够不着 */
    private static final double BEAM_MIN_RANGE = 8.0D;
    private static final double BEAM_MAX_RANGE = 30.0D;

    /** melee：手臂 1.0s 挥到底，命中帧 19 / 全长 31 tick */
    private static final int SLAM_HIT = 19;
    private static final int SLAM_END = 31;
    /** melee2：1.125s 转过肩，命中帧 20 / 全长 32 */
    private static final int SWEEP_HIT = 20;
    private static final int SWEEP_END = 32;
    /** melee3：1s = 20 tick，0.7s（14 tick）双脚踏地 */
    private static final int STOMP_HIT = 14;
    private static final int STOMP_END = 21;
    /** melee4：1.25s = 25 tick，双臂 0.65s / 0.95s 各砸一下 */
    private static final int DOUBLE_HIT_1 = 13;
    private static final int DOUBLE_HIT_2 = 19;
    private static final int DOUBLE_END = 26;
    /** melee5：1.75s = 35 tick，慢刀 1.25s 才落 */
    private static final int QUAKE_HIT = 25;
    private static final int QUAKE_END = 34;

    /** melee6：10 tick 蓄势后起步，最多冲 46 tick */
    private static final int CHARGE_WINDUP = 10;
    private static final int CHARGE_MAX_TICKS = 46;
    private static final double CHARGE_SPEED = 0.66D;
    /** 冲锋最远距离（格） */
    private static final double CHARGE_MAX_DISTANCE = 26.0D;
    /** 冲锋的起动距离：太近了反而撞不到人 */
    private static final double CHARGE_PICK_RANGE = 16.0D;

    /** melee7：残血后 12 tick 蓄势，然后无视地形直冲 */
    private static final int EXECUTION_WINDUP = 12;
    private static final int EXECUTION_MAX_TICKS = 32;
    private static final double EXECUTION_SPEED = 0.88D;
    private static final int EXECUTION_COOLDOWN = 600;
    /** 血量比例低于这个值才会解锁这招 */
    private static final float EXECUTION_HEALTH_RATIO = 0.35F;
    /** 起手距离上限 */
    private static final double EXECUTION_RANGE = 30.0D;
    /** 终结爆炸的半径与伤害倍率 */
    private static final double EXECUTION_BLAST_RADIUS = 9.0D;
    private static final float EXECUTION_DAMAGE_RATIO = 2.6F;
    /** 冲锋时每 tick 最多拆多少个方块（防止地形过密时掉帧） */
    private static final int EXECUTION_BREAK_LIMIT = 240;

    /** attack3：1s = 20 tick，0.7s（14 tick）双手推出火球 */
    private static final int FIREBALL_CAST = 14;
    private static final int FIREBALL_END = 21;
    private static final int FIREBALL_COOLDOWN = 200;
    /** 起手距离：太近了照样砍，太远了火球下坠打不准 */
    private static final double FIREBALL_MIN_RANGE = 6.0D;
    private static final double FIREBALL_MAX_RANGE = 28.0D;
    private static final int FIREBALL_COUNT = 5;
    /** 相邻两颗火球的夹角（度），5 发就是左右各 40 度 */
    private static final double FIREBALL_SPREAD_DEG = 20.0D;
    private static final double FIREBALL_SPEED = 0.95D;

    /** 冲锋的伤害结算范围（中心距），命中判定另有包围盒接触 */
    private static final double IMPACT_RADIUS = 4.6D;
    /** 践踏伤害倍率（基础 18 → 20.7） */
    private static final float STOMP_DAMAGE_RATIO = 1.15F;
    /** 冲锋伤害倍率（基础 18 → 32.4） */
    private static final float CHARGE_DAMAGE_RATIO = 1.8F;
    /** 重砸伤害倍率 */
    private static final float QUAKE_DAMAGE_RATIO = 1.6F;
    /** 双臂连砸每段倍率 */
    private static final float DOUBLE_DAMAGE_RATIO = 0.62F;

    /** 冲锋破方块：单次最多拆多少个，以及硬度上限 */
    private static final int CRUSH_LIMIT = 80;
    private static final float CRUSH_MAX_HARDNESS = 12.0F;

    // ------------------------------------------------------------------
    // 地震（melee 专属）与火焰附加
    // ------------------------------------------------------------------

    /** 地震波扩到多远 */
    private static final double QUAKE_RADIUS = 7.0D;
    /** 地震波扩散用多少 tick */
    private static final int QUAKE_TICKS = 14;
    /** 地震的二次伤害倍率（主刀之外再震一下）。注意别跟重砸的 QUAKE_DAMAGE_RATIO 混了 */
    private static final float QUAKE_SHOCK_DAMAGE_RATIO = 0.5F;
    /** 地震把人从地上掀起来的初速度 */
    private static final double QUAKE_LIFT = 0.34D;
    /** 地震的减速等级与时长（缓慢 II，3 秒） */
    private static final int QUAKE_SLOW_TICKS = 60;
    private static final int QUAKE_SLOW_AMPLIFIER = 1;
    /** 地震的眩晕时长（反胃会让视野晃，配合减速就是"站不稳"） */
    private static final int QUAKE_NAUSEA_TICKS = 45;

    /** 普通攻击点燃秒数 */
    private static final int FIRE_SECONDS = 5;
    /** 重击/冲锋点燃秒数 */
    private static final int HEAVY_FIRE_SECONDS = 8;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 血条。只在服务端创建，惰性初始化 */
    private ServerBossBar bossBar;

    // ---- 战斗状态 ----
    /** 一次性招式的起始 age；-1 = 没在出招 */
    private int meleeStartAge = -1;
    /** 当前招式，见 STEP_* */
    private int meleeStep = STEP_SLAM;
    /** 单段招式的伤害是否已结算，防止 age 跳帧导致重复打 */
    private boolean meleeHitDone;
    /** 双臂连砸已经打了几段 */
    private int doubleHits;
    /** 冲锋锁定方向 */

    /** 冲锋的起始 age；-1 = 没在冲 */
    private int chargeStartAge = -1;
    /** 冲锋锁定方向 */
    private Vec3d chargeDir = Vec3d.ZERO;
    /** 这次冲锋已经跑了多远 */
    private double chargeTravel;

    /** 地震波扩散的起始 age；-1 = 没在震 */
    private int quakeShockAge = -1;

    private int attackCooldown;
    private int stompCooldown;
    private int executionCooldown;
    private int fireballCooldown;
    /** 狂暴冲锋的锁定方向 */
    private Vec3d executionDir = Vec3d.ZERO;
    private int quakeCooldown;
    private int chargeCooldown;
    private int beam1Cooldown;
    private int beam2Cooldown;

    public CinderColossusEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        // 三格多高的块头，一格台阶不该把它卡住
        this.setStepHeight(1.0F);
        this.experiencePoints = XP_TO_DROP;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 400.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 18.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.24D)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.0D)
                // 熔渣外壳：12 点护甲 + 8 点韧性，普通武器打上去基本没感觉
                .add(EntityAttributes.GENERIC_ARMOR, 12.0D)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 8.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0D);
    }

    /** 亡灵：亡灵杀手附魔对它加伤，治疗药水伤它、伤害药水治它 */
    @Override
    public EntityGroup getGroup() {
        return EntityGroup.UNDEAD;
    }

    /** 一坨烧红的熔渣，当然不怕火 */
    @Override
    public boolean isFireImmune() {
        return true;
    }

    /** 必掉熔岩核心：封岩巨剑最后一道材料，不管怎么死都掉一枚 */
    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        this.dropStack(new ItemStack(ItemsRegistry.WS_MOLTEN_CORE));
    }

    @Override
    protected void initGoals() {
        // 远程优先：站远了先放激光，贴近了才动刀
        this.goalSelector.add(0, new ColossusBeamGoal(this));
        // 出招 > 走位 > 闲逛。原来的 MeleeAttackGoal 只有一下平A，
        // 六套动作全靠自己按 tick 结算，所以整个替换掉
        this.goalSelector.add(1, new ColossusMeleeGoal(this));
        this.goalSelector.add(2, new ColossusApproachGoal(this));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.8D));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 24.0F));
        this.goalSelector.add(8, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        // 默认那个三参构造器带的是 EXCEPT_CREATIVE_OR_SPECTATOR，
        // 创造模式测 BOSS 时它一个目标都找不到，只会站在原地发呆。
        // 这里自己给谓词：只排除旁观者，创造模式照打。
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                living -> !(living instanceof PlayerEntity player) || !player.isSpectator()));
    }

    // ------------------------------------------------------------------
    // 伤害规则
    // ------------------------------------------------------------------

    /**
     * 只有封岩巨剑打出全额伤害，其余来源一律 ×0.2。
     *
     * <p>放在 {@code damage} 里而不是护甲值上，是因为要区分「谁用什么打的」；
     * 护甲是统一减伤，拦不住「换了把武器就能正常打」这件事。
     */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (amount > 0.0F && !isSealedRockSource(source)) {
            amount *= FOREIGN_DAMAGE_MULTIPLIER;
        }
        return super.damage(source, amount);
    }

    /** 判断这一下是不是封岩巨剑打的：直接近战看手持，投射物看发射者手持 */
    private static boolean isSealedRockSource(DamageSource source) {
        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity living && isWieldingSealedRock(living)) {
            return true;
        }
        Entity direct = source.getSource();
        if (direct instanceof ProjectileEntity projectile
                && projectile.getOwner() instanceof LivingEntity owner) {
            return isWieldingSealedRock(owner);
        }
        return false;
    }

    private static boolean isWieldingSealedRock(LivingEntity entity) {
        ItemStack main = entity.getMainHandStack();
        if (isSealedBlade(main)) {
            return true;
        }
        return isSealedBlade(entity.getOffHandStack());
    }

    /** 封岩系两把：原版封岩巨剑和吸收熔岩核心后的熔核封岩巨剑 */
    private static boolean isSealedBlade(ItemStack stack) {
        return stack.isOf(ItemsRegistry.WS_SEALED_ROCK_GREATSWORD)
                || stack.isOf(ItemsRegistry.WS_MOLTEN_SEALED_GREATSWORD);
    }

    // ------------------------------------------------------------------
    // 战斗驱动
    // ------------------------------------------------------------------

    /**
     * 是否正在出招。
     *
     * <p>不能叫 {@code isAttacking}：{@code MobEntity} 已经有一个 public 的同名方法
     * （给原版 AI 用的"正在攻击目标"），私有方法同名会被判定为"降低访问权限"。
     */
    private boolean isPerformingAttack() {
        return this.meleeStartAge >= 0 || this.chargeStartAge >= 0;
    }

    /** 残血大招是否可用：血量掉到阈值以下、且不在冷却 */
    private boolean isExecutionReady() {
        return this.executionCooldown <= 0
                && this.getHealth() <= this.getMaxHealth() * EXECUTION_HEALTH_RATIO;
    }

    private void tickCooldowns() {
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.stompCooldown > 0) {
            this.stompCooldown--;
        }
        if (this.executionCooldown > 0) {
            this.executionCooldown--;
        }
        if (this.fireballCooldown > 0) {
            this.fireballCooldown--;
        }
        if (this.quakeCooldown > 0) {
            this.quakeCooldown--;
        }
        if (this.chargeCooldown > 0) {
            this.chargeCooldown--;
        }
        if (this.beam1Cooldown > 0) {
            this.beam1Cooldown--;
        }
        if (this.beam2Cooldown > 0) {
            this.beam2Cooldown--;
        }
    }

    /**
     * 挑一招。
     *
     * <p>远距离优先冲锋，贴身优先践踏，中距离才轮到挥砍。
     * 大招收招长，各自带独立冷却，避免连着两轮都放同一招。
     */
    private int chooseStep(double distance) {
        // 残血后优先放大招，30 格内都直接起手
        if (this.isExecutionReady() && distance <= EXECUTION_RANGE) {
            return STEP_EXECUTION;
        }
        if (distance > CHARGE_PICK_RANGE * 0.68D && this.chargeCooldown <= 0) {
            return STEP_CHARGE;
        }
        // 中远距离：召唤火球覆盖
        if (distance >= FIREBALL_MIN_RANGE && distance <= FIREBALL_MAX_RANGE
                && this.fireballCooldown <= 0) {
            return STEP_FIREBALL;
        }
        // 践踏是站桩技：站在脚下才踩，玩家跑出 6 格就躲得掉
        if (distance <= 6.0D && this.stompCooldown <= 0) {
            return STEP_STOMP;
        }
        int roll = this.random.nextInt(100);
        if (roll < 28) {
            return STEP_SLAM;
        }
        if (roll < 54) {
            return STEP_SWEEP;
        }
        if (roll < 78) {
            return STEP_DOUBLE;
        }
        if (this.quakeCooldown <= 0) {
            return STEP_QUAKE;
        }
        return STEP_SLAM;
    }

    /** 两个远程招里挑一个：attack2 更痛，冷却好了就优先 */
    private int chooseBeam() {
        boolean headReady = this.beam1Cooldown <= 0;
        boolean coreReady = this.beam2Cooldown <= 0;
        if (coreReady && (!headReady || this.random.nextBoolean())) {
            return STEP_BEAM2;
        }
        return headReady ? STEP_BEAM1 : STEP_BEAM2;
    }

    /**
     * 放出激光实体。
     *
     * <p>方向和朝向在 spawn 之前就写好（{@link CinderBeamEntity#lockDirection}），
     * 这样客户端收到的第一帧就是正确朝向，不会出现"光束先歪一下再正过来"。
     */
    private void spawnBeam(int kind, int life) {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        CinderBeamEntity beam = new CinderBeamEntity(EntityRegistry.CINDER_BEAM, world);
        beam.setKind(kind);
        beam.setMaxAge(life);
        beam.setOwnerId(this.getId());
        // 用"从发射点指向目标胸口"的向量，而不是实体的视线向量：
        // 巨兽头在 6.9 格高，玩家只有 1.8 格，只用 yaw 的话激光会从玩家头顶平着飞过去
        Vec3d aim = this.aimDirection(kind);
        beam.lockDirection(aim, yawOf(aim), pitchOf(aim));
        beam.setPosition(this.getX(), this.getY(), this.getZ());
        world.spawnEntity(beam);

        this.playSound(SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.4F, 0.65F);
        if (kind == CinderBeamEntity.KIND_CORE) {
            world.spawnParticles(ParticleTypes.FLASH,
                    this.getX(), this.getY() + CinderBeamEntity.CORE_HEIGHT, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * melee4 每一锤落地时，从自己脚下推出一圈火焰冲击波。
     *
     * <p>本体那下扇形结算只打身前，冲击波补的是"整圈扩散"：
     * 站位在侧面、背后的也会被火浪扫到，逼着人往外跑而不是贴着打。
     *
     * @param maxRadius   扩散到的最终半径（格）
     * @param duration    扩散时长（tick）
     * @param ratio       伤害倍率，攻击力 × ratio
     * @param fireSeconds 点燃秒数
     */
    private void spawnFlameWave(float maxRadius, int duration, float ratio, int fireSeconds) {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * ratio;
        FlameShockwaveEntity wave = new FlameShockwaveEntity(
                EntityRegistry.FLAME_SHOCKWAVE, world, this, maxRadius, duration, damage, fireSeconds);
        wave.setPosition(this.getX(), this.getY() + 0.05D, this.getZ());
        world.spawnEntity(wave);
    }

    /**
     * attack3：双手前推，朝目标甩出 5 颗火球。
     *
     * <p>以「胸口 → 目标腰部」为主方向，左右各偏 20/40 度；
     * 玩家站着不动最多吃到一两颗，横向拉开才能全躲。
     */
    private void castFireballs() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        Vec3d origin = new Vec3d(this.getX(), this.getY() + 3.9D, this.getZ());
        Vec3d aim = new Vec3d(
                target.getX() - origin.x,
                target.getY() + target.getHeight() * 0.5D - origin.y,
                target.getZ() - origin.z);
        if (aim.lengthSquared() < 1.0E-4D) {
            return;
        }
        Vec3d base = aim.normalize();

        double half = (FIREBALL_COUNT - 1) / 2.0D;
        for (int i = 0; i < FIREBALL_COUNT; i++) {
            double degrees = (i - half) * FIREBALL_SPREAD_DEG;
            Vec3d dir = rotateY(base, Math.toRadians(degrees));
            world.spawnEntity(CinderFireballEntity.create(world, this, origin, dir, FIREBALL_SPEED));
        }

        this.playSound(SoundEvents.ENTITY_GHAST_SHOOT, 1.6F, 0.65F);
        this.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.4F, 0.7F);
        world.spawnParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z,
                24, 1.2D, 0.8D, 1.2D, 0.06D);
    }

    /**
     * 激光的发射方向：发射点 → 目标胸口。
     *
     * <p>必须带俯仰角。之前直接用 {@code getRotationVec}（只有 yaw，pitch 恒为 0），
     * 激光就是一条从 6.9 格高处水平射出的线，十几格外正好从站着的玩家头顶掠过，
     * 看着就是"根本没瞄准"。
     *
     * <p>没有目标时退回视线方向，至少不会朝自己脚下打。
     */
    private Vec3d aimDirection(int kind) {
        double height = kind == CinderBeamEntity.KIND_CORE
                ? CinderBeamEntity.CORE_HEIGHT : CinderBeamEntity.HEAD_HEIGHT;
        LivingEntity target = this.getTarget();
        if (target == null) {
            return this.getRotationVec(1.0F);
        }
        Vec3d origin = new Vec3d(this.getX(), this.getY() + height, this.getZ());
        Vec3d toTarget = new Vec3d(target.getX(),
                target.getY() + target.getHeight() * 0.6D, target.getZ()).subtract(origin);
        if (toTarget.lengthSquared() < 1.0E-4D) {
            return this.getRotationVec(1.0F);
        }
        return toTarget.normalize();
    }

    /** 由方向反推 Minecraft 的 yaw（x = -sin(yaw)cos(pitch)、z = cos(yaw)cos(pitch)） */
    private static float yawOf(Vec3d dir) {
        return (float) (MathHelper.atan2(-dir.x, dir.z) * (180.0D / Math.PI));
    }

    /** 由方向反推 pitch（y = -sin(pitch)，正值向下） */
    private static float pitchOf(Vec3d dir) {
        double clamped = MathHelper.clamp(dir.y, -1.0D, 1.0D);
        return (float) (-Math.asin(clamped) * (180.0D / Math.PI));
    }

    /** 起手：播动画 + 记录时间轴起点 */
    private void startAttack(int step) {
        this.getNavigation().stop();
        this.meleeStep = step;
        this.meleeHitDone = false;
        this.doubleHits = 0;
        this.attackCooldown = ATTACK_COOLDOWN;

        switch (step) {
            case STEP_SWEEP -> {
                this.meleeStartAge = this.age;
                this.triggerAnim("body", "melee2");
            }
            case STEP_STOMP -> {
                this.meleeStartAge = this.age;
                this.stompCooldown = STOMP_COOLDOWN;
                this.triggerAnim("body", "melee3");
                this.playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.5F, 0.7F);
            }
            case STEP_DOUBLE -> {
                this.meleeStartAge = this.age;
                this.triggerAnim("body", "melee4");
            }
            case STEP_QUAKE -> {
                this.meleeStartAge = this.age;
                this.quakeCooldown = QUAKE_COOLDOWN;
                this.triggerAnim("body", "melee5");
                this.playSound(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 1.6F, 0.5F);
            }
            case STEP_CHARGE -> {
                this.chargeStartAge = this.age;
                this.chargeTravel = 0.0D;
                this.chargeDir = Vec3d.ZERO;
                this.chargeCooldown = CHARGE_COOLDOWN;
                this.triggerAnim("body", "melee6");
            }
            case STEP_FIREBALL -> {
                this.meleeStartAge = this.age;
                this.fireballCooldown = FIREBALL_COOLDOWN;
                this.triggerAnim("body", "attack3");
                this.playSound(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 1.5F, 0.6F);
            }
            case STEP_EXECUTION -> {
                this.meleeStartAge = this.age;
                this.executionCooldown = EXECUTION_COOLDOWN;
                this.executionDir = Vec3d.ZERO;
                this.triggerAnim("body", "melee7");
                this.playSound(SoundEvents.ENTITY_WARDEN_ROAR, 1.7F, 0.5F);
                this.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.3F, 0.55F);
            }
            case STEP_BEAM1 -> {
                this.meleeStartAge = this.age;
                this.beam1Cooldown = BEAM1_COOLDOWN;
                this.triggerAnim("body", "attack1");
                this.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.6F, 0.6F);
            }
            case STEP_BEAM2 -> {
                this.meleeStartAge = this.age;
                this.beam2Cooldown = BEAM2_COOLDOWN;
                this.triggerAnim("body", "attack2");
                this.playSound(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 1.5F, 0.55F);
            }
            default -> {
                this.meleeStartAge = this.age;
                this.triggerAnim("body", "melee");
            }
        }
    }

    /** 被打断（目标死亡、被移除等）：收掉状态，避免卡在挥砍姿势 */
    private void cancelAttack() {
        this.meleeStartAge = -1;
        if (this.chargeStartAge >= 0) {
            this.stopTriggeredAnimation("body", "melee6");
        }
        if (this.meleeStep == STEP_EXECUTION) {
            this.stopTriggeredAnimation("body", "melee7");
        }
        if (this.meleeStep == STEP_BEAM1) {
            // hold_on_last_frame 的动作必须主动停，否则会定格
            this.stopTriggeredAnimation("body", "attack1");
        }
        this.chargeStartAge = -1;
        this.meleeHitDone = true;
        this.attackCooldown = Math.max(this.attackCooldown, 20);
        this.setVelocity(this.getVelocity().multiply(0.2D, 1.0D, 0.2D));
        this.velocityModified = true;
    }

    /** 每一 tick 推进一次时间轴；只有服务端跑 */
    private void tickCombat() {
        if (this.getWorld().isClient()) {
            return;
        }
        this.tickCooldowns();
        this.tickQuake();
        if (this.chargeStartAge >= 0) {
            this.tickCharge();
        } else if (this.meleeStartAge >= 0) {
            this.tickMelee();
        }
    }

    private void tickMelee() {
        int t = this.age - this.meleeStartAge;
        LivingEntity target = this.getTarget();
        // 激光开火之后方向已经锁进光束，身体再转只会让光源跟着乱晃
        boolean facingLocked = this.meleeHitDone
                && (this.meleeStep == STEP_BEAM1 || this.meleeStep == STEP_BEAM2);
        if (target != null && target.isAlive() && !facingLocked) {
            this.faceTowards(target);
        }

        switch (this.meleeStep) {
            case STEP_STOMP -> this.tickStomp(t);
            case STEP_EXECUTION -> this.tickExecution(t);
            case STEP_FIREBALL -> {
                if (!this.meleeHitDone && t >= FIREBALL_CAST) {
                    this.meleeHitDone = true;
                    this.castFireballs();
                }
            }
            case STEP_DOUBLE -> {
                if (t >= DOUBLE_HIT_1 && this.doubleHits == 0) {
                    this.doubleHits = 1;
                    this.areaAttack(5.8D, DOUBLE_DAMAGE_RATIO, 0.35D, 0.0D, 0.15D, FIRE_SECONDS);
                    this.impactEffects(3.0D, 10);
                    // 第一锤：小一圈、扩得快
                    this.spawnFlameWave(6.5F, 14, 0.35F, FIRE_SECONDS);
                }
                if (t >= DOUBLE_HIT_2 && this.doubleHits == 1) {
                    this.doubleHits = 2;
                    this.areaAttack(5.8D, DOUBLE_DAMAGE_RATIO, 0.55D, 0.15D, 0.15D, FIRE_SECONDS);
                    this.impactEffects(3.2D, 14);
                    this.playSound(SoundEvents.BLOCK_ANVIL_LAND, 1.3F, 0.6F);
                    // 第二锤：更大、更慢，烧得更久，两层火浪会套在一起
                    this.spawnFlameWave(9.5F, 20, 0.60F, HEAVY_FIRE_SECONDS);
                }
            }
            case STEP_QUAKE -> {
                if (t >= QUAKE_HIT && !this.meleeHitDone) {
                    this.meleeHitDone = true;
                    // 全向重砸：动画里是双脚踏地前压，背后也能被震到
                    this.areaAttack(8.0D, QUAKE_DAMAGE_RATIO, 1.3D, 0.42D, -0.2D, HEAVY_FIRE_SECONDS);
                    this.quakeEffects();
                }
            }
            case STEP_SWEEP -> {
                if (t >= SWEEP_HIT && !this.meleeHitDone) {
                    this.meleeHitDone = true;
                    this.areaAttack(7.2D, 1.12F, 1.25D, 0.0D, 0.05D, FIRE_SECONDS);
                    this.playSound(SoundRegistry.SLASH_IMPACT, 1.6F, 0.7F);
                    this.impactEffects(3.6D, 12);
                }
            }
            case STEP_BEAM1 -> {
                // 前摇一直转头跟枪，开火之后方向就交给光束实体锁死
                if (!this.meleeHitDone && t >= BEAM1_FIRE) {
                    this.meleeHitDone = true;
                    this.spawnBeam(CinderBeamEntity.KIND_HEAD, CinderBeamEntity.HEAD_LIFE);
                }
            }
            case STEP_BEAM2 -> {
                if (!this.meleeHitDone && t >= BEAM2_FIRE) {
                    this.meleeHitDone = true;
                    this.spawnBeam(CinderBeamEntity.KIND_CORE, CinderBeamEntity.CORE_LIFE);
                }
            }
            default -> {
                if (t >= SLAM_HIT && !this.meleeHitDone) {
                    this.meleeHitDone = true;
                    this.areaAttack(6.6D, 1.0F, 0.7D, 0.0D, 0.15D, FIRE_SECONDS);
                    this.playSound(SoundRegistry.SLASH_IMPACT, 1.4F, 0.65F);
                    this.impactEffects(3.4D, 10);
                    // 刀砸进地里：主刀之外再震一圈
                    this.startQuake();
                }
            }
        }

        int end = switch (this.meleeStep) {
            case STEP_STOMP -> STOMP_END;
            // 狂暴冲锋自己管结束时机（撞击爆炸那一帧），别让时间轴提前收
            case STEP_EXECUTION -> Integer.MAX_VALUE;
            case STEP_DOUBLE -> DOUBLE_END;
            case STEP_QUAKE -> QUAKE_END;
            case STEP_SWEEP -> SWEEP_END;
            case STEP_BEAM1 -> BEAM1_END;
            case STEP_BEAM2 -> BEAM2_END;
            case STEP_FIREBALL -> FIREBALL_END;
            default -> SLAM_END;
        };
        if (t >= end) {
            this.meleeStartAge = -1;
            // attack1 是 hold_on_last_frame，不主动停会永远定格在收枪那一帧
            if (this.meleeStep == STEP_BEAM1) {
                this.stopTriggeredAnimation("body", "attack1");
            }
            // attack3 也是 hold_on_last_frame，不主动停会一直举着手
            if (this.meleeStep == STEP_FIREBALL) {
                this.stopTriggeredAnimation("body", "attack3");
            }
        }
    }

    /**
     * 践踏：原地抬脚砸地，脚下整圈吃伤害。
     *
     * <p>1s 的动作，第 0.7s（14 tick）落地结算。没有位移 —— 从冲撞改成
     * 站桩踩地之后，这招不再是「贴脸必中」，玩家跑出 6 格就能躲；
     * 但站在脚下会被主伤害、地震的二次震击和控制三段一起吃掉。
     */
    private void tickStomp(int t) {
        if (t != STOMP_HIT || this.meleeHitDone) {
            return;
        }
        this.meleeHitDone = true;
        // 全向阈值 -0.2：践踏震的是整圈地面，站在背后一样站不稳
        this.areaAttack(6.2D, STOMP_DAMAGE_RATIO, 0.9D, 0.25D, -0.2D, FIRE_SECONDS);
        this.stompEffects();
        // 复用重劈的地震：二次震击 + 缓慢 II + 反胃，视觉上是地裂一圈圈推出去
        this.startQuake();
    }

    /**
     * 狂暴冲锋：蓄势 12 tick，然后无视地形直线碾过去，撞到人或跑满 32 tick
     * 就地引爆。只剩三成血才会解锁，是这场仗的收尾手段。
     */
    private void tickExecution(int t) {
        LivingEntity target = this.getTarget();

        if (t < EXECUTION_WINDUP) {
            if (target != null && target.isAlive()) {
                this.faceTowards(target);
            }
            this.executionWindupEffects();
            return;
        }
        if (t == EXECUTION_WINDUP) {
            this.executionDir = this.horizontalFacing();
            return;
        }
        if (t - EXECUTION_WINDUP > EXECUTION_MAX_TICKS) {
            this.executionExplode();
            return;
        }

        this.executionDash();
        this.crushInevitable();
        this.executionTrail();

        if (!this.meleeHitDone && this.impactTarget(1.0D)) {
            this.meleeHitDone = true;
            this.executionExplode();
        }
    }

    /**
     * 冲锋位移。
     *
     * <p>先用 {@code move} 正常走一步；如果被方块挡住（实际位移不到期望的 30%），
     * 就直接改坐标硬推过去。这一招的设定就是什么都挡不住，配合
     * {@link #crushInevitable()} 把沿途拆空，不会真卡死在石头里。
     */
    private void executionDash() {
        Vec3d step = new Vec3d(this.executionDir.x * EXECUTION_SPEED, 0.0D,
                this.executionDir.z * EXECUTION_SPEED);
        Vec3d before = this.getPos();
        this.move(MovementType.SELF, step);
        if (this.getPos().squaredDistanceTo(before) < step.lengthSquared() * 0.3D) {
            this.setPosition(before.x + step.x, before.y, before.z + step.z);
            this.velocityModified = true;
        }
        this.fallDistance = 0.0F;
    }

    /**
     * 无视硬度拆掉前方一切。
     *
     * <p>和普通冲锋的 {@link #crushAhead()} 区别：黑曜石 50、远古残骸 30 全照拆，
     * 只放过方块实体（箱子、祭坛、召唤石、基座）和基岩 / 屏障 / 末地传送门框。
     */
    private void crushInevitable() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d dir = this.executionDir;
        if (dir.lengthSquared() < 1.0E-6D) {
            return;
        }
        int footY = MathHelper.floor(this.getY());
        Vec3d side = new Vec3d(-dir.z, 0.0D, dir.x);
        int broken = 0;

        for (int fw = 1; fw <= 3; fw++) {
            for (int lateral = -2; lateral <= 2; lateral++) {
                for (int dy = 1; dy <= 4; dy++) {
                    double px = this.getX() + dir.x * fw + side.x * lateral;
                    double pz = this.getZ() + dir.z * fw + side.z * lateral;
                    BlockPos pos = BlockPos.ofFloored(px, footY + dy, pz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || state.hasBlockEntity()) {
                        continue;
                    }
                    if (state.isOf(BlocksRegistry.MOLTEN_CORE_SUMMONER)
                            || state.isOf(BlocksRegistry.SWORD_TOMB_ALTAR)
                            || state.isOf(BlocksRegistry.STONE_SWORD_PEDESTAL)) {
                        continue;
                    }
                    if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.BARRIER)
                            || state.isOf(Blocks.END_PORTAL_FRAME)) {
                        continue;
                    }
                    if (broken >= EXECUTION_BREAK_LIMIT) {
                        return;
                    }
                    world.breakBlock(pos, false, this);
                    world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
                    broken++;
                }
            }
        }
    }

    /** 撞上目标 / 跑满距离后的终结爆炸 */
    private void executionExplode() {
        this.stopTriggeredAnimation("body", "melee7");
        this.meleeStartAge = -1;
        this.meleeHitDone = true;
        this.attackCooldown = ATTACK_COOLDOWN + 30;

        // 本体：9 格全向，攻击力 ×2.6（18 → 46.8）
        this.areaAttack(EXECUTION_BLAST_RADIUS, EXECUTION_DAMAGE_RATIO, 2.6D, 0.8D, -1.0D, 10);
        // 再多铺一圈火浪，爆炸外面还有一层余波
        this.spawnFlameWave(13.0F, 24, 0.9F, 10);

        if (this.getWorld() instanceof ServerWorld world) {
            double px = this.getX();
            double py = this.getY() + 1.6D;
            double pz = this.getZ();
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, px, py, pz, 2, 0.0D, 0.0D, 0.0D, 0.0D);
            world.spawnParticles(ParticleTypes.EXPLOSION, px, py, pz, 14, 3.2D, 1.6D, 3.2D, 0.0D);
            world.spawnParticles(ParticleTypes.FLAME, px, py, pz, 140, 4.8D, 2.0D, 4.8D, 0.12D);
            world.spawnParticles(ParticleTypes.LAVA, px, this.getY() + 0.4D, pz, 70, 4.2D, 0.4D, 4.2D, 0.0D);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, px, py, pz, 45, 4.2D, 1.6D, 4.2D, 0.06D);
        }

        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 2.2F, 0.35F);
        this.playSound(SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.8F, 0.4F);
        this.playSound(SoundEvents.ENTITY_WARDEN_ROAR, 1.6F, 0.45F);
    }

    /** 蓄势：脚下聚火、身上冒烟 */
    private void executionWindupEffects() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        world.spawnParticles(ParticleTypes.FLAME,
                this.getX(), this.getY() + 1.2D, this.getZ(), 10, 2.4D, 1.6D, 2.4D, 0.03D);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                this.getX(), this.getY() + 0.5D, this.getZ(), 5, 2.2D, 0.5D, 2.2D, 0.02D);
        world.spawnParticles(ParticleTypes.LAVA,
                this.getX(), this.getY() + 0.2D, this.getZ(), 2, 2.0D, 0.1D, 2.0D, 0.0D);
    }

    /** 冲锋拖尾：一条火与碎石 */
    private void executionTrail() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        world.spawnParticles(ParticleTypes.FLAME,
                this.getX(), this.getY() + 1.4D, this.getZ(), 12, 2.0D, 1.4D, 2.0D, 0.06D);
        world.spawnParticles(ParticleTypes.CLOUD,
                this.getX(), this.getY() + 0.2D, this.getZ(), 8, 2.0D, 0.2D, 2.0D, 0.07D);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                this.getX(), this.getY() + 1.0D, this.getZ(), 4, 1.6D, 0.8D, 1.6D, 0.03D);
    }

    /**
     * 长距冲锋：一路撞碎方块，直到撞到人、撞到拆不动的墙、或者跑满 26 格。
     *
     * <p>melee6 是循环动画（上半身定格前倾、腿在跑），所以冲锋结束时
     * 必须显式 stopTriggeredAnimation，否则会站在原地跑步。
     */
    private void tickCharge() {
        LivingEntity target = this.getTarget();
        int t = this.age - this.chargeStartAge;

        if (t < CHARGE_WINDUP) {
            if (target != null && target.isAlive()) {
                this.faceTowards(target);
            }
            this.chargeWindupEffects();
            return;
        }
        if (t == CHARGE_WINDUP) {
            this.chargeDir = this.horizontalFacing();
            this.chargeTravel = 0.0D;
            this.playSound(SoundEvents.ENTITY_WARDEN_ROAR, 1.5F, 0.75F);
            this.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.8F);
            return;
        }

        if (target == null || !target.isAlive()) {
            this.endCharge(false);
            return;
        }
        if (t - CHARGE_WINDUP > CHARGE_MAX_TICKS) {
            this.endCharge(false);
            return;
        }

        Vec3d beforePush = this.getPos();
        this.pushForward(this.chargeDir, CHARGE_SPEED);
        this.chargeTravel += this.getPos().subtract(beforePush).length();
        this.chargeEffects();

        boolean crushedIntoWall = this.crushAhead();
        if (!this.meleeHitDone && this.impactTarget(0.6D)) {
            this.meleeHitDone = true;
            this.areaAttack(IMPACT_RADIUS + 0.8D, CHARGE_DAMAGE_RATIO, 2.2D, 0.5D, 0.0D, HEAVY_FIRE_SECONDS);
            this.impactEffects(4.0D, 26);
            this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2F, 0.6F);
            this.endCharge(true);
            return;
        }
        if (crushedIntoWall || this.chargeTravel >= CHARGE_MAX_DISTANCE) {
            this.endCharge(false);
        }
    }

    private void endCharge(boolean hitSomething) {
        this.stopTriggeredAnimation("body", "melee6");
        this.chargeStartAge = -1;
        this.meleeHitDone = true;
        this.attackCooldown = ATTACK_COOLDOWN + 20;
        // 收势：把水平速度咬掉，不然会继续往前滑一段
        Vec3d v = this.getVelocity();
        this.setVelocity(v.x * 0.15D, v.y, v.z * 0.15D);
        this.velocityModified = true;

        if (this.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 0.4D, this.getZ(), 20, 1.6D, 0.3D, 1.6D, 0.04D);
            world.spawnParticles(ParticleTypes.CLOUD,
                    this.getX(), this.getY() + 0.2D, this.getZ(), 14, 1.4D, 0.1D, 1.4D, 0.06D);
        }
        if (hitSomething) {
            this.playSound(SoundRegistry.SLASH_IMPACT, 1.8F, 0.55F);
        } else {
            this.playSound(SoundEvents.BLOCK_ANVIL_LAND, 1.4F, 0.5F);
        }
    }

    // ------------------------------------------------------------------
    // 地震（melee 重劈落地 / melee3 践踏）
    // ------------------------------------------------------------------

    /**
     * 落地触发地震：重劈落刀、践踏踩地都走这里。
     *
     * <p>先做一次全向震击（伤害、掀飞、缓慢、眩晕），再在 {@link #tickQuake()}
     * 里花 14 tick 把地裂从脚下推到 7 格开外。震击不看朝向——这一刀是砸在
     * 地上，站背后的人一样站不稳。
     */
    private void startQuake() {
        this.quakeShockAge = this.age;
        this.quakeShockwave();
        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.4F, 0.45F);
        this.playSound(SoundEvents.ENTITY_WARDEN_ROAR, 1.1F, 0.6F);
    }

    /** 地震的伤害与状态结算：全向，越近掀得越高、推得越远 */
    private void quakeShockwave() {
        if (this.getWorld().isClient()) {
            return;
        }
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                * QUAKE_SHOCK_DAMAGE_RATIO;

        for (Entity entity : this.getWorld().getOtherEntities(
                this, this.getBoundingBox().expand(QUAKE_RADIUS))) {
            if (!(entity instanceof LivingEntity target) || target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && player.isSpectator()) {
                continue;
            }
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > QUAKE_RADIUS) {
                continue;
            }
            target.timeUntilRegen = 0;
            if (!target.damage(this.getDamageSources().mobAttack(this), damage)) {
                continue;
            }
            // 距离衰减：脚下的人被整个掀起来，圈边的人只是被晃一下
            double falloff = 1.0D - (dist / QUAKE_RADIUS) * 0.6D;
            target.takeKnockback(0.9D * falloff, -dx, -dz);
            Vec3d velocity = target.getVelocity();
            target.setVelocity(velocity.x, velocity.y + QUAKE_LIFT * falloff, velocity.z);
            target.velocityModified = true;
            // 火是附加的，重点是站不稳：缓慢 II + 反胃（视野晃）
            target.setOnFireFor(FIRE_SECONDS);
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, QUAKE_SLOW_TICKS, QUAKE_SLOW_AMPLIFIER), this);
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NAUSEA, QUAKE_NAUSEA_TICKS, 0), this);
        }
    }

    /** 地震的视觉：一圈地裂逐 tick 往外推，混着尘柱与火星 */
    private void tickQuake() {
        if (this.quakeShockAge < 0) {
            return;
        }
        int t = this.age - this.quakeShockAge;
        if (t > QUAKE_TICKS) {
            this.quakeShockAge = -1;
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }

        double radius = 1.2D + (QUAKE_RADIUS - 1.2D) * ((double) t / QUAKE_TICKS);
        int points = 10 + t * 2;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            double px = this.getX() + Math.cos(angle) * radius;
            double pz = this.getZ() + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.CLOUD,
                    px, this.getY() + 0.15D, pz, 1, 0.12D, 0.04D, 0.12D, 0.01D);
            if (i % 3 == 0) {
                world.spawnParticles(ParticleTypes.LAVA,
                        px, this.getY() + 0.25D, pz, 1, 0.1D, 0.15D, 0.1D, 0.0D);
            }
            if (i % 5 == 0) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        px, this.getY() + 0.25D, pz, 1, 0.1D, 0.1D, 0.1D, 0.01D);
            }
        }
    }

    // ------------------------------------------------------------------
    // 位移与破方块
    // ------------------------------------------------------------------

    /** 水平朝向（已归一化） */
    private Vec3d horizontalFacing() {
        Vec3d look = this.getRotationVec(1.0F);
        Vec3d flat = new Vec3d(look.x, 0.0D, look.z);
        return flat.lengthSquared() < 1.0E-6D ? new Vec3d(0.0D, 0.0D, 1.0D) : flat.normalize();
    }

    /** 水平面内旋转一个向量：火球扇形展开用 */
    private static Vec3d rotateY(Vec3d v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3d(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    /**
     * 往指定方向推。
     *
     * <p>不能只靠 {@code setVelocity}：{@code LivingEntity.travel} 每 tick 都会用
     * {@code updateVelocity(移动速度, movementInput)} 把水平速度整个覆盖掉。
     * 冲刺期间 navigation 是停的、movementInput 恒为 0，所以设上去的速度
     * 一帧都没被用于移动，表现就是原地播动画、脚下冒烟，人却没往前挪。
     *
     * <p>这里直接调 {@code move} 走这一步（自带碰撞检测），每 tick 实打实
     * 前进 speed 格。
     */
    private void pushForward(Vec3d dir, double speed) {
        this.move(MovementType.SELF, new Vec3d(dir.x * speed, 0.0D, dir.z * speed));
        this.fallDistance = 0.0F;
    }

    /**
     * 冲刺的命中探测：自己的碰撞箱再往外放 padding 格，和目标的包围盒相交才算撞上。
     *
     * <p>以前用的是「中心距 &lt; 5.4」：4.4 格宽的块头，等于隔着两格半就算撞到，
     * 冲锋刚起步就判定命中，直接收招，看起来就像只挪了半步。
     * 现在按包围盒接触算，必须真贴上去才停。
     */
    private boolean impactTarget(double padding) {
        Vec3d facing = this.chargeStartAge >= 0 && this.chargeDir.lengthSquared() > 0.0D
                ? this.chargeDir : this.horizontalFacing();
        Box reach = this.getBoundingBox().expand(padding);
        for (Entity entity : this.getWorld().getOtherEntities(this, reach)) {
            if (!(entity instanceof LivingEntity target) || target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && player.isSpectator()) {
                continue;
            }
            if (!reach.intersects(target.getBoundingBox())) {
                continue;
            }
            // 只算正前方半球，免得冲刺时被侧后方的人蹭停
            Vec3d to = new Vec3d(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
            if (to.lengthSquared() > 1.0E-4D && facing.dotProduct(to.normalize()) < 0.0D) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 冲锋沿途拆方块。
     *
     * <p>只碰脚部往上 1~3 层、正前方 2 格内的方块：脚底那层拆了会自己掉进坑里；
     * 方块实体（祭坛、战利品箱、基座）一律不碰；硬度上限 12，黑曜石、远古残骸、
     * 基岩这类拆不动，撞上去就地停下。
     *
     * @return true = 正前方被拆不动的东西挡住了
     */
    private boolean crushAhead() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        Vec3d dir = this.chargeDir;
        if (dir.lengthSquared() < 1.0E-6D) {
            return false;
        }

        int footY = MathHelper.floor(this.getY());
        int broken = 0;
        boolean blocked = false;

        // 正前方 2 格、横向 ±2 格、脚上 1~3 层。
        // 脚底那一层（dy = 0）故意不拆：拆了会把自己脚下的地板掏空，
        // 冲锋一变自由落体就再也撞不到人了
        Vec3d side = new Vec3d(-dir.z, 0.0D, dir.x);
        for (int fw = 1; fw <= 2 && !blocked; fw++) {
            for (int lateral = -2; lateral <= 2; lateral++) {
                for (int dy = 1; dy <= 3; dy++) {
                    double px = this.getX() + dir.x * fw + side.x * lateral;
                    double pz = this.getZ() + dir.z * fw + side.z * lateral;
                    BlockPos pos = BlockPos.ofFloored(px, footY + dy, pz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (!canCrush(world, pos, state)) {
                        // 紧贴身前的那一层拆不动 = 撞墙
                        if (fw == 1 && lateral == 0) {
                            blocked = true;
                        }
                        continue;
                    }
                    if (broken >= CRUSH_LIMIT) {
                        return false;
                    }
                    world.breakBlock(pos, false, this);
                    world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
                    broken++;
                }
            }
        }
        return blocked;
    }

    private boolean canCrush(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        // 自己家的东西不许拆：祭坛、召唤石、剑座
        if (state.isOf(BlocksRegistry.MOLTEN_CORE_SUMMONER)
                || state.isOf(BlocksRegistry.SWORD_TOMB_ALTAR)
                || state.isOf(BlocksRegistry.STONE_SWORD_PEDESTAL)) {
            return false;
        }
        // 1.20.1 的爆炸抗性没有公开 getter，改用硬度卡：
        // 黑曜石 50、远古残骸 30 都会被下面这行挡住，基岩是 -1
        float hardness = state.getHardness(world, pos);
        return hardness >= 0.0F && hardness <= CRUSH_MAX_HARDNESS;
    }

    // ------------------------------------------------------------------
    // 伤害结算
    // ------------------------------------------------------------------

    /**
     * 前方扇形结算。
     *
     * @param range       中心距上限（格）
     * @param ratio       攻击力倍率
     * @param knockback   水平击退强度，0 = 不击退
     * @param lift        垂直挑飞速度
     * @param threshold   朝向点积阈值，-1 = 全向
     * @param fireSeconds 命中后点燃多少秒，0 = 不点。它全身是熔渣，所有招式都带火
     */
    private void areaAttack(double range, float ratio, double knockback, double lift,
                            double threshold, int fireSeconds) {
        if (this.getWorld().isClient()) {
            return;
        }
        Vec3d facing = this.horizontalFacing();
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * ratio;

        for (Entity entity : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(range))) {
            if (!(entity instanceof LivingEntity target) || target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && player.isSpectator()) {
                continue;
            }
            Vec3d to = new Vec3d(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
            double dist = to.length();
            if (dist > range) {
                continue;
            }
            if (dist > 1.0E-4D && facing.dotProduct(to.normalize()) < threshold) {
                continue;
            }
            // 慢刀只有一下，不能被前一刀残留的受击无敌帧吞掉
            target.timeUntilRegen = 0;
            if (!target.damage(this.getDamageSources().mobAttack(this), damage)) {
                continue;
            }
            if (knockback > 0.0D) {
                target.takeKnockback(knockback,
                        this.getX() - target.getX(), this.getZ() - target.getZ());
            }
            if (lift > 0.0D) {
                Vec3d v = target.getVelocity();
                target.setVelocity(v.x, v.y + lift, v.z);
                target.velocityModified = true;
            }
            if (fireSeconds > 0) {
                target.setOnFireFor(fireSeconds);
            }
        }
    }

    /** 命中处的一圈火花与冲击尘 */
    private void impactEffects(double forward, int count) {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d facing = this.horizontalFacing();
        double px = this.getX() + facing.x * forward;
        double pz = this.getZ() + facing.z * forward;
        world.spawnParticles(ParticleTypes.CRIT, px, this.getY() + 1.6D, pz,
                count, 1.2D, 1.2D, 1.2D, 0.25D);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, px, this.getY() + 1.4D, pz,
                3, 1.2D, 0.4D, 1.2D, 0.0D);
    }

    /** 重砸落地：一圈尘土 + 熔岩星点 */
    private void quakeEffects() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d facing = this.horizontalFacing();
        double px = this.getX() + facing.x * 2.5D;
        double pz = this.getZ() + facing.z * 2.5D;
        world.spawnParticles(ParticleTypes.CLOUD, px, this.getY() + 0.2D, pz,
                22, 2.4D, 0.2D, 2.4D, 0.08D);
        world.spawnParticles(ParticleTypes.LAVA, px, this.getY() + 0.3D, pz,
                10, 1.6D, 0.1D, 1.6D, 0.0D);
        world.spawnParticles(ParticleTypes.EXPLOSION, px, this.getY() + 0.4D, pz,
                2, 1.4D, 0.1D, 1.4D, 0.0D);
        this.playSound(SoundEvents.BLOCK_ANVIL_LAND, 1.6F, 0.5F);
        this.playSound(SoundRegistry.SLASH_IMPACT, 1.7F, 0.5F);
    }

    /** 践踏落地：正下方炸开一圈尘浪 + 熔岩点，不往身前偏 */
    private void stompEffects() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        double px = this.getX();
        double pz = this.getZ();
        world.spawnParticles(ParticleTypes.CLOUD, px, this.getY() + 0.15D, pz,
                28, 2.6D, 0.15D, 2.6D, 0.09D);
        world.spawnParticles(ParticleTypes.FLAME, px, this.getY() + 0.55D, pz,
                18, 2.0D, 0.4D, 2.0D, 0.05D);
        world.spawnParticles(ParticleTypes.LAVA, px, this.getY() + 0.25D, pz,
                14, 2.2D, 0.1D, 2.2D, 0.0D);
        world.spawnParticles(ParticleTypes.EXPLOSION, px, this.getY() + 0.35D, pz,
                3, 1.8D, 0.1D, 1.8D, 0.0D);
        this.playSound(SoundEvents.BLOCK_ANVIL_LAND, 1.7F, 0.45F);
        this.playSound(SoundRegistry.SLASH_IMPACT, 1.7F, 0.5F);
    }

    /** 冲锋蓄势：身上的熔渣开始发亮、火星往上窜 */
    private void chargeWindupEffects() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        world.spawnParticles(ParticleTypes.FLAME,
                this.getX(), this.getY() + 1.6D, this.getZ(), 6, 1.8D, 1.6D, 1.8D, 0.02D);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                this.getX(), this.getY() + 0.6D, this.getZ(), 3, 1.6D, 0.4D, 1.6D, 0.01D);
    }

    /** 冲锋途中：拖一条火与烟 */
    private void chargeEffects() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                this.getX(), this.getY() + 0.4D, this.getZ(), 5, 1.8D, 0.3D, 1.8D, 0.03D);
        world.spawnParticles(ParticleTypes.FLAME,
                this.getX(), this.getY() + 1.2D, this.getZ(), 8, 1.6D, 1.0D, 1.6D, 0.04D);
    }

    /** 转身面向目标（只转身体，不寻路）。限速转向，避免瞬间甩头 */
    private void faceTowards(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float wanted = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float delta = MathHelper.wrapDegrees(wanted - this.getYaw());
        // 一格 18°，冲锋的 10 tick 蓄势足够把背对的目标转过来
        float yaw = this.getYaw() + MathHelper.clamp(delta, -18.0F, 18.0F);
        this.setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
    }

    // ------------------------------------------------------------------
    // 音效
    // ------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WITHER_SKELETON_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.4F;
    }

    /** 压低音调，听起来更沉、更像一坨烧红的石头 */
    @Override
    public float getSoundPitch() {
        return 0.55F;
    }

    // ------------------------------------------------------------------
    // Boss 血条
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        this.tickCombat();
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            this.tickBossBar(serverWorld);
            this.pickFallbackTarget(serverWorld);
        }
    }

    /**
     * 兜底锁定：没有有效目标时，每 10 tick 自己扫一次附近玩家。
     *
     * <p>不能只靠 {@code ActiveTargetGoal}。它是原版的追踪目标，走
     * {@code TargetPredicate.createAttackable()}，路径上任何一层谓词把玩家滤掉，
     * 结果都是 BOSS 站着不动、怎么打都不还手。这里只排除旁观者，
     * 也不再受 {@code getFollowRange} 以外的条件影响。
     */
    private void pickFallbackTarget(ServerWorld world) {
        if (this.age % 10 != 0) {
            return;
        }
        LivingEntity current = this.getTarget();
        if (current != null && current.isAlive()) {
            return;
        }
        // 手写遍历，不用 getClosestPlayer：那条路默认谓词也会滤人，
        // 正是这里要绕开的东西
        ServerPlayerEntity best = null;
        double bestSq = TARGET_RANGE * TARGET_RANGE;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            double distSq = player.squaredDistanceTo(this);
            if (distSq <= bestSq) {
                bestSq = distSq;
                best = player;
            }
        }
        if (best != null) {
            this.setTarget(best);
        }
    }

    private ServerBossBar bossBar() {
        if (this.bossBar == null) {
            this.bossBar = new ServerBossBar(
                    Text.translatable("boss.wangsword.cinder_colossus"),
                    BossBar.Color.RED,
                    BossBar.Style.NOTCHED_10);
        }
        return this.bossBar;
    }

    private void tickBossBar(ServerWorld world) {
        ServerBossBar bar = this.bossBar();
        if (!this.isAlive()) {
            bar.clearPlayers();
            return;
        }
        bar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        double rangeSq = BOSS_BAR_RANGE * BOSS_BAR_RANGE;
        for (ServerPlayerEntity player : bar.getPlayers().toArray(new ServerPlayerEntity[0])) {
            if (player.isRemoved() || player.getWorld() != world
                    || player.squaredDistanceTo(this) > rangeSq) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!bar.getPlayers().contains(player)
                    && !player.isSpectator()
                    && player.squaredDistanceTo(this) <= rangeSq) {
                bar.addPlayer(player);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.bossBar != null) {
            this.bossBar.clearPlayers();
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------
    // 动画
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 腿部先注册，body 后注册 —— 出招时 melee 动画自带的腿部关键帧要能盖住 walk
        controllers.add(new AnimationController<>(this, "legs", 3, this::legController));
        controllers.add(new AnimationController<>(this, "body", 4, this::bodyController)
                .triggerableAnim("melee", SLAM_ANIM)
                .triggerableAnim("melee2", SWEEP_ANIM)
                .triggerableAnim("melee3", STOMP_ANIM)
                .triggerableAnim("melee4", DOUBLE_ANIM)
                .triggerableAnim("melee5", QUAKE_ANIM)
                .triggerableAnim("melee6", CHARGE_ANIM)
                .triggerableAnim("melee7", EXECUTION_ANIM)
                .triggerableAnim("attack1", BEAM1_ANIM)
                .triggerableAnim("attack2", BEAM2_ANIM)
                .triggerableAnim("attack3", FIREBALL_ANIM));
    }

    private PlayState legController(AnimationState<CinderColossusEntity> state) {
        // 出招期间腿交给 melee 动画自己摆（每套动作都带腿部关键帧），
        // 不掐掉的话会出现"上半身在劈砍、下半身还在走路"
        if (this.isPerformingAttack()) {
            return PlayState.STOP;
        }
        if (state.isMoving() || state.getLimbSwingAmount() > 0.05F) {
            return state.setAndContinue(WALK);
        }
        return PlayState.STOP;
    }

    /**
     * 身体层常驻 idle，一次性招式由 triggerableAnim 抢占。
     *
     * <p>不能像守卫者那样直接返回 STOP：那只适合有石化解的雕像，
     * 这里战斗结束要能自动滑回 idle，否则最后一下收招会僵在半空。
     */
    private PlayState bodyController(AnimationState<CinderColossusEntity> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------------
    // AI
    // ------------------------------------------------------------------

    /**
     * 远程：站定放激光。
     *
     * <p>只在 8~30 格之间起手：贴脸时挥刀更划算，太远射线也够不着
     * （单束最多 32 格，留一点余量）。前摇期间继续转向目标，开火之后
     * 就不再转——方向已经锁进光束实体了，再转头只会让光源跟着乱晃。
     */
    private static class ColossusBeamGoal extends Goal {
        private final CinderColossusEntity colossus;

        ColossusBeamGoal(CinderColossusEntity colossus) {
            this.colossus = colossus;
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.colossus.getWorld().isClient() || this.colossus.isPerformingAttack()) {
                return false;
            }
            // 残血大招优先，别让激光把起手帧抢走
            if (this.colossus.isExecutionReady()) {
                return false;
            }
            if (this.colossus.beam1Cooldown > 0 && this.colossus.beam2Cooldown > 0) {
                return false;
            }
            LivingEntity target = this.colossus.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distSq = this.colossus.squaredDistanceTo(target);
            return distSq >= BEAM_MIN_RANGE * BEAM_MIN_RANGE
                    && distSq <= BEAM_MAX_RANGE * BEAM_MAX_RANGE;
        }

        @Override
        public void start() {
            LivingEntity target = this.colossus.getTarget();
            if (target != null) {
                this.colossus.faceTowards(target);
            }
            this.colossus.startAttack(this.colossus.chooseBeam());
        }

        @Override
        public void tick() {
            if (this.colossus.meleeHitDone) {
                return;
            }
            LivingEntity target = this.colossus.getTarget();
            if (target != null) {
                this.colossus.faceTowards(target);
            }
        }

        @Override
        public boolean shouldContinue() {
            if (!this.colossus.isPerformingAttack()) {
                return false;
            }
            LivingEntity target = this.colossus.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            if (this.colossus.isPerformingAttack()) {
                this.colossus.cancelAttack();
            }
        }
    }

    /** 出招：进到触发距离、且没在出招时就挑一招打出去 */
    private static class ColossusMeleeGoal extends Goal {
        private final CinderColossusEntity colossus;

        ColossusMeleeGoal(CinderColossusEntity colossus) {
            this.colossus = colossus;
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.colossus.getWorld().isClient() || this.colossus.isPerformingAttack()) {
                return false;
            }
            if (this.colossus.attackCooldown > 0) {
                return false;
            }
            LivingEntity target = this.colossus.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distSq = this.colossus.squaredDistanceTo(target);
            if (distSq <= NEAR_RANGE * NEAR_RANGE) {
                return true;
            }
            // 残血大招不看冷却之外的任何条件，30 格内都能起手
            if (this.colossus.isExecutionReady()
                    && distSq <= EXECUTION_RANGE * EXECUTION_RANGE) {
                return true;
            }
            // 火球覆盖中远距离，不然 6~16 格之间没招可用
            if (this.colossus.fireballCooldown <= 0
                    && distSq >= FIREBALL_MIN_RANGE * FIREBALL_MIN_RANGE
                    && distSq <= FIREBALL_MAX_RANGE * FIREBALL_MAX_RANGE) {
                return true;
            }
            // 远了只有冲锋能接手
            return this.colossus.chargeCooldown <= 0
                    && distSq <= CHARGE_PICK_RANGE * CHARGE_PICK_RANGE;
        }

        @Override
        public void start() {
            LivingEntity target = this.colossus.getTarget();
            if (target != null) {
                this.colossus.faceTowards(target);
            }
            double distance = target == null ? 0.0D : Math.sqrt(this.colossus.squaredDistanceTo(target));
            this.colossus.startAttack(this.colossus.chooseStep(distance));
        }

        @Override
        public void tick() {
            LivingEntity target = this.colossus.getTarget();
            // 冲锋一旦起步就不再转头：方向在 tickCharge 里锁死
            if (target != null && this.colossus.chargeStartAge < 0) {
                this.colossus.faceTowards(target);
            }
        }

        @Override
        public boolean shouldContinue() {
            if (!this.colossus.isPerformingAttack()) {
                return false;
            }
            LivingEntity target = this.colossus.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            // 动作播完（或被打断）后收尾，别把挥砍状态带进下一个 Goal
            if (this.colossus.isPerformingAttack()) {
                this.colossus.cancelAttack();
            }
        }
    }

    /** 走位：目标还远就靠过去，进到 KEEP_DISTANCE 就交给出招的 Goal */
    private static class ColossusApproachGoal extends Goal {
        private final CinderColossusEntity colossus;
        private int repathTimer;

        ColossusApproachGoal(CinderColossusEntity colossus) {
            this.colossus = colossus;
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.colossus.getWorld().isClient() || this.colossus.isPerformingAttack()) {
                return false;
            }
            LivingEntity target = this.colossus.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distSq = this.colossus.squaredDistanceTo(target);
            return distSq > KEEP_DISTANCE * KEEP_DISTANCE
                    && distSq <= CHASE_RANGE * CHASE_RANGE;
        }

        @Override
        public void start() {
            this.repathTimer = 0;
            this.moveToTarget();
        }

        @Override
        public void tick() {
            this.moveToTarget();
        }

        @Override
        public boolean shouldContinue() {
            if (this.colossus.isPerformingAttack()) {
                return false;
            }
            LivingEntity target = this.colossus.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            return this.colossus.squaredDistanceTo(target) > KEEP_DISTANCE * KEEP_DISTANCE * 0.8D;
        }

        @Override
        public void stop() {
            this.colossus.getNavigation().stop();
        }

        /** 路径走完或被挡了都会让 navigation 空闲，隔几 tick 重新起步，别每 tick 都算路径 */
        private void moveToTarget() {
            LivingEntity target = this.colossus.getTarget();
            if (target == null) {
                return;
            }
            this.colossus.getLookControl().lookAt(target, 30.0F, 30.0F);
            if (this.repathTimer > 0) {
                this.repathTimer--;
                return;
            }
            if (this.colossus.getNavigation().isIdle()) {
                this.colossus.getNavigation().startMovingTo(target, 1.0D);
                this.repathTimer = 10;
            }
        }
    }
}
