package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d; // 🌟 新增：用来计算速度向量
import net.minecraft.world.World;
import org.examplee.wangsword.Material.BowMaterial;
import org.examplee.wangsword.entity.ThunderBoltEntity;
import org.examplee.wangsword.entity.WangArrowEntity;
import org.examplee.wangsword.item.ISkillWeapon;
import org.examplee.wangsword.registry.EntityRegistry;

import java.util.List;

public class WangBowItem extends BowItem implements ISkillWeapon {

    /** 普通箭的伤害倍率：最终伤害 ≈ ceil(箭速 × 该值)，原版是 2.0 */
    public static final double NORMAL_ARROW_DAMAGE = 8.0;

    /**
     * 客户端标记：当前是否在用 X 蓄力（雷涌），以及已经蓄了多少 tick。
     *
     * <p>为什么要自己数：X 蓄力是客户端 {@code setCurrentHand} 强制拉弓的，
     * MC 的物品使用计时（{@code getItemUseTime} / {@code getItemUseTimeLeft}）在这种强制状态下不推进，
     * 直接拿来用就会得到「贴图一直停在未拉弓、进度条不动」这两个症状。
     * 所以由 {@code handleUltimate} 每 tick 把 {@code ultHold} 写进来，
     * 模型谓词和 HUD 进度条都读它。只在客户端使用。
     */
    public static boolean CLIENT_ULT_CHARGING = false;
    public static int CLIENT_ULT_CHARGE_TICKS = 0;

    /** 服务端：每个玩家开始拉弓的墙钟毫秒数，TPS 掉帧时也能算准 3 秒 */
    private static final java.util.Map<java.util.UUID, Long> USE_STARTED_AT = new java.util.HashMap<>();

    /**
     * 把「已蓄 tick」映射成模型 {@code pull} 值。
     *
     * <p>模型的蓄力贴图按 {@code pull} 切在 0.65 / 0.9 两档。X 蓄力总共只有 24 tick，
     * 线性映射的话中间那张只出现 5 tick（0.25 秒），肉眼等于没有——这就是「只有未拉弓和拉满」的原因。
     * 这里把进度按三分之一等分，让三张贴图各占约 0.4 秒，才看得出来是在拉。
     */
    public static float ultPullFor(int elapsed) {
        float p = Math.min(1.0F, Math.max(0.0F, elapsed / (float) ULT_CHARGE_TICKS));
        if (p < 1.0F / 3.0F) {
            return p / (1.0F / 3.0F) * 0.64F;
        }
        if (p < 2.0F / 3.0F) {
            return 0.65F + (p - 1.0F / 3.0F) / (1.0F / 3.0F) * 0.24F;
        }
        return 0.9F + (p - 2.0F / 3.0F) / (1.0F / 3.0F) * 0.1F;
    }

    /** 需要"命中即召雷"的箭（服务端轮询用） */
    private static final java.util.List<net.minecraft.entity.projectile.ArrowEntity> LIGHTNING_ARROWS =
            new java.util.ArrayList<>();

    public static void trackLightningArrow(net.minecraft.entity.projectile.ArrowEntity arrow) {
        LIGHTNING_ARROWS.add(arrow);
    }

    /**
     * 每个服务端 tick 调用一次：检测被跟踪的箭是否命中（速度归零或被移除），
     * 命中就在它最后的位置召唤闪电。
     */
    public static void tickLightningArrows() {
        java.util.Iterator<net.minecraft.entity.projectile.ArrowEntity> it = LIGHTNING_ARROWS.iterator();
        while (it.hasNext()) {
            net.minecraft.entity.projectile.ArrowEntity arrow = it.next();

            if (arrow.getWorld().isClient()) {
                it.remove();
                continue;
            }

            boolean hit = arrow.isRemoved() || arrow.getVelocity().lengthSquared() < 0.01D;
            if (!hit) {
                // 过期兜底清理（原版箭 1200 tick 后会自己消失）
                if (arrow.age > 1200) it.remove();
                continue;
            }

            it.remove();
            if (arrow.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                // 蓝电：换成带皮肤的自定义落雷。
                // cosmetic = false，伤害 / 点火 / 充能苦力怕 / 引雷针仍走原版 LightningEntity 的逻辑，
                // 只是外观由 ThunderBoltRenderer 画成蓝色，不再是原版那道白雷。
                ThunderBoltEntity.spawn(sw, arrow.getPos(), ThunderBoltEntity.BLUE, false);
            }
        }
    }
    // ================== 【被动 · 静电护体】 ==================
    //
    // 手持弓时身边一圈电场：敌对生物靠近就挨一下电，并被轻推开半步。
    //
    // 为什么用 CombatTargets::isHostile 而不是 isSkillTarget：
    // 这是「自己会打人」的常驻效果，不受玩家控制。用 isSkillTarget 会把路过的村民、
    // 牛羊一并电死，整座村庄变屠宰场——和剑阵、飞剑是同一类问题，必须收窄。
    //
    // 为什么是轻推：被动不该有强控制。0.28 只能把贴脸的怪挤开半步，
    // 够玩家把弓放下换个角度，不够把怪吹飞。
    //
    // 设计意图：这把弓要拉 3 秒蓄力才能放「流星」，那 3 秒正是最脆弱的时候。
    // 有了这层电场，蓄力越久反而越安全——这是它和太刀「雷纹」最大的区别：
    // 雷纹是打中才叠（进攻向），静电是靠近就挨（防守向）。

    /** 电场半径（格） */
    public static final double AURA_RADIUS = 3.5D;
    /** 每隔多少 tick 电一次（0.5 秒） */
    public static final int AURA_INTERVAL = 10;
    /** 每次伤害 */
    public static final float AURA_DAMAGE = 2.0F;
    /** 轻推强度 */
    public static final float AURA_PUSH = 0.28F;

    /** 每个服务端 tick 调一次：手持弓的玩家身边放电。由 Wangsword 的 END_SERVER_TICK 驱动 */
    public static void tickStaticAura(net.minecraft.server.MinecraftServer server) {
        // 每 0.5 秒结算一次，没到点整轮跳过（放在最外层，别在循环里判断）
        if (server.getTicks() % AURA_INTERVAL != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isDead() || player.isSpectator()) {
                continue;
            }
            if (!(player.getMainHandStack().getItem() instanceof WangBowItem)) {
                continue;
            }
            if (!(player.getWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
                continue;
            }

            net.minecraft.util.math.Box box = player.getBoundingBox().expand(AURA_RADIUS);
            boolean zapped = false;
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                    org.examplee.wangsword.util.CombatTargets::isHostile)) {
                if (target.squaredDistanceTo(player) > AURA_RADIUS * AURA_RADIUS) {
                    continue;
                }
                target.timeUntilRegen = 0;
                target.damage(world.getDamageSources().lightningBolt(), AURA_DAMAGE);
                // 沿「玩家 → 目标」的方向推出去
                target.takeKnockback(AURA_PUSH,
                        player.getX() - target.getX(), player.getZ() - target.getZ());
                zapped = true;
            }

            // 只有真的电到东西才冒火花；否则站在那儿一直冒电会很吵
            if (zapped) {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + 1.0D, player.getZ(),
                        10, AURA_RADIUS * 0.55D, 0.5D, AURA_RADIUS * 0.55D, 0.05D);
            }
        }
    }

    // ================== 【X 键 · 雷涌】 ==================
    //
    // 和太刀右键「万雷」的区别，一句话：
    //   万雷 = 在一片地上随机落雷（面）；雷涌 = 命中一个生物，从它身上炸开一圈电流（点 → 放射）。
    // 所以这里不做落雷雨、不做区域持续，只做「打中即放电」的一次性脉冲。
    //
    // 三个决定：
    // 1. 不做飞行弹体，直接准心射线索敌。飞行体的碰撞 / 追踪 / 越界回收全是 bug 温床，
    //    而 32 格内即时命中，玩家根本分不出差别。
    // 2. 中心那道蓝雷用 cosmetic = true（只出画面），伤害全部由 damagePulse 结算，
    //    不然真雷会点燃地皮、充能苦力怕。
    // 3. 打空不进冷却——空放还进 CD 是最恶心人的设计。

    /** 电流放射半径（格） */
    public static final double SURGE_RADIUS = 6.0D;
    /** 放射的电流束数 */
    public static final int SURGE_RAYS = 24;
    /** 扩散动画帧数（tick）：9 tick = 0.45 秒扫出去，比之前 6 tick 更看得清 */
    public static final int SURGE_FRAMES = 9;
    /**
     * 放电的总寿命（tick）：前 {@link #SURGE_FRAMES} 帧是扩散，之后留边界余韵。
     *
     * <p>为什么要留：扩散只有 0.45 秒，玩家眼睛刚跟上就没了，根本记不住范围多大。
     * 余韵阶段用两圈粒子把范围「画」在地上——外圈白环是杀伤范围（6 格），
     * 内圈青蓝是过载范围（4 格），粒子随时间变稀，读作淡出。
     */
    public static final int SURGE_RING_LIFE = 30;
    /** 中心伤害 */
    public static final float SURGE_DAMAGE_CENTER = 28.0F;
    /** 边缘伤害（按距离线性衰减到它） */
    public static final float SURGE_DAMAGE_EDGE = 12.0F;
    /** 麻痹时长（1.5 秒） */
    public static final int SURGE_PARALYZE_TICKS = 30;
    /** 冷却 tick（10 秒） */
    public static final int SURGE_COOLDOWN = 200;
    /**
     * 蓄力 tick：按住 X 满 24 tick（1.2 秒）才算装上，松手才放。
     *
     * <p>为什么要蓄力：直接按下就炸太廉价，也不符合这把弓「瞄准」的节奏。
     * 客户端 {@code WangSwordClientInit.handleUltimate} 会把蓄力进度经由
     * {@link ISkillWeapon#triggerUltimate} 的 {@code charged} 参数传上来，
     * 没蓄满这里直接拒掉，且不消耗冷却。
     */
    public static final int ULT_CHARGE_TICKS = 24;
    /** 索敌距离（格） */
    public static final double SURGE_RANGE = 32.0D;

    /** 正在播放的电流扩散（纯表现）。静态表 + 全局 tick，和上面的 LIGHTNING_ARROWS 一个路子 */
    private static final java.util.List<Surge> SURGES = new java.util.ArrayList<>();

    /** 一次放电的运行时状态：只在服务端存在，播完即弃，不落盘 */
    private static final class Surge {
        final net.minecraft.server.world.ServerWorld world;
        final Vec3d center;
        int frame;

        Surge(net.minecraft.server.world.ServerWorld world, Vec3d center) {
            this.world = world;
            this.center = center;
        }
    }

    // ---- 过载：让雷涌不只是「炸一下」，而是留下持续削弱的标记 ----
    //
    // 为什么需要它：单看那一发，28 点还是不如右键「流星」的 30 点，
    // 终极技就名不副实了。过载把总额拉到 28 + 20，且附带减速，
    // 于是两者的分工才清楚：右键是「一发入魂的单体点杀」，雷涌是「范围清场 + 持续压制」。

    /** 过载半径：只有中心这几格的目标会挂上 */
    public static final double OVERLOAD_RADIUS = 4.0D;
    /** 过载持续 tick（5 秒） */
    public static final int OVERLOAD_DURATION = 100;
    /** DoT 间隔（0.5 秒） */
    public static final int OVERLOAD_INTERVAL = 10;
    /** 每次 DoT 伤害 */
    public static final float OVERLOAD_DAMAGE = 2.0F;

    /** 当前带着过载的目标。用实体引用而不是 UUID——只活 5 秒，查表反而多一层出错的机会 */
    private static final java.util.List<Overload> OVERLOADS = new java.util.ArrayList<>();

    /**
     * 雷涌在通用冷却池里的技能 ID。
     *
     * <p>冷却统一交给 {@link org.examplee.wangsword.util.SkillCooldowns}：按技能 ID 记，
     * 不用 {@code ItemCooldownManager}（那是物品级冷却，会把右键「流星」一起锁死）。
     * 客户端 {@code SkillCooldownHud} 也用同一个 ID 注册图标。
     */
    public static final String SKILL_SURGE = "surge";

    private static final class Overload {
        final net.minecraft.server.world.ServerWorld world;
        final LivingEntity target;
        int ticksLeft;

        Overload(net.minecraft.server.world.ServerWorld world, LivingEntity target) {
            this.world = world;
            this.target = target;
            this.ticksLeft = OVERLOAD_DURATION;
        }
    }

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        if (!(player.getMainHandStack().getItem() instanceof WangBowItem)) {
            return;
        }
        if (!(player.getWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
            return;
        }

        // 冷却走通用技能冷却池：按技能 ID 记，不碰物品冷却（否则右键「流星」会被一起锁掉）
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_SURGE)) {
            org.examplee.wangsword.util.SkillCooldowns.notifyCooldown(player, SKILL_SURGE, "雷涌");
            return;
        }

        // 没蓄满：只提示，不进冷却，也不索敌
        if (!charged) {
            player.sendMessage(Text.translatable("message.wangsword.bow.ult.not_charged",
                    String.format(java.util.Locale.ROOT, "%.1f", ULT_CHARGE_TICKS / 20.0D)), true);
            return;
        }

        // 索敌：命中生物才放电，打空不进冷却
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.sendMessage(Text.translatable("message.wangsword.bow.ult.no_target"), true);
            return;
        }

        // getPos() 是脚下坐标；先取出来再放电，目标当场被打死也不影响脉冲落点
        Vec3d center = target.getPos();
        discharge(world, player, center);
        // 进冷却 + 同步客户端画图标（就这一句，冷却和图标永远一致）
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_SURGE, SURGE_COOLDOWN);
    }

    /** 准心射线找一个能打的生物（判定盒放宽 1 格，糊脸和擦边都算中） */
    private static LivingEntity findTarget(ServerPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        Vec3d end = eye.add(dir.multiply(SURGE_RANGE));
        net.minecraft.util.hit.EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                player, eye, end,
                player.getBoundingBox().stretch(dir.multiply(SURGE_RANGE)).expand(1.0D),
                e -> e instanceof LivingEntity living
                        && org.examplee.wangsword.util.CombatTargets.isSkillTarget(living),
                SURGE_RANGE * SURGE_RANGE);
        return hit == null ? null : (LivingEntity) hit.getEntity();
    }

    /** 放电：拉一条电流过去 → 结算径向伤害 → 三雷齐落 + 白闪 → 起扩散动画 */
    private static void discharge(net.minecraft.server.world.ServerWorld world,
                                  ServerPlayerEntity player, Vec3d center) {
        drawTracer(world, player.getEyePos(), center);

        // 三雷齐落：单根雷柱太单薄，正中一道 + 左右各偏一点，视觉上才是一「丛」
        ThunderBoltEntity.spawn(world, center, ThunderBoltEntity.BLUE, true);
        for (int i = 0; i < 2; i++) {
            double angle = (Math.PI * 2.0D) * i / 2.0D + world.random.nextDouble() * 0.9D;
            double radius = 1.0D + world.random.nextDouble() * 0.7D;
            Vec3d at = new Vec3d(
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius);
            ThunderBoltEntity.spawn(world, at, ThunderBoltEntity.BLUE, true);
        }

        // 命中瞬间一记白闪：没有这一下，整段放电会显得「没砸到」
        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH,
                center.x, center.y + 1.0D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);

        damagePulse(world, center);

        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.5F, 0.8F);
        // 低八度的滚雷补底，冲击感靠它
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.1F, 0.65F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                org.examplee.wangsword.registry.SoundRegistry.BOW_SHOOT,
                SoundCategory.PLAYERS, 1.6F, 0.55F);
        SURGES.add(new Surge(world, center));
    }

    /** 一次性径向伤害：中心满伤，越靠边越轻 */
    private static void damagePulse(net.minecraft.server.world.ServerWorld world, Vec3d center) {
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(center, center).expand(SURGE_RADIUS);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                org.examplee.wangsword.util.CombatTargets::isSkillTarget)) {
            double distSq = target.squaredDistanceTo(center);
            if (distSq > SURGE_RADIUS * SURGE_RADIUS) {
                continue;
            }
            double t = Math.sqrt(distSq) / SURGE_RADIUS;   // 0 = 正中，1 = 最外圈
            float damage = (float) (SURGE_DAMAGE_CENTER
                    - (SURGE_DAMAGE_CENTER - SURGE_DAMAGE_EDGE) * t);
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().lightningBolt(), damage);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, SURGE_PARALYZE_TICKS, 1));
            // 中心几格：额外挂上过载
            if (distSq <= OVERLOAD_RADIUS * OVERLOAD_RADIUS) {
                markOverload(world, target);
            }
        }
    }

    /** 挂过载标记；已经挂着的只刷新时长，不叠第二份 */
    private static void markOverload(net.minecraft.server.world.ServerWorld world, LivingEntity target) {
        for (Overload overload : OVERLOADS) {
            if (overload.target == target) {
                overload.ticksLeft = OVERLOAD_DURATION;
                return;
            }
        }
        OVERLOADS.add(new Overload(world, target));
    }

    /** 每个服务端 tick 调一次：推进过载 DoT。由 Wangsword 的 END_SERVER_TICK 驱动 */
    public static void tickOverloads() {
        if (OVERLOADS.isEmpty()) {
            return;
        }
        java.util.Iterator<Overload> it = OVERLOADS.iterator();
        while (it.hasNext()) {
            Overload overload = it.next();
            overload.ticksLeft--;

            LivingEntity target = overload.target;
            if (overload.ticksLeft <= 0 || target.isRemoved() || !target.isAlive()) {
                it.remove();
                continue;
            }
            if (overload.ticksLeft % OVERLOAD_INTERVAL != 0) {
                continue;
            }

            target.timeUntilRegen = 0;
            target.damage(overload.world.getDamageSources().lightningBolt(), OVERLOAD_DAMAGE);
            // 减速每次只续 0.75 秒：标记一断，减速也跟着消失
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 15, 1));

            // 身上一直跳小电花——玩家得看得见「这怪还带着电」
            overload.world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                    target.getX(), target.getY() + target.getHeight() * 0.6D, target.getZ(),
                    4, 0.25D, 0.3D, 0.25D, 0.02D);
        }
    }

    /** 从眼睛到命中点拉一条电流：细碎电火花 + 每 4 点一颗亮白，读作「射出去的那道电」 */
    private static void drawTracer(net.minecraft.server.world.ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.001D) {
            return;
        }
        Vec3d step = delta.multiply(1.0D / length).multiply(0.25D);
        int points = (int) (length / 0.25D);
        for (int i = 0; i <= points; i++) {
            Vec3d p = from.add(step.multiply(i));
            world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                    p.x, p.y, p.z, 1, 0.03D, 0.03D, 0.03D, 0.02D);
            if (i % 4 == 0) {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                        p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 每个服务端 tick 调一次：推进电流扩散动画。由 Wangsword 的 END_SERVER_TICK 驱动 */
    public static void tickSurges() {
        java.util.Iterator<Surge> it = SURGES.iterator();
        while (it.hasNext()) {
            Surge surge = it.next();
            surge.frame++;
            if (surge.frame > SURGE_RING_LIFE) {
                it.remove();
                continue;
            }
            drawSurge(surge);
        }
    }

    /**
     * 画一帧：每条束画成一小段折线（3 点），外圈每 2 帧补一圈冲击波。
     *
     * <p>之前每束每帧只有 1 颗粒子，看着就是一串零散的点，读不出「电流」。
     * 现在一束是「短线段 + 亮白头」，再加成形的一圈白环，才像放电。
     */
    private static void drawSurge(Surge surge) {
        // 扩散已经扫完：转入边界余韵
        if (surge.frame > SURGE_FRAMES) {
            drawLinger(surge);
            return;
        }
        net.minecraft.server.world.ServerWorld world = surge.world;
        double t = surge.frame / (double) SURGE_FRAMES;
        double reach = SURGE_RADIUS * t;

        for (int i = 0; i < SURGE_RAYS; i++) {
            double angle = (Math.PI * 2.0D) * i / SURGE_RAYS
                    + (world.random.nextDouble() - 0.5D) * 0.22D;

            // 每条束只画外侧一段（内圈已经扫过去了），3 个点连成一小段折线
            double from = reach * 0.5D;
            double to = reach * (0.9D + world.random.nextDouble() * 0.25D);
            for (int seg = 0; seg < 3; seg++) {
                double dist = from + (to - from) * (seg / 2.0D);
                double jitter = angle + (world.random.nextDouble() - 0.5D) * 0.18D;
                double x = surge.center.x + Math.cos(jitter) * dist;
                double z = surge.center.z + Math.sin(jitter) * dist;
                // 束在竖直方向也散一散，全压在一个平面上会很假
                double y = surge.center.y + 0.5D + world.random.nextDouble() * 1.0D;
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                        x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            // 束头点一颗亮白，那是电流的「锋」
            if ((i & 1) == 0) {
                double x = surge.center.x + Math.cos(angle) * to;
                double z = surge.center.z + Math.sin(angle) * to;
                double y = surge.center.y + 0.7D + world.random.nextDouble() * 0.6D;
                world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                        x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        // 冲击波外圈：每 2 帧补一圈，扩散才有「形状」而不只是一团雾
        if (surge.frame % 2 == 0) {
            final int ringPoints = 36;
            for (int i = 0; i < ringPoints; i++) {
                double angle = (Math.PI * 2.0D) * i / ringPoints;
                double x = surge.center.x + Math.cos(angle) * reach;
                double z = surge.center.z + Math.sin(angle) * reach;
                world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                        x, surge.center.y + 0.1D, z, 1, 0.0D, 0.02D, 0.0D, 0.0D);
            }
        }

        // 中心那一团：前 3 帧最亮，之后收掉，免得糊成一片白
        if (surge.frame <= 3) {
            world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                    surge.center.x, surge.center.y + 0.9D, surge.center.z,
                    24, 0.4D, 0.5D, 0.4D, 0.15D);
            // 青蓝色的鬼火往上飘，给中心一点「颜色」而不只是白点
            world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                    surge.center.x, surge.center.y + 0.7D, surge.center.z,
                    6, 0.3D, 0.35D, 0.3D, 0.03D);
        }
    }

    /**
     * 余韵：扩散结束后把范围「画」在地上。
     *
     * <p>外圈白环 = 杀伤半径，内圈青蓝 = 过载半径。粒子数量随时间递减，
     * 视觉上就是淡出——粒子本身没有 alpha，只能靠密度做透明度。
     */
    private static void drawLinger(Surge surge) {
        int span = SURGE_RING_LIFE - SURGE_FRAMES;
        int left = SURGE_RING_LIFE - surge.frame;
        double fade = Math.max(0.18D, left / (double) span);   // 1 → 0.18

        // 外圈：杀伤范围，隔帧画，密度高一些才显眼
        if ((surge.frame & 1) == 0) {
            drawRing(surge, SURGE_RADIUS, Math.max(10, (int) Math.round(44 * fade)), false);
        }
        // 内圈：过载范围，隔 3 帧画，故意比外圈稀，一眼能分出两档
        if (surge.frame % 3 == 0) {
            drawRing(surge, OVERLOAD_RADIUS, Math.max(6, (int) Math.round(26 * fade)), true);
        }
    }

    /** 在地面画一圈粒子（贴脚 +0.1，压在地皮上才读得出是个圆） */
    private static void drawRing(Surge surge, double radius, int points, boolean soul) {
        net.minecraft.server.world.ServerWorld world = surge.world;
        net.minecraft.particle.ParticleEffect particle = soul
                ? net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME
                : net.minecraft.particle.ParticleTypes.END_ROD;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D) * i / points
                    + (world.random.nextDouble() - 0.5D) * 0.06D;
            double x = surge.center.x + Math.cos(angle) * radius;
            double z = surge.center.z + Math.sin(angle) * radius;
            world.spawnParticles(particle, x, surge.center.y + 0.1D, z,
                    1, 0.0D, 0.03D, 0.0D, 0.0D);
        }
    }

    public WangBowItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return BowMaterial.INSTANCE.getRepairIngredient().test(ingredient);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        super.usageTick(world, user, stack, remainingUseTicks);
        if (!world.isClient()) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20, 3, false, false, true));

            // 记录真实开始时间：服务端 TPS 掉到 18 时，3 秒内只数到 47 tick，
            // 光靠 tick 判定会出现"条满了还差十几 tick"，所以按墙钟判。
            long now = System.currentTimeMillis();
            Long start = USE_STARTED_AT.get(user.getUuid());
            if (start == null || now - start > 10_000L) {
                USE_STARTED_AT.put(user.getUuid(), now);
            }

            // 刚开始拉弓的那一 tick，播放拉弓音
            if (remainingUseTicks == this.getMaxUseTime(stack)) {
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                        SoundCategory.PLAYERS, 0.9F, 1.0F);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        // 1.20.1 中可以直接用 this.getMaxUseTime(stack)
        // 客户端条按 60 tick 走，但服务端 TPS 一掉就只数到 47 tick 左右，
        // 所以以墙钟 3 秒为准，tick 数只作兜底。
        int pullTicks = this.getMaxUseTime(stack) - remainingUseTicks;
        // 只在服务端取：单人游戏同进程，客户端先跑一遍 onStoppedUsing，
        // 若在这里就 remove 掉，服务端再取就是 null。
        Long startedAt = world.isClient() ? null : USE_STARTED_AT.remove(user.getUuid());
        long heldMs = startedAt == null ? 0L : System.currentTimeMillis() - startedAt;
        boolean isSkillTriggered = heldMs >= 2900L || pullTicks >= 55;

        if (isSkillTriggered && !world.isClient()) {
            // ================== 🌟 3秒技能：发射自定义穿透实体 ==================

            // 1. 生成自定义实体
            WangArrowEntity specialArrow = new WangArrowEntity(EntityRegistry.WANG_ARROW, world, user);
            // 把箭的初始位置放在玩家眼睛前方一点点
            specialArrow.setPos(user.getX(), user.getEyeY() - 0.1, user.getZ());

            // 2. 🌟 手动计算速度并设置（因为不再继承原版箭的特定方法）
            float speed = 3.0F;
            Vec3d look = user.getRotationVec(1.0F); // 获取玩家视线方向
            specialArrow.setVelocity(look.x * speed, look.y * speed, look.z * speed);

            // 3. 消耗弓箭耐久（消耗 1 点耐久）
            stack.damage(1, user, (p) -> p.sendToolBreakStatus(user.getActiveHand()));

            // 4. 统计与音效
            if (user instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.incrementStat(Stats.USED.getOrCreateStat(this));
            }
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    org.examplee.wangsword.registry.SoundRegistry.BOW_SHOOT,
                    SoundCategory.PLAYERS, 1.8F, 1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);

            // 5. 将实体生成在服务端世界中
            world.spawnEntity(specialArrow);

        } else {
            // ================== 普通箭：自己生成，方便提高伤害 ==================
            // 原版伤害 = ceil(箭速 × 2.0)，满拉弓约 6~9 点，太低。
            // 这里改成 ceil(箭速 × NORMAL_ARROW_DAMAGE)，满拉弓约 24 点。
            if (!world.isClient() && user instanceof net.minecraft.entity.player.PlayerEntity player) {
                float pull = BowItem.getPullProgress(this.getMaxUseTime(stack) - remainingUseTicks);
                if (pull < 0.1F) return;

                net.minecraft.entity.projectile.ArrowEntity arrow =
                        new net.minecraft.entity.projectile.ArrowEntity(world, player);
                arrow.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
                arrow.setVelocity(player.getRotationVector().multiply(pull * 3.0F));
                arrow.setDamage(NORMAL_ARROW_DAMAGE);
                arrow.setCritical(pull >= 1.0F);
                arrow.setNoGravity(true);   // 关闭下坠，直线飞行
                arrow.setGlowing(true);     // 发光，暗处也能看见
                world.spawnEntity(arrow);
                trackLightningArrow(arrow); // 命中后召唤闪电

                stack.damage(1, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    serverPlayer.incrementStat(Stats.USED.getOrCreateStat(this));
                }
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        org.examplee.wangsword.registry.SoundRegistry.BOW_SHOOT,
                        SoundCategory.PLAYERS, 1.8F,
                        1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
            }
        }
    }
    @Override
    public void appendTooltip(ItemStack itemStack, World world, List<Text> tooltip, TooltipContext tooltipContext) {
        super.appendTooltip(itemStack, world, tooltip, tooltipContext);
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.bow.passive.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.bow.passive.desc",
                String.format(java.util.Locale.ROOT, "%.1f", AURA_RADIUS),
                (int) AURA_DAMAGE));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.bow.skill"));
        tooltip.add(Text.translatable("tooltip.wangsword.bow.skill.desc"));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.bow.ult"));
        tooltip.add(Text.translatable("tooltip.wangsword.bow.ult.desc",
                String.format(java.util.Locale.ROOT, "%.1f", ULT_CHARGE_TICKS / 20.0D),
                String.format(java.util.Locale.ROOT, "%.0f", SURGE_RADIUS),
                (int) SURGE_DAMAGE_CENTER,
                (int) SURGE_DAMAGE_EDGE,
                SURGE_COOLDOWN / 20));
        tooltip.add(Text.translatable("tooltip.wangsword.bow.ult.overload",
                (int) OVERLOAD_RADIUS,
                OVERLOAD_DURATION / 20,
                String.format(java.util.Locale.ROOT, "%.0f",
                        OVERLOAD_DAMAGE * (OVERLOAD_DURATION / OVERLOAD_INTERVAL))));
    }
}