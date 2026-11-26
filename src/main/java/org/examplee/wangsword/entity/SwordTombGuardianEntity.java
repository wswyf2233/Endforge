package org.examplee.wangsword.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.examplee.wangsword.block.StoneSwordPedestalBlock;
import org.examplee.wangsword.registry.BlocksRegistry;
import org.examplee.wangsword.registry.EntityRegistry;
import org.examplee.wangsword.registry.ParticleRegistry;
import org.examplee.wangsword.registry.SoundRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 剑冢守卫：一尊会苏醒的石像，站桩不动，靠身边浮着的六把飞剑轮流射人。
 *
 * <p>模型 / 动画 / 贴图：
 * <ul>
 *   <li>{@code assets/wangsword/geo/sword_tomb_guardian.geo.json}</li>
 *   <li>{@code assets/wangsword/animations/sword_tomb_guardian.animation.json}</li>
 *   <li>{@code assets/wangsword/textures/entity/sword_tomb_guardian.png}</li>
 * </ul>
 *
 * <p>动画与代码的对应：
 * <ul>
 *   <li>{@code idle}（2.0s，循环）—— 常驻底，六把剑缓慢自转</li>
 *   <li>{@code awake}（1.5s，一次性）—— 玩家靠近 10 格或挨打时触发，播完自动回 idle</li>
 *   <li>{@code attack}（1.0s，一次性）—— 每 3 秒一次，第 7 tick（0.35s）放飞剑</li>
 *   <li>{@code attack2}（1.0s，一次性）—— 紧接着 attack：头部原地转三圈，
 *       每 2 tick 沿"头当前朝向"甩出一把光刃，1 秒里把一整圈扫满</li>
 *   <li>{@code melee}（0.75s，一次性）—— 近身竖劈，第 11 tick（0.55s）结算</li>
 *   <li>{@code melee2}（0.75s，一次性）—— 转腰横扫，第 12 tick（0.6s）结算，击退更远</li>
 *   <li>{@code melee4}（1.0s，一次性）—— 旋风连斩：原地转两圈，全程每 3 tick 结算一跳，
 *       整圈判定不看朝向，最后一跳把人甩出去；固定接在 melee2 后面</li>
 *   <li>{@code melee3}（1.0s，一次性）—— 大招：举剑蓄力后半秒砸地，第 17 tick（0.85s）结算，
 *       地裂冲击波半径 6.5 格，伤害/击退随距离衰减；残血（≤50%）才接在 melee4 后面</li>
 *   <li>{@code melee5}（1.5s，一次性）—— 抬剑踏步重砸，第 22 tick（1.1s）结算，
 *       伤害 ×1.6、击退 1.4 并把人挑起；固定在 melee2 与 melee4 之间</li>
 *   <li>{@code mele6}（1.0s，一次性，导出名少一个 e）—— 终极大招：濒死时锁血 1 点、
 *       进入 2 秒无敌并把巨剑甩出去，第 15 tick（0.75s）结算，正前方 10 格扇面 60 伤害</li>
 *   <li>{@code walk}（0.75s，循环）—— 走动时由独立的 legs 控制器播放</li>
 * </ul>
 * 一次性动作一律走 {@code triggerableAnim}，不能再塞进 {@code setAndContinue}
 * 的谓词里——那里每帧都会重新求值，会把触发动画顶掉。
 */
public class SwordTombGuardianEntity extends HostileEntity implements GeoEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger("wangsword/sword_tomb_guardian");

    /** 常驻底动画 */
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    /** 走动：只有在 WALK_ANIM_READY 为 true 时才会用到 */
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    /** 一次性过渡：苏醒，播完由控制器回落 IDLE */
    private static final RawAnimation AWAKE_ANIM = RawAnimation.begin().thenPlay("awake");
    /**
     * 石化姿态 = awake 动画的第 0 帧（双臂收拢抱剑、六把剑竖直收在身周）。
     *
     * <p>不另画一套"石化动画"，而是把 awake 定格在 0.0 秒当雕像用：
     * 控制器速度锁 0（见 {@code registerControllers} 里的 speed handler），
     * 于是永远停在第一帧；苏醒时 awake 又正好从这一帧开始播，**一帧都不会跳**。
     *
     * <p>改石化形象只要改 awake 的第 0 帧，动画本身不用动。
     */
    private static final RawAnimation PETRIFIED = RawAnimation.begin().thenPlay("awake");
    /** 一次性攻击：0.35s 处是挥剑动作点 */
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("attack");
    /** 攻击第二段：转头旋发一圈光刃 */
    private static final RawAnimation ATTACK2_ANIM = RawAnimation.begin().thenPlay("attack2");
    /** 一次性近战挥砍（动画还没导出前触发是空操作） */
    private static final RawAnimation MELEE_ANIM = RawAnimation.begin().thenPlay("melee");
    /** 近战第二段：转腰横扫，带更强击退 */
    private static final RawAnimation MELEE2_ANIM = RawAnimation.begin().thenPlay("melee2");
    /** 近战第三段：抬剑踏步重砸，1.5 秒的慢刀 */
    private static final RawAnimation MELEE5_ANIM = RawAnimation.begin().thenPlay("melee5");
    /** 近战第四段：旋风连斩，1 秒转两圈，全程持续结算 */
    private static final RawAnimation MELEE4_ANIM = RawAnimation.begin().thenPlay("melee4");
    /** 近战收尾（大招）：举剑蓄力后砸地，残血才会接在 melee4 后面 */
    private static final RawAnimation MELEE3_ANIM = RawAnimation.begin().thenPlay("melee3");
    /**
     * 终极大招：巨剑横扫（濒死锁血时才会放）。
     *
     * <p>注意导出名是 <b>mele6</b>（少一个 e），不是 melee6。GeckoLib 找不到同名动画
     * 时是静默空放，所以这里单独提成常量——哪天在 Blockbench 里把名字改对，
     * 只改这一个字符串。
     */
    private static final String MELEE6_NAME = "mele6";
    private static final RawAnimation MELEE6_ANIM = RawAnimation.begin().thenPlay(MELEE6_NAME);
    /**
     * 死亡：卸剑崩解。
     *
     * <p>必须用 {@code thenPlayAndHold} 而不是 {@code thenPlay}：一次性动画播完会回落到
     * body 控制器的默认状态（STOP = 站立姿态），尸体在"播完"到"被移除"之间的几 tick
     * 会突然站起来。保持末帧就不会。
     */
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("death");

    /**
     * 是否已经有 death 动画。动画导出并放进 animation.json 之后改成 true。
     *
     * <p>注意时限：原版 {@code deathTime} 走到 20 tick 就把实体移除，
     * 所以死亡动画**必须 1 秒内播完**，否则最后一段永远看不到。
     */
    public static final boolean DEATH_ANIM_ENABLED = true;

    /**
     * 崩解那一 tick（从死亡开始数，20 tick/秒）：10 = 0.50 秒。
     *
     * <p>对应 death 动画里"双腿已经沉到底（0.375s）、身体塌到一半"的那一帧，
     * 碎石在这时炸开最像结构断裂。动画全长 0.875 秒，塌平在 0.75 秒。
     */
    private static final int DEATH_CRUMBLE_TICK = 10;

    /**
     * 剑冢基座出现的 tick（从死亡开始数）。
     *
     * <p>取值晚于 {@link #DEATH_CRUMBLE_TICK}：先让身体塌下去，再把剑插上来。
     * 尸体会在 20 tick 时被移除，所以必须在 20 之前放完。
     */
    private static final int DEATH_PEDESTAL_TICK = 16;

    /** 召唤它的祭坛位置。只有祭坛召唤出来的守卫才有，死亡时在这一格上方长出石中剑基座 */
    private BlockPos altarPos;

    /** 基座是否已经放出来，防止 tick 和 remove 各放一次 */
    private boolean pedestalPlaced;

    /**
     * 是否已经有 walk 动画。导出 walk 之后把这里改成 true，
     * 走动时才播 walk、停下回到 idle；否则一直用 idle，不会报错。
     */
    private static final boolean WALK_ANIM_READY = true;

    /**
     * walk 的播放速度倍率。1.0 = 按动画原本的 0.75 秒一个周期。
     * 腿摆得比实际位移快（脚底打滑）就调小，比如 0.6；
     * 反过来像在冰上滑行就调大。
     */
    private static final double WALK_ANIM_SPEED = 0.6D;

    /**
     * 玩家进入多少格唤醒石像（必须大于 STOP_DISTANCE，否则它醒了也不走）。
     *
     * <p>这个值不能大：之前是 16，玩家还在剑冢外面（甚至绕到背面）就已经把它弄醒了，
     * 1.5 秒的苏醒动画在你眼皮外播完，等你走上台阶只剩一尊"本来就在动"的雕像，
     * 看起来就是"走近没触发"。10 差不多是站到台阶边上的距离。
     */
    private static final double WAKE_RANGE = 10.0D;
    /** 苏醒动画原速 1.5 秒 = 30 tick */
    private static final int AWAKE_ANIM_TICKS = 30;
    /**
     * 苏醒动画的播放倍率：0.5 = 慢放一倍，1.5 秒的起身过程拉成 3 秒，
     * 石像裂开、抬头的动作看得清楚，也更容易被打断前的压迫感拉满。
     */
    private static final double AWAKE_SPEED = 0.5D;
    /** 苏醒动画实际占用多少 tick：慢放后要同比拉长，否则挥刀会顶掉没播完的动画 */
    private static final int WAKE_LOCK_TICKS = (int) Math.round(AWAKE_ANIM_TICKS / AWAKE_SPEED);
    /**
     * 石化状态下单次最多吃多少伤害。
     *
     * <p>石像是"未启动"状态，没有防御可言，如果照常结算，玩家拿一把高伤武器
     * 在门外一剑就能把还没醒的雕像打没。所以未苏醒时把每次伤害压到 1 点：
     * 无论外部给多少，都只能削掉 1 格血，同时这一下会把它打醒，之后恢复正常结算。
     */
    private static final float PETRIFIED_MAX_DAMAGE = 1.0F;
    /** 超过这个距离就走过去（远程射程） */
    private static final double VOLLEY_RANGE = 12.0D;
    /** 走到这么近就停止靠近，进近战圈 */
    private static final double STOP_DISTANCE = 4.0D;

    /** 近战挥砍的判定半径 */
    private static final double MELEE_RANGE = 5.0D;
    /** melee 动画总长 0.75 秒 = 15 tick */
    private static final int MELEE_CAST_TICKS = 15;
    /**
     * melee（竖劈）的播放速度倍率。
     *
     * <p>不改动画、不重画：GeckoLib 的控制器速度乘上去，0.75 秒的动作就压到
     * 0.5 秒播完，动作幅度完全不变。**提速后下面两个按 1 倍速写的 tick 必须一起缩**，
     * 否则会出现"刀已经劈完了才掉血"——所以命中帧和收尾时间都走 {@link #speedTick}。
     */
    private static final double MELEE_SPEED = 1.5D;
    /**
     * 挥砍结算帧（从动作开始算第几 tick 掉血）。
     *
     * <p>动画里肩/臂真正挥到底是在 0.5417 秒（第 11 tick），但如果按落到底才结算，
     * 玩家看到刀已经砍过身体了才掉血，手感是"晚半拍"。这里提前到第 8 tick（0.40 秒），
     * 刀劈到一半多就结算，砍中反馈和视觉对得上。
     */
    private static final int MELEE_HIT_TICK = 11;
    /** melee2 的命中帧：0.6 秒 = 第 12 tick */
    private static final int MELEE2_HIT_TICK = 12;
    /** melee5 的命中帧：1.1 秒 = 第 22 tick */
    private static final int MELEE5_HIT_TICK = 22;
    /** melee 的击退强度 */
    private static final double MELEE_KNOCKBACK = 0.6D;
    /** melee2 是横扫，推得更远 */
    private static final double MELEE2_KNOCKBACK = 1.15D;
    /**
     * 一整轮连招打完之后才会用到的冷却（tick）。
     *
     * <p>段与段之间现在是 0 冷却：上一段的收招帧播完，下一段立刻起手，
     * 中间不再有站着喘气的空档。这个常量只作用在"这一轮打完了"之后
     * （见 {@code MeleeGoal.stop()}），给玩家留一点反手的窗口。
     */
    private static final int MELEE_COOLDOWN = 7;

    // ---- 连招的段号（playMelee 的入参） ----
    /** 竖劈 */
    private static final int STEP_MELEE = 0;
    /** 横扫 */
    private static final int STEP_MELEE2 = 1;
    /** 抬剑踏步重砸 */
    private static final int STEP_MELEE5 = 2;
    /** 旋风连斩 */
    private static final int STEP_MELEE4 = 3;
    /** 大招（地裂冲击波） */
    private static final int STEP_ULT = 4;

    // ---- 第三段：melee5 抬剑踏步重砸 ----
    /** melee5 动画总长 1.5 秒 = 30 tick */
    private static final int MELEE5_CAST_TICKS = 30;
    /** 重砸伤害 = 攻击力(15) × 1.6 = 24 */
    private static final float MELEE5_DAMAGE_RATIO = 1.6F;
    /** 重砸击退：比横扫推得更远 */
    private static final double MELEE5_KNOCKBACK = 1.4D;
    /** 砸地会把人稍微挑起，配合击退看起来是被掀飞 */
    private static final double MELEE5_LIFT = 0.28D;

    // ---- 第四段：melee4 旋风连斩 ----
    /** melee4 动画总长 1 秒 = 20 tick */
    private static final int MELEE4_CAST_TICKS = 20;
    /** 第一跳落在第 2 tick（人已经开始转了，别在起手帧就掉血） */
    private static final int MELEE4_FIRST_HIT = 2;
    /** 两跳之间的间隔（tick）：20 tick 内正好 6 跳（2/5/8/11/14/17） */
    private static final int MELEE4_HIT_INTERVAL = 3;
    /** 一共几跳 */
    private static final int MELEE4_HITS = 6;
    /** 单跳伤害 = 攻击力(15) × 该系数 = 5.25，六跳打满 31.5 */
    private static final float MELEE4_HIT_DAMAGE_RATIO = 0.35F;
    /** 判定半径：刀 3.4 格长，转起来是整圈，比普攻的 5 格小一点 */
    private static final double MELEE4_RADIUS = 3.8D;
    /** 最后一跳把人甩出去的力度 */
    private static final double MELEE4_FINAL_KNOCKBACK = 0.8D;

    // ---- 大招（melee3 地裂冲击波） ----
    /** melee3 动画总长 1 秒 = 20 tick */
    private static final int MELEE3_CAST_TICKS = 20;
    /** melee3 的伤害帧：0.85 秒 = 第 17 tick */
    private static final int MELEE3_HIT_TICK = 17;
    /** 冲击波扩散到多大半径（格） */
    private static final double ULT_RADIUS = 6.5D;
    /** 冲击波用多少 tick 从 1 格扩散到 ULT_RADIUS */
    private static final int ULT_SHOCK_TICKS = 12;
    /** 贴脸伤害 / 边缘伤害，中间线性衰减（固定值，不跟攻击力缩放） */
    private static final float ULT_DAMAGE_NEAR = 15.0F;
    private static final float ULT_DAMAGE_FAR = 7.0F;
    /** 贴脸击退强度，随距离减半 */
    private static final double ULT_KNOCKBACK = 1.5D;
    /** 血量掉到这个比例以下，连招收尾才换成大招 */
    private static final float ULT_HEALTH_RATIO = 0.5F;
    /** 大招冷却：200 tick = 10 秒 */
    private static final int ULT_COOLDOWN = 200;

    // ---- 终极大招：melee6 濒死巨剑 ----
    /** melee6 动画总长 1 秒 = 20 tick */
    private static final int MELEE6_CAST_TICKS = 20;
    /** melee6 的伤害帧：0.75 秒 = 第 15 tick */
    private static final int MELEE6_HIT_TICK = 15;
    /**
     * 锁血后的无敌时长（tick）。比动画长 1 秒：
     * 出招的 1 秒 + 收势的 1 秒，避免刚放完就被围殴秒掉，
     * 也让"打完还是 1 血、但还能动"这件事看起来是有意为之。
     */
    private static final int LAST_STAND_TICKS = MELEE6_CAST_TICKS * 2;
    /** 巨剑横扫的射程：剑被甩出去 4 格，判定给到 10 格 */
    private static final double MELEE6_RANGE = 10.0D;
    /** 终极大招伤害：固定 30，不走攻击力缩放（等于 3 刀普攻），一辈子只放一次 */
    private static final float MELEE6_DAMAGE = 30.0F;
    /** 击退：这是全模组最重的一击，直接把人送出去 */
    private static final double MELEE6_KNOCKBACK = 2.4D;
    /** 再往上挑一点，配合击退看起来是整个人被掀飞 */
    private static final double MELEE6_LIFT = 0.45D;

    // ---- 二阶段：狂暴 ----
    /**
     * 生命降到这个比例时进入狂暴。一辈子只进一次：
     * 它不是"血低了变强"，而是一次不可逆的转阶段，撑过去就回到普通的濒死大招。
     */
    private static final float ENRAGE_HEALTH_RATIO = 0.5F;
    /** 狂暴后攻击力加算：10 → 16，普攻与所有技能都吃这个值 */
    private static final double ENRAGE_ATTACK_BONUS = 6.0D;
    /** 狂暴后移速加算：0.22 → 0.28，想拉开距离换血的会被它追上 */
    private static final double ENRAGE_SPEED_BONUS = 0.06D;
    /** 狂暴后一轮连招之间的停手时间：7 → 4 tick，出手密度接近翻倍 */
    private static final int ENRAGED_MELEE_COOLDOWN = 4;
    /** 狂暴后剑阵的间隔：15 → 9 tick */
    private static final int ENRAGED_SWORD_AURA_INTERVAL = 9;

    /** 属性修饰符 id 必须固定，否则每次重进存档都会叠一层 */
    private static final UUID ENRAGE_ATTACK_ID = UUID.fromString("ae696fef-e6ff-41cc-9205-05e5053db0f6");
    private static final String ENRAGE_ATTACK_NAME = "ws_guardian_enrage_attack";
    private static final UUID ENRAGE_SPEED_ID = UUID.fromString("05b49016-c9fa-4904-9c60-020ba9b7d52c");
    private static final String ENRAGE_SPEED_NAME = "ws_guardian_enrage_speed";

    /** 飞剑数量，对应模型里的 sword1~sword6 */
    private static final int SWORD_SLOTS = 6;
    /** 放剑冷切：20 tick 动画 + 45 tick 冷却 = 3.25 秒一轮 */
    private static final int ATTACK_COOLDOWN = 45;
    /** 挥手出剑的那一帧，从动作开始算第 7 tick */
    private static final int RELEASE_TICK = 7;
    /** attack 动画总长 1 秒 = 20 tick */
    private static final int CAST_TICKS = 20;

    // ---- 攻击第二段：attack2 转头旋发 ----
    /** attack2 动画总长 1 秒 = 20 tick */
    private static final int ATTACK2_CAST_TICKS = 20;
    /**
     * 动画里 head 骨骼在 1 秒内转了 3 整圈（0 → -1080°），
     * 折合每 tick 54°。模型的负向旋转对应世界 yaw 的正向（见 SwordTombGuardianModel
     * 的 HEAD_YAW_SIGN），所以这里直接按 +54°/tick 往外扫。
     * 动画改了转速，只改这一个数字。
     */
    private static final double SPIN_DEG_PER_TICK = 54.0D;
    /** 第一把剑落在第 4 tick（等头先转起来，别在起手帧就射） */
    private static final int ATTACK2_FIRST_SHOT = 4;
    /** 每 2 tick 一把：4,6,8…18，共 8 把，正好扫满一圈多一点 */
    private static final int ATTACK2_SHOT_INTERVAL = 2;
    private static final int ATTACK2_SHOTS = 8;
    /** 光刃伤害 = 攻击力(15) × 0.5 = 7.5，比瞄准单发的 11.25 轻一点，靠数量取胜 */
    private static final float ATTACK2_DAMAGE_RATIO = 0.5F;
    /** 出剑高度：胸口（3.6 格）。从头顶 5.2 平射会全部从玩家头顶掠过 */
    private static final double ATTACK2_MUZZLE_Y = 3.6D;
    /**
     * 俯仰：正数是向下。守卫六格高，剑口本来就比玩家高，
     * 所以整圈统一往下压 16°，再上下摆 10°，近处才打得到站着的人。
     */
    private static final double ATTACK2_PITCH_BASE = 16.0D;
    private static final double ATTACK2_PITCH_SWING = 10.0D;
    /** 飞剑速度（格 / tick） */
    private static final double SWORD_SPEED = 0.9D;
    /** 剑从石像身边多高处射出 */
    private static final double SWORD_MUZZLE_Y = 4.2D;
    /** 六把剑围绕身体的半径 */
    private static final double SWORD_RING_RADIUS = 1.15D;
    /** 剑阵割伤半径：贴到这么近就会被环身的剑刃割到 */
    private static final double SWORD_AURA_RANGE = 2.8D;
    /** 剑阵结算间隔（tick）。原版受击后有 10 tick 无敌，间隔必须大于它，否则一半伤害被吞 */
    private static final int SWORD_AURA_INTERVAL = 15;
    /** 剑阵每下伤害：骚扰级别，不是主要输出来源（固定值，不跟攻击力缩放） */
    private static final float SWORD_AURA_DAMAGE = 1.0F;
    /**
     * 飞剑命中/撞墙炸开时的溅射伤害。
     * 默认的 6 是给玩家武器用的，守卫的飞剑走这条路时砍到 3，
     * 否则一发普攻 7.5 点会被 6 点溅射反超，手感完全不对。
     */
    private static final float SWORD_BURST_DAMAGE = 3.0F;

    /** 是否已苏醒，走 DataTracker 让客户端也能读到 */
    private static final TrackedData<Boolean> AWAKE =
            DataTracker.registerData(SwordTombGuardianEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /**
     * 是否处在锁血无敌状态。渲染层要用（发光层在这里切成过载闪烁），
     * 所以必须走 DataTracker 同步；服务端自己的计时还是用 {@link #lastStandTicks}。
     */
    private static final TrackedData<Boolean> LAST_STANDING =
            DataTracker.registerData(SwordTombGuardianEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /**
     * body 层当前动画的播放速度倍率。
     *
     * <p>必须同步：动画是在客户端播的，速度只写在服务端字段里的话，
     * 客户端会按 1.0 播、服务端按 1.5 算伤害帧，又变成"刀劈完了才掉血"。
     * 所以走 DataTracker，改一次就同步给所有追踪者。
     */
    private static final TrackedData<Float> BODY_SPEED =
            DataTracker.registerData(SwordTombGuardianEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 服务端攻击冷却 */
    private int attackCooldown = 0;
    /** 服务端挥砍冷却 */
    private int meleeCooldown = 0;
    /** 苏醒动画还要慢放多少 tick；减到 0 就把 body 速度恢复成 1.0，别把 idle 也拖慢 */
    private int wakeAnimTicks = 0;
    /** 击杀经验：普通怪物只有 5，这个 250 血的守卫给一档精英的量 */
    private static final int XP_TO_DROP = 50;

    /** 剑阵割伤的冷却 */
    private int swordAuraCooldown = 0;
    /** 下一把要射的剑，0~5 循环 */
    private int swordIndex = 0;
    /** 本次 melee 动作开始时的 age；-1 表示这次动作已经结算过 */
    private int meleeStartAge = -1;
    /** 这一刀是第几段：见 STEP_* 常量 */
    private int meleeStep = STEP_MELEE;
    /** 这一刀的命中帧（单段动作各不同；melee4 不用它，用 melee4Pulses） */
    private int meleeHitTick = MELEE_HIT_TICK;
    /** melee4 旋风连斩已经结算了几跳 */
    private int melee4Pulses = 0;
    /** 大招冷却 */
    private int ultCooldown = 0;
    /** 大招蓄力的起点 age；-1 表示没在蓄力 */
    private int ultChargeAge = -1;
    /** 冲击波的起点 age；-1 表示波已经放完 */
    private int ultShockAge = -1;
    /** 客户端上一帧的苏醒状态；null 表示还没记过初值 */
    private Boolean clientAwakeLast;

    /** attack2 旋发的起点 age；-1 表示没在旋发 */
    private int attack2StartAge = -1;
    /** attack2 已经甩出去几把 */
    private int attack2Shots = 0;
    /** attack2 起手时的世界 yaw，往外扫的角度都从这个值算 */
    private float attack2BaseYaw = 0.0F;

    /** 终极大招是否已经用过（一只守卫一辈子只锁一次血） */
    private boolean lastStandUsed = false;
    /** 是否已进入狂暴二阶段（一辈子只进一次，所以用布尔而不是计时） */
    private boolean enraged = false;
    /** 无敌剩余 tick；> 0 时免疫一切伤害（虚空除外） */
    private int lastStandTicks = 0;
    /** melee6 动作起点 age；-1 表示没在放 */
    private int melee6StartAge = -1;

    /** 崩解粒子是否已经炸过（每具尸体只炸一次） */
    private boolean debrisSpawned = false;

    /** Boss 血条。只在服务端创建，惰性初始化 */
    private ServerBossBar bossBar;

    public SwordTombGuardianEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        // 六格高的大块头，抬高单步高度，否则一格台阶就能把它卡死
        this.setStepHeight(1.0F);
        // 击杀经验：MobEntity 的掉落就取这个字段，普通怪物是 5
        this.experiencePoints = XP_TO_DROP;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 250.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22D)
                // 石像外壳：8 点护甲 + 4 点韧性，能扛住铁傀儡一拳（15 伤害 → 12.8）
                .add(EntityAttributes.GENERIC_ARMOR, 8.0D)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 4.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D);
    }

    /** 归入亡灵：亡灵杀手附魔对它加伤，治疗药水伤它、伤害药水治它（和凋零同组） */
    @Override
    public EntityGroup getGroup() {
        return EntityGroup.UNDEAD;
    }

    // ------------------------------------------------------------------
    // 状态同步
    // ------------------------------------------------------------------

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(AWAKE, false);
        this.dataTracker.startTracking(LAST_STANDING, false);
        this.dataTracker.startTracking(BODY_SPEED, 1.0F);
    }

    /** 渲染/动画层用：body 层当前的速度倍率 */
    public double getBodyAnimSpeed() {
        return this.dataTracker.get(BODY_SPEED);
    }

    /**
     * 设置 body 层速度倍率。只在服务端调用，靠 DataTracker 同步给客户端。
     * 必须在 triggerAnim **之前**设好：GeckoLib 的动画时间是"已播 tick × 速度"，
     * 中途改速度会让动作直接跳帧。
     */
    private void setBodyAnimSpeed(double speed) {
        if (!this.getWorld().isClient()) {
            this.dataTracker.set(BODY_SPEED, (float) speed);
        }
    }

    public boolean isAwake() {
        return this.dataTracker.get(AWAKE);
    }

    /**
     * 设置苏醒状态。从睡着变醒的那一次会顺带播 awake 动画并出声；
     * 已经醒着时重复调用是空操作。
     */
    public void setAwake(boolean awake) {
        if (this.isAwake() == awake) {
            return;
        }
        this.dataTracker.set(AWAKE, awake);
        if (awake) {
            // 苏醒动画 1.5 秒（0.5 倍速下 3 秒），这期间不许抬手：否则玩家贴脸唤醒时，
            // melee 会在 0.3 秒后就把 awake 顶掉，动画只播了个开头
            this.attackCooldown = WAKE_LOCK_TICKS;
            this.meleeCooldown = WAKE_LOCK_TICKS;
            // triggerAnim 在服务端会自己发包给追踪者，客户端不需要另外同步
            this.setBodyAnimSpeed(AWAKE_SPEED);
            this.wakeAnimTicks = WAKE_LOCK_TICKS;
            this.triggerAnim("body", "awake");
            this.playSound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 1.6F, 0.7F);
            LOGGER.info("[sword_tomb_guardian] awake triggered at tick {} ({})",
                    this.age, this.getWorld().isClient() ? "client" : "server");
        }
    }

    /**
     * 客户端兜底：GeckoLib 的触发包只发给"那一刻正在追踪这个实体"的玩家。
     * 只要漏一次，表现就是"没播苏醒动画"。这里监听 AWAKE 的 false → true 跳变，
     * 自己再补一次触发，动画一定会播；和触发包撞在同一 tick 也只是从头播一遍，看不出来。
     */
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            boolean awake = this.isAwake();
            if (this.clientAwakeLast != null && awake && !this.clientAwakeLast) {
                this.triggerAnim("body", "awake");
            }
            this.clientAwakeLast = awake;
        } else if (this.getWorld() instanceof ServerWorld serverWorld) {
            if (!this.debrisSpawned && this.deathTime >= DEATH_CRUMBLE_TICK) {
                // 尸体在被移除前只走到 20 tick，崩解粒子就挂在这一帧上，和动画对齐
                this.spawnDeathDebris();
                this.debrisSpawned = true;
            }
            // 身体塌下去之后，剑从祭坛里竖起来
            if (!this.pedestalPlaced && this.deathTime >= DEATH_PEDESTAL_TICK) {
                this.placePedestal(serverWorld);
            }
            this.tickBossBar(serverWorld);
        }
    }

    // ------------------------------------------------------------------
    // Boss 血条
    // ------------------------------------------------------------------

    /** 血条可见半径：超出这个距离的玩家会被移出，避免远处挂一条永远不动的空条 */
    private static final double BOSS_BAR_RANGE = 48.0D;

    private ServerBossBar bossBar() {
        if (this.bossBar == null) {
            this.bossBar = new ServerBossBar(
                    Text.translatable("boss.wangsword.sword_tomb_guardian"),
                    BossBar.Color.PURPLE,
                    BossBar.Style.NOTCHED_10);
        }
        return this.bossBar;
    }

    /**
     * 每 tick 维护血条。
     *
     * <p>石化期间不给血条：一尊还当雕像摆着的石像顶着 Boss 血条太出戏，
     * 血条要等 {@code awake} 触发之后才亮起来。死亡、以及玩家跑出
     * {@link #BOSS_BAR_RANGE} 格，都会把玩家从血条上摘掉。
     */
    private void tickBossBar(ServerWorld world) {
        ServerBossBar bar = this.bossBar();
        if (!this.isAwake() || !this.isAlive()) {
            bar.clearPlayers();
            return;
        }
        bar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        double rangeSq = BOSS_BAR_RANGE * BOSS_BAR_RANGE;
        // 先摘掉不该看见的人：换维度、跑出范围、已掉线（掉线的实体不会再出现在 getPlayers 里，
        // 但血条的玩家集合里还留着，不清就会一直挂着）
        for (ServerPlayerEntity player : bar.getPlayers().toArray(new ServerPlayerEntity[0])) {
            if (player.isRemoved() || player.getWorld() != world
                    || player.squaredDistanceTo(this) > rangeSq) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(this) <= rangeSq) {
                bar.addPlayer(player);
            }
        }
    }

    // ------------------------------------------------------------------
    // 动画
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 底层：剑阵。醒着时循环 idle，六把剑绕身自转；石化时整层停摆
        controllers.add(new AnimationController<>(this, "swords", 5, this::swordsController));
        // 腿部：walk 单独一层，只为了能单独调播放速度而不影响挥砍/苏醒的节奏。
        // 注册在 body 之前，这样 melee 里的腿部关键帧能盖住走路的腿
        controllers.add(new AnimationController<>(this, "legs", 2, this::legController)
                .setAnimationSpeed(WALK_ANIM_SPEED));
        // 上层：身体与四肢。过渡必须是 0 tick——带过渡的话动作会缓入，
        // 视觉上的落刀帧比代码的判定 tick 晚 3 tick，看起来就是"打下去慢半拍"。
        //
        // 速度 handler 是石化的关键：没醒时整层速度 = 0，PETRIFIED（awake 第 0 帧）
        // 就永远停在第一帧，一动不动，像块石头；一醒立刻恢复 1.0。
        controllers.add(new AnimationController<>(this, "body", 0, this::bodyController)
                .setAnimationSpeedHandler(entity -> {
                    // 石化：整层速度 0，把 awake 第 0 帧冻成雕像
                    if (!entity.isAwake() && entity.deathTime <= 0) {
                        return 0.0D;
                    }
                    // 醒着：用当前这一招自己的倍率（melee 是 1.5，其余 1.0）
                    return entity.getBodyAnimSpeed();
                })
                .triggerableAnim("awake", AWAKE_ANIM)
                .triggerableAnim("attack", ATTACK_ANIM)
                .triggerableAnim("attack2", ATTACK2_ANIM)
                .triggerableAnim("melee", MELEE_ANIM)
                .triggerableAnim("melee2", MELEE2_ANIM)
                .triggerableAnim("melee5", MELEE5_ANIM)
                .triggerableAnim("melee4", MELEE4_ANIM)
                .triggerableAnim("melee3", MELEE3_ANIM)
                .triggerableAnim("melee6", MELEE6_ANIM)
                .triggerableAnim("death", DEATH_ANIM));
    }

    /**
     * 剑阵层：石化时整层 STOP。
     *
     * <p>石像身上的剑当然不会自己转圈。停掉这一层之后，六把剑的骨骼交给
     * body 层的石化姿态摆（竖直收在身周），一动不动。
     */
    private PlayState swordsController(AnimationState<SwordTombGuardianEntity> state) {
        if (!this.isAwake()) {
            return PlayState.STOP;
        }
        return state.setAndContinue(IDLE);
    }

    /**
     * 身体层：石化时定格在 awake 第 0 帧，醒着时 STOP，
     * 一次性动作 awake / attack / melee / death 由 triggerableAnim 接管。
     */
    private PlayState bodyController(AnimationState<SwordTombGuardianEntity> state) {
        if (!this.isAwake()) {
            return state.setAndContinue(PETRIFIED);
        }
        return PlayState.STOP;
    }

    /**
     * 腿部层：只在真的在走的时候循环 walk。
     *
     * <p>走动判定同时看 isMoving() 和 limbSwingAmount：前者靠速度，
     * 客户端上生物速度有时是 0，后者是原版按位移算的踏步量，更可靠。
     */
    private PlayState legController(AnimationState<SwordTombGuardianEntity> state) {
        // 石化时腿也必须是死的：被活塞推、被水冲、从台阶上蹭下来都会让
        // limbSwingAmount 非 0，不挡的话雕像会突然迈步
        if (!this.isAwake()) {
            return PlayState.STOP;
        }
        if (WALK_ANIM_READY && (state.isMoving() || state.getLimbSwingAmount() > 0.01F)) {
            return state.setAndContinue(WALK_ANIM);
        }
        return PlayState.STOP;
    }

    /** 播放一次攻击动作（由 VolleyGoal 调用） */
    public void playAttack() {
        this.setBodyAnimSpeed(1.0D);
        this.triggerAnim("body", "attack");
    }

    /**
     * 攻击第二段：头原地转三圈，边转边往外甩光刃（由 VolleyGoal 紧接 attack 调用）。
     *
     * <p>只有在起手时记一次基准 yaw，之后这个动作期间身体不再转向，
     * 头扫过的角度就是实打实的世界方位——玩家看到的"头指哪、剑射哪"才对得上。
     */
    public void playAttack2() {
        this.attack2StartAge = this.age;
        this.attack2Shots = 0;
        this.attack2BaseYaw = this.getYaw();
        this.setBodyAnimSpeed(1.0D);
        this.triggerAnim("body", "attack2");
        this.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 0.7F, 0.75F);
    }

    /**
     * 播放一次近战动作。
     *
     * @param step {@link #STEP_MELEE} 竖劈（0.55s 结算）、{@link #STEP_MELEE2} 横扫
     *             （0.6s 结算、强击退）、{@link #STEP_MELEE5} 重砸（1.1s 结算、大伤 + 挑飞）、
     *             {@link #STEP_MELEE4} 旋风连斩（全程 6 跳）、
     *             {@link #STEP_ULT} 大招（0.85s 结算、地裂冲击波）
     */
    public void playMelee(int step) {
        this.meleeStep = MathHelper.clamp(step, STEP_MELEE, STEP_ULT);
        this.melee4Pulses = 0;
        // 近战插进来就掐掉还没甩完的 attack2，否则会出现"人在劈砍、剑还在往外转着飞"
        this.attack2StartAge = -1;
        // 每段自己的播放倍率：只有 melee（竖劈）提速，其余保持原速。
        // 必须在 triggerAnim 之前写，速度变化在动画中途生效会直接跳帧
        this.setBodyAnimSpeed(this.meleeStep == STEP_MELEE ? MELEE_SPEED : 1.0D);
        if (this.meleeStep == STEP_ULT) {
            this.meleeHitTick = MELEE3_HIT_TICK;
            // 蓄力期间由 tickUlt 往头顶吸粒子，这里只记起点 + 起手风声
            this.ultChargeAge = this.age;
            this.ultCooldown = ULT_COOLDOWN;
            this.triggerAnim("body", "melee3");
            this.playSound(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 1.8F, 0.7F);
        } else if (this.meleeStep == STEP_MELEE4) {
            // 旋风斩没有单点结算，伤害由 spinPulse 一跳一跳地打
            this.triggerAnim("body", "melee4");
        } else if (this.meleeStep == STEP_MELEE5) {
            this.meleeHitTick = MELEE5_HIT_TICK;
            this.triggerAnim("body", "melee5");
            // 抬手时一声低沉的石头摩擦，给 1.5 秒的慢刀留个预告
            this.playSound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 1.0F, 0.6F);
        } else if (this.meleeStep == STEP_MELEE2) {
            this.meleeHitTick = MELEE2_HIT_TICK;
            this.triggerAnim("body", "melee2");
        } else {
            // 竖劈提速 1.5 倍，命中帧必须跟着提前，否则画面劈完了才掉血
            this.meleeHitTick = speedTick(MELEE_HIT_TICK, MELEE_SPEED);
            this.triggerAnim("body", "melee");
        }
        // 记下动作起点，判定用 age 差值对时，动画和结算共用同一个时间轴
        this.meleeStartAge = this.age;
    }

    /**
     * 把按 1 倍速写的 tick 换算成提速后的实际 tick。
     *
     * <p>动画提速后，原本第 11 tick 的落刀点变成第 11/1.5 ≈ 7 tick，
     * 收尾时间同理。至少保证 1 tick，避免极端倍率下算出 0。
     */
    private static int speedTick(int baseTick, double speed) {
        return Math.max(1, (int) Math.round(baseTick / speed));
    }

    // ------------------------------------------------------------------
    // AI
    // ------------------------------------------------------------------

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new WakeGoal(this));
        // 贴近了先砍，砍完拉开距离才放剑
        this.goalSelector.add(1, new MeleeGoal(this));
        this.goalSelector.add(2, new VolleyGoal(this));
        this.goalSelector.add(3, new ApproachGoal(this));
        // 谁先动手打它，就先还手打谁（铁傀儡、其他模组的怪都走这条），
        // 优先级要高于"扫描附近玩家"，否则被围攻时目标会被玩家抢走
        this.targetSelector.add(0, new RevengeGoal(this));
        this.targetSelector.add(1, new AwakeTargetGoal(this));
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        // 头部看向目标：LookControl 先把 headYaw 转过去，身体再慢慢跟上，
        // 两者之间的差值就是渲染时头部偏转的来源。
        // headYaw 是服务端算的，变化时会由 EntityTrackerEntry 同步给客户端
        LivingEntity lookTarget = this.getTarget();
        if (this.isAwake() && lookTarget != null && lookTarget.isAlive()) {
            this.getLookControl().lookAt(lookTarget, 30.0F, 30.0F);
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.meleeCooldown > 0) {
            this.meleeCooldown--;
        }
        // 苏醒动画播完就把 body 速度恢复原速，否则连 idle 的呼吸都会一直是半速
        if (this.wakeAnimTicks > 0 && --this.wakeAnimTicks == 0) {
            this.setBodyAnimSpeed(1.0D);
        }
        if (this.swordAuraCooldown > 0) {
            this.swordAuraCooldown--;
        } else if (this.trySwordAura()) {
            this.swordAuraCooldown = this.enraged ? ENRAGED_SWORD_AURA_INTERVAL : SWORD_AURA_INTERVAL;
        }
        if (this.ultCooldown > 0) {
            this.ultCooldown--;
        }
        this.tickEnrage();
        this.tickUlt();
        this.tickSpinVolley();
        this.tickLastStand();
    }

    // ------------------------------------------------------------------
    // 二阶段：狂暴
    // ------------------------------------------------------------------

    /** 每 tick 检查该不该转阶段；已经狂暴、还没苏醒、或者已经死了都直接跳过 */
    private void tickEnrage() {
        if (this.enraged || !this.isAwake() || this.getWorld().isClient() || this.deathTime > 0) {
            return;
        }
        if (this.getHealth() > this.getMaxHealth() * ENRAGE_HEALTH_RATIO) {
            return;
        }
        this.enrage();
    }

    /**
     * 转入狂暴：攻击与移速各挂一层永久修饰符，连招间隔和剑阵间隔一起缩短，
     * 血条换成红色并改标题。
     *
     * <p>用固定 UUID 的 {@code addPersistentModifier} 而不是每 tick 维护临时修饰符：
     * 狂暴是不可逆的一次性状态，挂上去就不用再管，实体移除时随之消失。
     * 加之前先查 {@code getModifier} 是为了防重入——万一 tickEnrage 被重复调到，
     * 同一个 UUID 不会叠出两层。
     *
     * <p>注意所有伤害结算都读 {@code GENERIC_ATTACK_DAMAGE}（普攻、横扫、剑阵、冲击波），
     * 所以只改这一个属性，整套技能的伤害就一起涨了，不用逐处改系数。
     */
    private void enrage() {
        this.enraged = true;

        EntityAttributeInstance attack = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attack != null && attack.getModifier(ENRAGE_ATTACK_ID) == null) {
            attack.addPersistentModifier(new EntityAttributeModifier(
                    ENRAGE_ATTACK_ID, ENRAGE_ATTACK_NAME, ENRAGE_ATTACK_BONUS,
                    EntityAttributeModifier.Operation.ADDITION));
        }
        EntityAttributeInstance speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(ENRAGE_SPEED_ID) == null) {
            speed.addPersistentModifier(new EntityAttributeModifier(
                    ENRAGE_SPEED_ID, ENRAGE_SPEED_NAME, ENRAGE_SPEED_BONUS,
                    EntityAttributeModifier.Operation.ADDITION));
        }

        ServerBossBar bar = this.bossBar();
        bar.setColor(BossBar.Color.RED);
        bar.setName(Text.translatable("boss.wangsword.sword_tomb_guardian.enraged"));

        this.playSound(SoundEvents.ENTITY_WARDEN_ROAR, 1.4F, 1.15F);
        this.playSound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5F, 0.6F);

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.FLASH,
                    this.getX(), this.getY() + 3.4D, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            for (int i = 0; i < 36; i++) {
                double angle = i * Math.PI * 2.0D / 36.0D;
                serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX() + Math.cos(angle) * 2.2D,
                        this.getY() + 0.6D + this.random.nextDouble() * 5.0D,
                        this.getZ() + Math.sin(angle) * 2.2D,
                        1, 0.0D, 0.02D, 0.0D, 0.02D);
            }
            double rangeSq = BOSS_BAR_RANGE * BOSS_BAR_RANGE;
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (player.squaredDistanceTo(this) <= rangeSq) {
                    player.sendMessage(Text.translatable("message.wangsword.guardian.enrage"), true);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 终极大招：濒死锁血 + 巨剑横扫
    // ------------------------------------------------------------------

    /**
     * 会不会被打死、且是本命第一次濒死。
     *
     * <p>真正拦伤害的入口在文件末尾那个 {@code damage()} 覆写里（那里还兼管
     * "挨打就醒"），这里只负责判断。
     */
    private boolean canEnterLastStand(DamageSource source, float amount) {
        return !this.lastStandUsed
                && this.isAwake()
                && this.deathTime == 0
                && amount > 0.0F
                && !isUnstoppable(source)
                && this.getHealth() - amount <= 0.0F;
    }

    /**
     * 无视无敌的伤害：虚空、/kill（generic_kill）这类。
     *
     * <p>1.20.1 的 {@link DamageSource} 没有 isOutOfWorld()，官方就是用
     * {@link DamageTypeTags#BYPASSES_INVULNERABILITY} 这一组标签来判断的，
     * 所以锁血不该拦它们——否则被打进虚空会永远卡在 1 血下不去。
     */
    private static boolean isUnstoppable(DamageSource source) {
        return source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** 锁血、进入无敌、起手 melee6 */
    private void enterLastStand() {
        this.lastStandUsed = true;
        this.setHealth(1.0F);
        this.lastStandTicks = LAST_STAND_TICKS;
        this.dataTracker.set(LAST_STANDING, true);
        // 把正在进行的所有动作全部掐掉：这一秒里只允许 melee6 存在，
        // 否则会出现"人已经在甩巨剑，飞剑还在往外转着发"的重叠
        this.meleeStartAge = -1;
        this.attack2StartAge = -1;
        this.ultChargeAge = -1;
        this.ultShockAge = -1;
        this.melee6StartAge = this.age;
        this.getNavigation().stop();
        this.setBodyAnimSpeed(1.0D);
        this.triggerAnim("body", "melee6");
        this.playSound(SoundEvents.ENTITY_WARDEN_ROAR, 1.6F, 0.8F);
        this.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.2F, 0.7F);

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            // 起手：一道白光 + 一圈魂焰往外推，告诉玩家"这一下打空了"
            serverWorld.spawnParticles(ParticleTypes.FLASH,
                    this.getX(), this.getY() + 3.4D, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI * 2.0D / 24.0D;
                serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX() + Math.cos(angle) * 1.4D,
                        this.getY() + 1.0D + this.random.nextDouble() * 4.5D,
                        this.getZ() + Math.sin(angle) * 1.4D,
                        2, 0.0D, 0.05D, 0.0D, 0.01D);
            }
        }
    }

    /** melee6 的驱动：蓄力吸能 + 到点结算 */
    private void tickLastStand() {
        if (this.lastStandTicks > 0) {
            this.lastStandTicks--;
            if (this.lastStandTicks == 0) {
                // 无敌结束，同步给客户端把过载发光收回去
                this.dataTracker.set(LAST_STANDING, false);
            }
        }
        if (this.melee6StartAge < 0) {
            return;
        }
        if (this.deathTime > 0) {
            this.melee6StartAge = -1;
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        int t = this.age - this.melee6StartAge;
        // 蓄力阶段：剑口往里吸末地烛，0.75 秒后才炸出去
        if (t < MELEE6_HIT_TICK) {
            Vec3d dir = this.getRotationVec(1.0F);
            double px = this.getX() + dir.x * 3.0D;
            double pz = this.getZ() + dir.z * 3.0D;
            serverWorld.spawnParticles(ParticleTypes.END_ROD,
                    px, this.getY() + 3.0D, pz, 3, 1.6D, 1.2D, 1.6D, -0.06D);
            return;
        }

        this.ultimateSword(serverWorld);
        this.melee6StartAge = -1;
    }

    /**
     * 巨剑横扫的结算：正前方一条 10 格长的扇面。
     *
     * <p>动画里那柄剑是被放大到 5 倍甩出去的（swordRoot scale 2.47/6.13/5.16，
     * 位置从身前一路推到 100 单位），所以判定不是环身，而是又长又宽的一个扇面：
     * 朝向阈值 0.1 比普攻更宽，射程 10 格——离得远也躲不掉。
     */
    private void ultimateSword(ServerWorld world) {
        Vec3d look = this.getRotationVec(1.0F);
        Vec3d facing = new Vec3d(look.x, 0.0D, look.z).normalize();
        boolean hit = false;

        for (net.minecraft.entity.Entity entity : world.getOtherEntities(
                this, this.getBoundingBox().expand(MELEE6_RANGE))) {
            if (!(entity instanceof LivingEntity target) || target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            Vec3d toTarget = new Vec3d(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
            if (toTarget.lengthSquared() < 1.0E-4D) {
                continue;
            }
            if (toTarget.length() > MELEE6_RANGE || facing.dotProduct(toTarget.normalize()) < 0.1D) {
                continue;
            }
            target.timeUntilRegen = 0;
            if (!target.damage(this.getDamageSources().mobAttack(this), MELEE6_DAMAGE)) {
                continue;
            }
            target.takeKnockback(MELEE6_KNOCKBACK,
                    this.getX() - target.getX(), this.getZ() - target.getZ());
            Vec3d velocity = target.getVelocity();
            target.setVelocity(velocity.x, velocity.y + MELEE6_LIFT, velocity.z);
            target.velocityModified = true;
            hit = true;
        }

        // 甩剑轨迹上铺一串蓝白冲击环（自绘粒子 wangsword:sword_wave，替掉原版 SONIC_BOOM）
        for (int step = 1; step <= 5; step++) {
            double distance = step * MELEE6_RANGE / 5.0D;
            world.spawnParticles(ParticleRegistry.SWORD_WAVE,
                    this.getX() + facing.x * distance,
                    this.getY() + 3.0D + (this.random.nextDouble() - 0.5D) * 0.7D,
                    this.getZ() + facing.z * distance,
                    1, 0.15D, 0.15D, 0.15D, 0.02D);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                this.getX() + facing.x * 4.0D, this.getY() + 3.0D,
                this.getZ() + facing.z * 4.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                this.getX() + facing.x * 5.0D, this.getY() + 2.6D,
                this.getZ() + facing.z * 5.0D, 12, 2.2D, 0.6D, 2.2D, 0.0D);
        if (hit) {
            world.spawnParticles(ParticleTypes.CRIT,
                    this.getX() + facing.x * 4.0D, this.getY() + 2.6D,
                    this.getZ() + facing.z * 4.0D, 24, 2.2D, 1.2D, 2.2D, 0.35D);
        }

        this.playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2.0F, 0.85F);
        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6F, 0.45F);
        this.playSound(SoundRegistry.SLASH_IMPACT, 2.0F, 0.4F);
    }

    /** 客户端/渲染层用：是否处在锁血无敌状态 */
    public boolean isLastStanding() {
        return this.dataTracker.get(LAST_STANDING);
    }

    /**
     * attack2 的旋发驱动：到点就沿"头此刻的朝向"甩一把光刃。
     *
     * <p>和 melee4 一样用 age 差值对时间轴，被跳帧时 while 会一次补齐，
     * 不会因为服务器卡一下少射两把。
     */
    private void tickSpinVolley() {
        if (this.attack2StartAge < 0) {
            return;
        }
        if (this.isDead() || this.deathTime > 0) {
            this.attack2StartAge = -1;
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        int t = this.age - this.attack2StartAge;
        while (this.attack2Shots < ATTACK2_SHOTS
                && t >= ATTACK2_FIRST_SHOT + this.attack2Shots * ATTACK2_SHOT_INTERVAL) {
            this.fireSpinBlade(serverWorld, this.attack2Shots);
            this.attack2Shots++;
        }
        if (t > ATTACK2_CAST_TICKS) {
            this.attack2StartAge = -1;
        }
    }

    /**
     * 甩出旋发的一把光刃。
     *
     * <p>方向 = 起手 yaw + 头在这一 tick 转过的角度，所以八把剑在 1 秒里
     * 铺成一圈：第 i 把的方位角就是 54° × (4 + 2i)。
     *
     * @param index 第几把（0 起）
     */
    private void fireSpinBlade(ServerWorld world, int index) {
        int shotTick = ATTACK2_FIRST_SHOT + index * ATTACK2_SHOT_INTERVAL;
        float yaw = (float) (this.attack2BaseYaw + shotTick * SPIN_DEG_PER_TICK);
        // 俯仰上下摆一摆，一圈剑不至于全糊在同一个水平面上
        float pitch = (float) (ATTACK2_PITCH_BASE + ATTACK2_PITCH_SWING * Math.sin(index * 1.1D));
        Vec3d dir = Vec3d.fromPolar(pitch, yaw);

        Vec3d origin = new Vec3d(
                this.getX() + dir.x * 0.9D,
                this.getY() + ATTACK2_MUZZLE_Y,
                this.getZ() + dir.z * 0.9D);

        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                * ATTACK2_DAMAGE_RATIO;
        FlyingSwordEntity blade = new FlyingSwordEntity(
                EntityRegistry.FLYING_SWORD, world, this,
                ItemStack.EMPTY, damage, dir.multiply(SWORD_SPEED), true)
                .setBurstDamage(SWORD_BURST_DAMAGE);
        blade.setPosition(origin.x, origin.y, origin.z);
        world.spawnEntity(blade);

        // 出剑反馈：剑口一小簇蓝焰，音高随序号往上抬，听感就是"转着扫出去"
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                origin.x, origin.y, origin.z, 12, 0.12D, 0.12D, 0.12D, 0.05D);
        world.spawnParticles(ParticleTypes.END_ROD,
                origin.x, origin.y, origin.z, 4, 0.08D, 0.08D, 0.08D, 0.02D);
        this.playSound(SoundEvents.ITEM_TRIDENT_THROW,
                0.55F, 1.0F + index * 0.04F);
    }

    /** 大招可用：血量掉到一半以下，且冷却好了 */
    private boolean ultReady() {
        return this.getHealth() <= this.getMaxHealth() * ULT_HEALTH_RATIO && this.ultCooldown <= 0;
    }

    /**
     * 大招的两段表现（只在服务端跑，粒子天然会同步给客户端）：
     * 蓄力 0~17 tick 往头顶吸魂焰，砸下后 12 tick 里把冲击波一圈圈推到 6.5 格。
     */
    private void tickUlt() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        if (this.ultChargeAge >= 0) {
            int t = this.age - this.ultChargeAge;
            if (t > MELEE3_HIT_TICK) {
                this.ultChargeAge = -1;
            } else {
                // 半径随时间收拢，看起来就是一圈魂焰被吸进刀里
                double progress = (double) t / MELEE3_HIT_TICK;
                double radius = 4.2D * (1.0D - progress) + 0.55D;
                for (int i = 0; i < 4; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2.0D;
                    serverWorld.spawnParticles(
                            i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                            this.getX() + Math.cos(angle) * radius,
                            this.getY() + 2.2D + this.random.nextDouble() * 3.2D,
                            this.getZ() + Math.sin(angle) * radius,
                            1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
        if (this.ultShockAge >= 0) {
            int t = this.age - this.ultShockAge;
            if (t > ULT_SHOCK_TICKS) {
                this.ultShockAge = -1;
            } else {
                double radius = 1.0D + (ULT_RADIUS - 1.0D) * t / ULT_SHOCK_TICKS;
                this.spawnShockRing(serverWorld, radius);
            }
        }
    }

    /**
     * 在地面画一圈粒子，半径由调用方给定。
     *
     * <p>每个点各自贴地（剑冢是五级台阶，平面圆环会悬在半空），
     * 但只接受离自己不超 2 格的地面高度——否则在洞穴里会把波画到地表去。
     */
    private void spawnShockRing(ServerWorld world, double radius) {
        int points = 28;
        double baseY = this.getY() + 0.12D;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2.0D / points;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING,
                    MathHelper.floor(x), MathHelper.floor(z)) + 0.05D;
            double y = Math.abs(groundY - this.getY()) <= 2.0D ? groundY : baseY;
            world.spawnParticles(
                    i % 4 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z, 1, 0.0D, 0.03D, 0.0D, 0.0D);
        }
    }

    /** 砸地：中心炸开 + 全向冲击波伤害，伤害和击退都随距离衰减 */
    private void ultSlam() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6F, 0.6F);
        this.playSound(SoundRegistry.SLASH_IMPACT, 2.0F, 0.5F);
        serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                this.getX(), this.getY() + 0.4D, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                this.getX(), this.getY() + 0.3D, this.getZ(), 40, 0.45D, 0.12D, 0.45D, 0.08D);
        this.ultShockAge = this.age;

        for (net.minecraft.entity.Entity entity : this.getWorld().getOtherEntities(
                this, this.getBoundingBox().expand(ULT_RADIUS))) {
            if (!(entity instanceof LivingEntity target) || target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            double distance = Math.sqrt(this.squaredDistanceTo(target));
            if (distance > ULT_RADIUS) {
                continue;
            }
            float ratio = (float) (distance / ULT_RADIUS);
            float damage = ULT_DAMAGE_NEAR + (ULT_DAMAGE_FAR - ULT_DAMAGE_NEAR) * ratio;
            target.damage(this.getDamageSources().mobAttack(this), damage);
            // 冲击波是往外推的，所以不看着目标也能命中
            target.takeKnockback(ULT_KNOCKBACK * (1.0D - 0.5D * ratio),
                    this.getX() - target.getX(), this.getZ() - target.getZ());
        }
    }

    /** 玩家靠近就醒，并把他设为目标 */
    private static class WakeGoal extends Goal {
        private final SwordTombGuardianEntity guardian;
        private PlayerEntity pending;

        WakeGoal(SwordTombGuardianEntity guardian) {
            this.guardian = guardian;
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.guardian.isAwake() || this.guardian.getWorld().isClient()) {
                return false;
            }
            PlayerEntity player = this.guardian.getWorld().getClosestPlayer(this.guardian, WAKE_RANGE);
            // 旁观者、创造模式都不自动唤醒：创造模式主要用于站桩观察石化/发光效果，
            // 想让它醒过来直接打一下（damage() 里的逻辑不受游戏模式限制）。
            if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative()) {
                return false;
            }
            if (!this.guardian.canSee(player)) {
                return false;
            }
            this.pending = player;
            return true;
        }

        @Override
        public void start() {
            LOGGER.info("[sword_tomb_guardian] woken by proximity, player {} blocks away",
                    this.pending == null ? -1.0D
                            : Math.round(Math.sqrt(this.guardian.squaredDistanceTo(this.pending)) * 10.0D) / 10.0D);
            this.guardian.setAwake(true);
            if (this.pending != null && !this.pending.isCreative()) {
                this.guardian.setTarget(this.pending);
            }
            this.pending = null;
        }

        @Override
        public boolean shouldContinue() {
            return false;
        }
    }

    /** 醒着才锁定玩家，睡着时不会提前获得目标 */
    private static class AwakeTargetGoal extends ActiveTargetGoal<PlayerEntity> {
        private final SwordTombGuardianEntity guardian;

        AwakeTargetGoal(SwordTombGuardianEntity guardian) {
            super(guardian, PlayerEntity.class, true);
            this.guardian = guardian;
        }

        @Override
        public boolean canStart() {
            return this.guardian.isAwake() && super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return this.guardian.isAwake() && super.shouldContinue();
        }
    }

    /**
     * 近身连招：melee → melee2 → melee5（重砸）→ melee4（旋风连斩）→（残血时）melee3 大招，
     * 然后从头再来。单段动作用自己的命中帧结算，melee4 则在 1 秒里一跳一跳地持续结算。
     */
    private static class MeleeGoal extends Goal {
        private final SwordTombGuardianEntity guardian;
        private int charge = -1;
        /** 连招进度：0 = melee，1 = melee2，2 = melee5，3 = melee4，4 = melee3 */
        private int comboStep = STEP_MELEE;

        MeleeGoal(SwordTombGuardianEntity guardian) {
            this.guardian = guardian;
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!this.guardian.isAwake() || this.guardian.getWorld().isClient()) {
                return false;
            }
            // 终极大招期间不许插别的动作，否则 melee6 会被普通挥砍顶掉
            if (this.guardian.isLastStanding() || this.guardian.melee6StartAge >= 0) {
                return false;
            }
            if (this.guardian.meleeCooldown > 0) {
                return false;
            }
            LivingEntity target = this.guardian.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            return this.guardian.squaredDistanceTo(target) <= MELEE_RANGE * MELEE_RANGE;
        }

        @Override
        public void start() {
            int step = this.comboStep;
            if (step == STEP_ULT && !this.guardian.ultReady()) {
                // 血量还没到一半、或者大招在冷却：直接从 melee 重新起手
                step = STEP_MELEE;
            }
            this.charge = switch (step) {
                case STEP_ULT -> MELEE3_CAST_TICKS;
                case STEP_MELEE4 -> MELEE4_CAST_TICKS;
                case STEP_MELEE5 -> MELEE5_CAST_TICKS;
                // 竖劈提速了，这一段占用的 tick 也要同比缩短，不然会多站一会
                case STEP_MELEE -> speedTick(MELEE_CAST_TICKS, MELEE_SPEED);
                default -> MELEE_CAST_TICKS;
            };
            this.guardian.getNavigation().stop();
            this.guardian.playMelee(step);
            this.comboStep = step >= STEP_ULT ? STEP_MELEE : step + 1;
        }

        @Override
        public void tick() {
            LivingEntity target = this.guardian.getTarget();
            if (target != null) {
                this.guardian.faceTowards(target);
            }
            this.charge--;
            if (this.guardian.meleeStartAge < 0) {
                return;
            }
            int t = this.guardian.age - this.guardian.meleeStartAge;

            if (this.guardian.meleeStep == STEP_MELEE4) {
                // 旋风连斩：到点就打一跳，被跳帧也不会整段丢掉
                while (this.guardian.melee4Pulses < MELEE4_HITS
                        && t >= MELEE4_FIRST_HIT + this.guardian.melee4Pulses * MELEE4_HIT_INTERVAL) {
                    this.guardian.spinPulse(this.guardian.melee4Pulses == MELEE4_HITS - 1);
                    this.guardian.melee4Pulses++;
                }
                if (this.guardian.melee4Pulses >= MELEE4_HITS) {
                    this.guardian.meleeStartAge = -1;
                }
                return;
            }

            // 用 age 对齐动画时间轴，而不是数 charge：即使某 tick 被跳过，
            // >= 也保证这一刀只结算一次、且不会永远不结算
            if (t >= this.guardian.meleeHitTick) {
                if (this.guardian.meleeStep == STEP_ULT) {
                    this.guardian.ultSlam();
                } else if (this.guardian.meleeStep == STEP_MELEE5) {
                    this.guardian.slamAttack();
                } else {
                    this.guardian.sweepAttack(this.guardian.meleeStep == STEP_MELEE2);
                }
                this.guardian.meleeStartAge = -1;
            }
        }

        @Override
        public boolean shouldContinue() {
            // 终极大招打断了这一刀，这里要主动结束，别让它继续按旧时间轴结算
            return this.charge > 0 && this.guardian.getTarget() != null
                    && !this.guardian.isLastStanding();
        }

        @Override
        public void stop() {
            this.charge = -1;
            // 动作被打断时清掉起点，避免下一次判定错位
            this.guardian.meleeStartAge = -1;
            // 段与段之间不设冷却，只在一轮打完（回到起手段，或残血大招没就绪、
            // 只能回头重开一轮）之后才停手
            boolean roundOver = this.comboStep == STEP_MELEE
                    || (this.comboStep == STEP_ULT && !this.guardian.ultReady());
            // 狂暴后停手时间减半，连招之间的空档更短
            this.guardian.meleeCooldown = roundOver
                    ? (this.guardian.enraged ? ENRAGED_MELEE_COOLDOWN : MELEE_COOLDOWN)
                    : 0;
        }
    }

    /** 目标离得太远就走过去，进到 STOP_DISTANCE 就停 */
    private static class ApproachGoal extends Goal {
        private final SwordTombGuardianEntity guardian;
        private int repathTimer = 0;

        ApproachGoal(SwordTombGuardianEntity guardian) {
            this.guardian = guardian;
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!this.guardian.isAwake() || this.guardian.getWorld().isClient()) {
                return false;
            }
            LivingEntity target = this.guardian.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            return this.guardian.squaredDistanceTo(target) > STOP_DISTANCE * STOP_DISTANCE;
        }

        @Override
        public void start() {
            this.moveToTarget();
        }

        @Override
        public void tick() {
            this.moveToTarget();
        }

        /** 路径走完或被挡了都会让 navigation 空闲，隔几 tick 重新起步，别每 tick 都算路径 */
        private void moveToTarget() {
            LivingEntity target = this.guardian.getTarget();
            if (target == null) {
                return;
            }
            this.guardian.getLookControl().lookAt(target, 30.0F, 30.0F);
            if (this.repathTimer > 0) {
                this.repathTimer--;
                return;
            }
            if (this.guardian.getNavigation().isIdle()) {
                this.guardian.getNavigation().startMovingTo(target, 1.0D);
                this.repathTimer = 10;
            }
        }

        @Override
        public boolean shouldContinue() {
            LivingEntity target = this.guardian.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            return this.guardian.squaredDistanceTo(target) > STOP_DISTANCE * STOP_DISTANCE;
        }

        @Override
        public void stop() {
            this.guardian.getNavigation().stop();
        }
    }

    /** 站桩放剑：冷却好了就朝目标打一套 attack，第 7 tick 射出一把飞剑 */
    private static class VolleyGoal extends Goal {
        private final SwordTombGuardianEntity guardian;
        private int charge = -1;
        /** 0 = attack（瞄准单发），1 = attack2（转头旋发） */
        private int phase = 0;

        VolleyGoal(SwordTombGuardianEntity guardian) {
            this.guardian = guardian;
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!this.guardian.isAwake() || this.guardian.getWorld().isClient()) {
                return false;
            }
            // 终极大招期间不放剑，melee6 必须是这一秒里唯一的动作
            if (this.guardian.isLastStanding() || this.guardian.melee6StartAge >= 0) {
                return false;
            }
            if (this.guardian.attackCooldown > 0) {
                return false;
            }
            LivingEntity target = this.guardian.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            // 只在射程内、且已经拉开到近战圈外时放剑
            double distSq = this.guardian.squaredDistanceTo(target);
            return distSq > MELEE_RANGE * MELEE_RANGE
                    && distSq <= VOLLEY_RANGE * VOLLEY_RANGE
                    && this.guardian.canSee(target);
        }

        @Override
        public void start() {
            this.charge = CAST_TICKS;
            this.phase = 0;
            this.guardian.playAttack();
        }

        @Override
        public void tick() {
            LivingEntity target = this.guardian.getTarget();
            if (target == null || !target.isAlive()) {
                return;
            }
            this.charge--;

            if (this.phase == 0) {
                this.guardian.faceTowards(target);
                // 减到 13 时正好是动画开始后的第 7 tick，也就是 0.35s 的挥剑帧
                if (this.charge == CAST_TICKS - RELEASE_TICK) {
                    this.guardian.fireSword(target);
                }
                // 第一段播完直接接第二段，中间不留空档
                if (this.charge <= 0) {
                    this.phase = 1;
                    this.charge = ATTACK2_CAST_TICKS;
                    this.guardian.playAttack2();
                }
                return;
            }

            // 第二段：这里故意不再 faceTowards——头转过的角度就是剑射出去的方位，
            // 身体要是跟着扭，玩家看到的和实际飞的就不是一个方向。
            // 甩剑本身由 tickSpinVolley 按 age 驱动，不依赖本 Goal 是否还活着。
        }

        @Override
        public boolean shouldContinue() {
            // 终极大招打断放剑流程
            if (this.guardian.isLastStanding()) {
                return false;
            }
            LivingEntity target = this.guardian.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (this.phase == 1) {
                // 第二段是原地旋发，不因为目标跑远就中断
                return this.charge > 0;
            }
            if (this.charge <= 0) {
                return false;
            }
            return this.guardian.squaredDistanceTo(target) <= VOLLEY_RANGE * 1.5D * (VOLLEY_RANGE * 1.5D);
        }

        @Override
        public void stop() {
            this.charge = -1;
            this.phase = 0;
            this.guardian.attackCooldown = ATTACK_COOLDOWN;
        }
    }

    // ------------------------------------------------------------------
    // 攻击
    // ------------------------------------------------------------------

    /**
     * 挥砍伤害判定：以自身为圆心的前方扇形，半径 5 格，命中扣血 + 击退。
     * 刀有 3.4 格长，5 格半径和视觉范围大致对得上。
     */
    /**
     * 前方扇形挥砍。
     *
     * @param heavy true = melee2 的横扫：击退更远、声音更沉
     */
    private void sweepAttack(boolean heavy) {
        if (this.getWorld().isClient()) {
            return;
        }
        Vec3d look = this.getRotationVec(1.0F);
        Vec3d facing = new Vec3d(look.x, 0.0D, look.z).normalize();
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        boolean hit = false;

        for (net.minecraft.entity.Entity entity : this.getWorld().getOtherEntities(
                this, this.getBoundingBox().expand(MELEE_RANGE))) {
            if (!(entity instanceof LivingEntity target) || target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            Vec3d toTarget = new Vec3d(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
            if (toTarget.lengthSquared() < 1.0E-4D || facing.dotProduct(toTarget.normalize()) < 0.15D) {
                continue;
            }
            target.damage(this.getDamageSources().mobAttack(this), damage);
            target.takeKnockback(heavy ? MELEE2_KNOCKBACK : MELEE_KNOCKBACK,
                    this.getX() - target.getX(), this.getZ() - target.getZ());
            hit = true;
        }
        if (hit) {
            this.playSound(SoundRegistry.SLASH_IMPACT, heavy ? 1.6F : 1.3F, heavy ? 0.7F : 0.9F);
        }
    }

    /**
     * melee5 重砸的结算：前方扇形，单次大伤害 + 挑飞。
     *
     * <p>和 {@link #sweepAttack} 的区别在分量和收尾：
     * 伤害是普攻的 1.6 倍，击退接近横扫的两倍，再往垂直方向补一点速度，
     * 看起来就是被这一剑从地面掀起来。判定的朝向阈值放宽到 0，
     * 因为动画里它踏步前压、剑是斜向下砸的，实际覆盖比"正前方"要宽。
     */
    private void slamAttack() {
        if (this.getWorld().isClient()) {
            return;
        }
        Vec3d look = this.getRotationVec(1.0F);
        Vec3d facing = new Vec3d(look.x, 0.0D, look.z).normalize();
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                * MELEE5_DAMAGE_RATIO;
        boolean hit = false;

        for (net.minecraft.entity.Entity entity : this.getWorld().getOtherEntities(
                this, this.getBoundingBox().expand(MELEE_RANGE))) {
            if (!(entity instanceof LivingEntity target) || target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            Vec3d toTarget = new Vec3d(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
            if (toTarget.lengthSquared() < 1.0E-4D || facing.dotProduct(toTarget.normalize()) < 0.0D) {
                continue;
            }
            // 慢刀只有一下，不能被前一刀的受击无敌帧吞掉
            target.timeUntilRegen = 0;
            if (!target.damage(this.getDamageSources().mobAttack(this), damage)) {
                continue;
            }
            target.takeKnockback(MELEE5_KNOCKBACK,
                    this.getX() - target.getX(), this.getZ() - target.getZ());
            Vec3d velocity = target.getVelocity();
            target.setVelocity(velocity.x, velocity.y + MELEE5_LIFT, velocity.z);
            target.velocityModified = true;
            hit = true;
        }

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            // 落点在身前 3 格：一圈冲击尘 + 几道扫击弧，强调"砸在地上"
            double frontX = this.getX() + facing.x * 3.0D;
            double frontZ = this.getZ() + facing.z * 3.0D;
            serverWorld.spawnParticles(ParticleTypes.CLOUD,
                    frontX, this.getY() + 0.2D, frontZ, 16, 1.2D, 0.15D, 1.2D, 0.08D);
            serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    frontX, this.getY() + 1.2D, frontZ, 4, 1.4D, 0.2D, 1.4D, 0.0D);
            if (hit) {
                serverWorld.spawnParticles(ParticleTypes.CRIT,
                        frontX, this.getY() + 1.6D, frontZ, 12, 1.0D, 0.8D, 1.0D, 0.2D);
            }
        }

        // 低音铁砧落地 = 重物砸地；命中再加一声刀刃切入
        this.playSound(SoundEvents.BLOCK_ANVIL_LAND, hit ? 1.5F : 1.0F, 0.55F);
        if (hit) {
            this.playSound(SoundRegistry.SLASH_IMPACT, 1.8F, 0.55F);
        }
    }

    /**
     * melee4 旋风连斩的一跳：不看朝向，半径内整圈都吃伤害。
     *
     * <p>每一跳都先把目标的受击无敌帧清零——原版挨打后有 10 tick 无敌，
     * 3 tick 一跳的话后两跳会被吞掉，表现就成了"只有第一下掉血"，
     * 这一点是"全程持续造成伤害"能不能成立的关键。
     *
     * @param last 最后一跳：额外把人甩出去，给这段连招收个尾
     */
    private void spinPulse(boolean last) {
        if (this.getWorld().isClient()) {
            return;
        }
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                * MELEE4_HIT_DAMAGE_RATIO;
        boolean hit = false;

        for (net.minecraft.entity.Entity entity : this.getWorld().getOtherEntities(
                this, this.getBoundingBox().expand(MELEE4_RADIUS))) {
            if (!(entity instanceof LivingEntity target) || target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            // expand 出来的是方盒，再按球距离筛一遍才是圆形判定
            if (this.squaredDistanceTo(target) > MELEE4_RADIUS * MELEE4_RADIUS) {
                continue;
            }

            target.timeUntilRegen = 0;
            if (!target.damage(this.getDamageSources().mobAttack(this), damage)) {
                continue;
            }
            if (last) {
                target.takeKnockback(MELEE4_FINAL_KNOCKBACK,
                        this.getX() - target.getX(), this.getZ() - target.getZ());
            }
            hit = true;
        }

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            // 环身一圈扫击粒子，跟着转速刷，看起来就是刀刃在转
            int points = 10;
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0D / points + this.random.nextDouble() * 0.35D;
                serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                        this.getX() + Math.cos(angle) * MELEE4_RADIUS * 0.8D,
                        this.getY() + 2.4D + this.random.nextDouble() * 1.6D,
                        this.getZ() + Math.sin(angle) * MELEE4_RADIUS * 0.8D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        this.playSound(SoundRegistry.SLASH_IMPACT,
                last ? 1.5F : (hit ? 0.8F : 0.45F),
                last ? 0.95F : 1.45F);
    }

    /**
     * 剑阵割伤：贴到 2.8 格以内，环身旋转的那六把剑会持续割目标。
     *
     * <p>定位是"别贴着站"的惩罚，不是主要输出——2 点伤害 / 0.75 秒，
     * 顺带把人往外推一点。打不中（对方在受击无敌帧里）就不进冷却，
     * 下一 tick 再试，保证这次结算不会白丢。
     */
    private boolean trySwordAura() {
        if (this.getWorld().isClient() || !this.isAwake()) {
            return false;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (this.squaredDistanceTo(target) > SWORD_AURA_RANGE * SWORD_AURA_RANGE) {
            return false;
        }
        if (!target.damage(this.getDamageSources().mobAttack(this), SWORD_AURA_DAMAGE)) {
            return false;
        }
        target.takeKnockback(0.3D, this.getX() - target.getX(), this.getZ() - target.getZ());
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            double y = target.getY() + target.getHeight() * 0.5D;
            serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), y, target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), y, target.getZ(), 5, 0.28D, 0.28D, 0.28D, 0.02D);
        }
        this.playSound(SoundRegistry.SLASH_IMPACT, 0.5F, 1.7F);
        return true;
    }

    /** 原地转向目标（不放剑、不走动的时候也别僵着背对玩家） */
    private void faceTowards(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float wanted = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float delta = MathHelper.wrapDegrees(wanted - this.getYaw());
        float yaw = this.getYaw() + MathHelper.clamp(delta, -12.0F, 12.0F);
        this.setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
    }

    /**
     * 从身边六个浮剑位里挑一把射出去，按 sword1~sword6 顺序循环，
     * 看起来就是"身边的剑少一把、飞向玩家"。
     */
    private void fireSword(LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        double angle = Math.toRadians(this.getBodyYaw())
                + Math.PI * 2.0D * this.swordIndex / SWORD_SLOTS;
        this.swordIndex = (this.swordIndex + 1) % SWORD_SLOTS;

        Vec3d origin = new Vec3d(
                this.getX() - Math.sin(angle) * SWORD_RING_RADIUS,
                this.getY() + SWORD_MUZZLE_Y,
                this.getZ() + Math.cos(angle) * SWORD_RING_RADIUS);

        Vec3d aim = target.getPos().add(0.0D, target.getHeight() * 0.55D, 0.0D);
        double distance = aim.distanceTo(origin);
        // 按飞行时间做 6 成预判，打移动目标不至于全空
        aim = aim.add(target.getVelocity().multiply(distance / SWORD_SPEED * 0.6D));

        Vec3d delta = aim.subtract(origin);
        double length = Math.max(delta.length(), 1.0E-4D);
        Vec3d velocity = delta.multiply(SWORD_SPEED / length);

        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * 0.75F;
        // 灵体形态：守卫没有"手里那把剑"的物品，不借用王剑的物品模型，改成幽蓝光刃
        FlyingSwordEntity sword = new FlyingSwordEntity(
                EntityRegistry.FLYING_SWORD, serverWorld, this,
                ItemStack.EMPTY, damage, velocity, true)
                .setBurstDamage(SWORD_BURST_DAMAGE);
        sword.setPosition(origin.x, origin.y, origin.z);
        serverWorld.spawnEntity(sword);

        this.playSound(SoundEvents.ITEM_TRIDENT_THROW, 1.1F, 1.2F);
        // 出剑特效：一圈蓝焰炸开 + 末地烛光点，让"剑从哪把飞出来的"一眼看清
        serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                origin.x, origin.y, origin.z, 24, 0.18D, 0.18D, 0.18D, 0.06D);
        serverWorld.spawnParticles(ParticleTypes.END_ROD,
                origin.x, origin.y, origin.z, 10, 0.12D, 0.12D, 0.12D, 0.02D);
    }

    // ------------------------------------------------------------------
    // 杂项
    // ------------------------------------------------------------------

    /**
     * 挨打也会醒，并把攻击者设为目标。
     *
     * <p>同时是终极大招的入口：会死的那一下被压到"刚好剩 1 点血"，
     * 然后锁血无敌、起手 melee6。一只守卫一辈子只锁一次。
     */
    @Override
    public boolean damage(DamageSource source, float amount) {
        // 锁血后摇期间免疫一切伤害（虚空、/kill 这类无视无敌的伤害放行）
        if (!this.getWorld().isClient() && this.lastStandTicks > 0 && !isUnstoppable(source)) {
            return false;
        }
        // 石化：这一下最多只吃 1 点血，防止还没醒就被一发高伤秒掉。
        // 先压再判，压完之后走正常流程，所以这一击既削不掉多少血，也照样会把它打醒
        if (!this.getWorld().isClient() && !this.isAwake() && !isUnstoppable(source)) {
            amount = Math.min(amount, PETRIFIED_MAX_DAMAGE);
        }
        boolean lastStand = !this.getWorld().isClient() && this.canEnterLastStand(source, amount);
        // 濒死这一击只结算到剩 1 点：护甲/附魔照原版算，但数值上不可能打穿，
        // 所以不用等 super 结算完再补血，也就不存在"先死再复活"的一帧
        boolean hurt = super.damage(source,
                lastStand ? Math.max(0.0F, this.getHealth() - 1.0F) : amount);
        if (hurt && !this.getWorld().isClient()) {
            if (lastStand) {
                this.enterLastStand();
            }
            if (!this.isAwake()) {
                LOGGER.info("[sword_tomb_guardian] woken by damage from {}",
                        source.getAttacker() == null ? "unknown" : source.getAttacker().getName().getString());
            }
            this.setAwake(true);
            if (source.getAttacker() instanceof LivingEntity attacker) {
                this.setTarget(attacker);
            }
        }
        return hurt;
    }

    /** 石像不该被玩家推着走 */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** 作为 Boss，不随玩家走远而被清除 */
    @Override
    public boolean isPersistent() {
        return true;
    }

    /**
     * 记下召唤它的祭坛位置。祭坛召唤时调用一次，死亡后石中剑就长在这个坐标上方。
     */
    public void setAltarPos(BlockPos pos) {
        this.altarPos = pos == null ? null : pos.toImmutable();
    }

    /**
     * 把石中剑基座插到祭坛上方那一格。
     *
     * <p>守卫就站在祭坛正上方，尸体在下沉过程中把这一格腾出来，所以这个位置通常还是空气。
     * 如果玩家提前在那里堆了方块就不放——宁可不给，也不要覆盖别人的建筑。
     */
    private void placePedestal(ServerWorld world) {
        this.pedestalPlaced = true;
        if (this.altarPos == null) {
            return;
        }
        BlockPos target = this.altarPos.up();
        BlockState existing = world.getBlockState(target);
        if (!existing.isAir() && !existing.isReplaceable()) {
            return;
        }

        world.setBlockState(target, BlocksRegistry.STONE_SWORD_PEDESTAL.getDefaultState()
                .with(StoneSwordPedestalBlock.HAS_ITEM, true), Block.NOTIFY_ALL);
        // 方块实体随 setBlockState 一起创建，这里再确认一次存的是默认的那把剑
        if (world.getBlockEntity(target) instanceof org.examplee.wangsword.block.StoneSwordPedestalBlockEntity pedestal) {
            pedestal.setStoredItem(org.examplee.wangsword.block.StoneSwordPedestalBlockEntity.defaultStack());
        }

        world.spawnParticles(ParticleTypes.END_ROD,
                target.getX() + 0.5D, target.getY() + 0.6D, target.getZ() + 0.5D,
                18, 0.18D, 0.5D, 0.18D, 0.02D);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.altarPos != null) {
            nbt.putLong("AltarPos", this.altarPos.asLong());
        }
        nbt.putBoolean("PedestalPlaced", this.pedestalPlaced);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.altarPos = nbt.contains("AltarPos")
                ? BlockPos.fromLong(nbt.getLong("AltarPos"))
                : null;
        this.pedestalPlaced = nbt.getBoolean("PedestalPlaced");
    }

    /**
     * 死亡时触发 death 动画（卸剑崩解）。
     *
     * <p>triggerAnim 在服务端会自己发包给追踪者，所以死亡那一刻在场的玩家都能看到。
     * death 动画只驱动身体（body1 / 四肢 / head / cloth），**不碰 sword1~6**，
     * 那六把剑继续由底层 swords 控制器的 idle 带着转，死亡过程里不会有一帧跳变。
     */
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        // 击杀记一次：解锁裂冠幽灵在主世界夜间的刷新
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            org.examplee.wangsword.util.WraithProgress.markGuardianSlain(serverWorld);
        }
        if (this.bossBar != null) {
            this.bossBar.clearPlayers();
        }
        if (DEATH_ANIM_ENABLED) {
            this.setBodyAnimSpeed(1.0D);
            this.triggerAnim("body", "death");
        }
    }

    /** 被移除（区块卸载、/kill 等）时也要把血条收干净，否则玩家会一直挂着一条空条 */
    @Override
    public void remove(RemovalReason reason) {
        if (this.bossBar != null) {
            this.bossBar.clearPlayers();
        }
        // 兜底：正常情况下基座已经在 deathTime 16 时放好；
        // 万一尸体提前被移走（某些模组/指令），这里补上，别让玩家白打一场
        if (reason == Entity.RemovalReason.KILLED && this.isDead() && !this.pedestalPlaced
                && this.getWorld() instanceof ServerWorld serverWorld) {
            this.placePedestal(serverWorld);
        }
        super.remove(reason);
    }

    /**
     * 崩解：身体塌下的那一帧炸一把碎石。
     *
     * <p>石像碎掉该有石头碎屑，所以用石砖 / 裂纹石砖的 BLOCK 粒子，
     * 落地会像真的碎了一地；再散一圈魂焰表示胸腔里的核心灭了，
     * 最后补几点末地烛的火星，像剑断时崩出来的光。
     */
    private void spawnDeathDebris() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        double cx = this.getX();
        double cz = this.getZ();

        // 沿身高分三层：脚下一大堆、腰部、头部，越往上范围越小（塌下来的形状）
        BlockState rubble = Blocks.STONE_BRICKS.getDefaultState();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        for (int layer = 0; layer < 3; layer++) {
            double y = this.getY() + 0.35D + layer * (this.getHeight() * 0.3D);
            double spread = 1.15D - layer * 0.2D;
            world.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, layer == 1 ? cracked : rubble),
                    cx, y, cz, 18, spread, 0.3D, spread, 0.12D);
        }

        // 核心熄灭：胸腔位置往外散一圈魂焰
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                cx, this.getY() + this.getHeight() * 0.45D, cz,
                26, 0.85D, 0.55D, 0.85D, 0.02D);
        // 断剑崩出的火星
        world.spawnParticles(ParticleTypes.END_ROD,
                cx, this.getY() + this.getHeight() * 0.8D, cz,
                12, 0.7D, 0.55D, 0.7D, 0.08D);

        this.playSound(SoundEvents.BLOCK_STONE_BREAK, 1.4F, 0.75F);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundRegistry.SLASH_IMPACT;
    }

    /** 石头崩解，铁傀儡死亡声是现成里最贴的（石块互撞 + 低沉落地） */
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
