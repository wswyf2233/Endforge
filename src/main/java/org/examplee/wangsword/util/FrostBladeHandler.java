package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.effect.FrozenEffect;
import org.examplee.wangsword.entity.FrostNovaEntity;
import org.examplee.wangsword.entity.FrostSpikeEntity;
import org.examplee.wangsword.registry.EntityRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 冰霜之刃的两个主动技能：右键霜环、X 键冰棱穿刺。
 *
 * <ol>
 *   <li><b>霜环</b>：以自身为圆心 7 格，范围内生物吃 2 点魔法伤害并被冰封 1.5 秒。
 *       视觉是脚下一圈向外扩张的冰环（{@link FrostNovaEntity} + 自绘环形贴图 + 环形冰壁），
 *       不是粒子堆——原版雪花粒子读不出"推出去"的方向感；粒子只在环的前沿跟着走，
 *       负责把"这一圈推到哪儿了"标出来；</li>
 *   <li><b>冰棱穿刺</b>：朝准心射出一根会飞的冰棱（{@link FrostSpikeEntity}），
 *       穿透路径上所有生物，每个吃 5 点伤害并被冰封 2 秒。这是冰霜之刃唯一的远程手段——
 *       它在前期真正的短板从来不是"跑得慢"，而是被骷髅、掠夺者在十几格外点名时只能站着挨打。</li>
 * </ol>
 *
 * <p>两个技能都只结算生物，不动方块。索敌走的是 {@link CombatTargets#isSkillTarget}——
 * 玩家自己按出来的技能按玩家选定的目标算，牛、羊这些中立/被动生物照样吃伤害；
 * 只有「自己会打人」的持续效果才需要限定敌对（那些走 {@code isHostile}）。
 *
 * <p>霜环的伤害取 {@code magic}：前期武器，霜环的作用是控场不是输出，
 * 走魔法伤害就不会顺带触发附魔、击退和火焰附加那一套。冰棱反过来走 {@code thrown}，
 * 它是实打实的弹体，吃护甲、能被盾挡，才符合"飞出去一根冰锥"的直觉。
 */
public final class FrostBladeHandler {

    /** 右键霜环冷却：9 秒 */
    public static final int NOVA_COOLDOWN = 180;
    /** X 键冰棱穿刺冷却：7 秒 */
    public static final int SPIKE_COOLDOWN = 140;

    /** 技能 ID：冷却按技能算，霜环和冰刺各占一槽，互不锁 */
    public static final String SKILL_NOVA = "frost_nova";
    public static final String SKILL_SPIKE = "frost_spike";

    /** 霜环半径（格） */
    public static final double NOVA_RADIUS = 7.0D;
    /** 霜环伤害（魔法伤害，无视护甲） */
    public static final float NOVA_DAMAGE = 2.0F;
    /** 霜环冰封时长：1.5 秒 */
    public static final int NOVA_FREEZE_TICKS = 30;

    /** 冰棱最大射程（格）：速度和存活 tick 都在实体里，这里只做展示用 */
    public static final double SPIKE_RANGE = FrostSpikeEntity.SPEED * FrostSpikeEntity.MAX_LIFE;
    /** 冰棱伤害 */
    public static final float SPIKE_DAMAGE = FrostSpikeEntity.DAMAGE;
    /** 冰棱冰封时长：2 秒 */
    public static final int SPIKE_FREEZE_TICKS = FrostSpikeEntity.FREEZE_TICKS;

    // 冷却改由 SkillCooldowns 统一管（按技能 ID 记），这里不再维护私有表：
    // 私有表的问题是客户端完全看不见，画不出冷却图标。

    /**
     * 正在向外推的霜环（只用于撒粒子）。
     *
     * <p>渲染那边靠 {@code age} 自己算扩散，不需要同步；但粒子必须在环的前沿上，
     * 服务端就得多记一份"什么时候放的、半径多大"，每 tick 沿当前圆周撒一圈。
     * 否则粒子只会糊在原地，和贴图各走各的。
     */
    private static final List<Pulse> PULSES = new ArrayList<>();
    /** 同时存在的霜环上限：防止连按把粒子预算吃光 */
    private static final int MAX_PULSES = 12;

    /** 一次正在扩散的霜环 */
    private static final class Pulse {
        final RegistryKey<World> world;
        final double x;
        final double y;
        final double z;
        final float radius;
        final long start;

        Pulse(RegistryKey<World> world, double x, double y, double z, float radius, long start) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.start = start;
        }
    }

    private FrostBladeHandler() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FrostBladeHandler::onServerTick);
    }

    // ================== 【右键 · 霜环】 ==================

    public static void castNova(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        double rangeSq = NOVA_RADIUS * NOVA_RADIUS;

        for (LivingEntity target : world.getEntitiesByClass(
                LivingEntity.class, player.getBoundingBox().expand(NOVA_RADIUS),
                CombatTargets::isSkillTarget)) {
            if (target.squaredDistanceTo(player) > rangeSq) {
                continue;
            }
            // 清掉受击无敌帧，否则刚被砍过一刀的怪会吞掉这 2 点
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().magic(), NOVA_DAMAGE);
            FrozenEffect.freezeTicks(target, NOVA_FREEZE_TICKS);
        }

        spawnNova(world, player.getX(), player.getY(), player.getZ(), (float) NOVA_RADIUS);
        addPulse(world, player.getX(), player.getY(), player.getZ(), (float) NOVA_RADIUS);

        // 起手三层霜屑：近、中、远各一圈，速度和数量递减，一上来就有"炸开"的层次感
        for (int ring = 0; ring < 3; ring++) {
            double base = 0.5D + ring * 0.75D;
            double speed = 0.55D - ring * 0.13D;
            int count = 34 - ring * 6;
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2.0D * i / count + ring * 0.21D;
                double cos = Math.cos(a);
                double sin = Math.sin(a);
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        player.getX() + cos * base,
                        player.getY() + 0.18D + ring * 0.10D,
                        player.getZ() + sin * base,
                        1, cos * speed, 0.06D, sin * speed, 0.02D);
            }
        }
        // 脚下的碎冰：横向撒开一片，读作"冰壳从脚下踩裂"
        world.spawnParticles(ParticleTypes.ITEM_SNOWBALL,
                player.getX(), player.getY() + 0.3D, player.getZ(),
                36, 2.2D, 0.16D, 2.2D, 0.14D);
        // 贴地白雾：把"从脚下起"这件事补实，不然起手只有空中一圈飞屑
        world.spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.12D, player.getZ(),
                24, 1.1D, 0.05D, 1.1D, 0.05D);

        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 1.3F, 1.45F);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE,
                SoundCategory.PLAYERS, 0.9F, 1.25F);
        // 低频垫底：只有玻璃碎裂的高频，听起来会"薄"得不像冲击波
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_POWDER_SNOW_BREAK,
                SoundCategory.PLAYERS, 1.0F, 0.85F);

        player.sendMessage(Text.translatable("message.wangsword.frost_blade.nova"), true);
    }

    // ================== 【X 键 · 冰棱穿刺】 ==================

    /** @return 是否真的射出去了（冷却中会返回 false） */
    public static boolean castSpike(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();

        if (!SkillCooldowns.isReady(player, SKILL_SPIKE)) {
            SkillCooldowns.notifyCooldown(player, SKILL_SPIKE, "冰棱穿刺");
            return false;
        }
        if (player.isDead() || player.isSpectator()) {
            return false;
        }

        Vec3d dir = player.getRotationVector().normalize();
        // 出膛点往前 0.9 格：冰棱本身有 1.75 格长，起点太靠眼睛会在第一人称里
        // 糊住准心一两帧。再压 0.15 格高度，让它从胸口偏上的位置射出去。
        Vec3d origin = player.getEyePos().add(dir.multiply(0.9D)).add(0.0D, -0.15D, 0.0D);

        FrostSpikeEntity spike = new FrostSpikeEntity(EntityRegistry.FROST_SPIKE, world);
        spike.setOwner(player);
        spike.setDirection(dir);
        spike.setPosition(origin.x, origin.y, origin.z);
        // 朝向必须和飞行方向一致，客户端渲染器靠 yaw/pitch 把十字平面转到弹道上
        spike.setYaw(player.getYaw());
        spike.setPitch(player.getPitch());
        world.spawnEntity(spike);

        // 出膛：一簇碎冰往前喷，读作"冰棱被推出去"。
        // 量比原来翻了一倍——冰棱本身快、细，出手那一瞬如果不炸开，玩家会以为技能没放出来。
        world.spawnParticles(ParticleTypes.SNOWFLAKE,
                origin.x, origin.y, origin.z, 26, 0.16D, 0.16D, 0.16D, 0.14D);
        world.spawnParticles(ParticleTypes.ITEM_SNOWBALL,
                origin.x, origin.y, origin.z, 14, 0.18D, 0.18D, 0.18D, 0.12D);
        world.spawnParticles(ParticleTypes.CLOUD,
                origin.x, origin.y, origin.z, 8, 0.10D, 0.10D, 0.10D, 0.06D);

        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_THROW,
                SoundCategory.PLAYERS, 0.85F, 1.55F);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 0.45F, 1.95F);

        SkillCooldowns.start(player, SKILL_SPIKE, SPIKE_COOLDOWN);
        player.sendMessage(Text.translatable("message.wangsword.frost_blade.spike"), true);
        return true;
    }

    // ================== 【每 tick：霜环粒子前沿】 ==================

    private static void onServerTick(MinecraftServer server) {
        tickPulses(server);
    }

    /** 记下一圈正在扩散的霜环，粒子靠它跟着前沿走 */
    private static void addPulse(ServerWorld world, double x, double y, double z, float radius) {
        if (PULSES.size() >= MAX_PULSES) {
            PULSES.remove(0);
        }
        PULSES.add(new Pulse(world.getRegistryKey(), x, y, z, radius, world.getTime()));
    }

    /**
     * 每 tick 沿霜环当前半径撒一圈粒子。
     *
     * <p>扩散曲线必须和 {@code FrostNovaRenderer} 里的完全一致（同一个 {@code t^0.7}），
     * 否则粒子会跑到贴图环的内侧或外侧，看着像两个不同的东西。
     */
    private static void tickPulses(MinecraftServer server) {
        if (PULSES.isEmpty()) {
            return;
        }
        Iterator<Pulse> it = PULSES.iterator();
        while (it.hasNext()) {
            Pulse pulse = it.next();
            ServerWorld world = server.getWorld(pulse.world);
            if (world == null) {
                it.remove();
                continue;
            }
            long age = world.getTime() - pulse.start;
            if (age >= FrostNovaEntity.LIFE) {
                it.remove();
                continue;
            }
            // 每 2 tick 撒一次：每 tick 撒会糊成一片，看不出"在推"
            if (age % 2 != 0) {
                continue;
            }

            float p = age / (float) FrostNovaEntity.LIFE;
            // 曲线必须和渲染器一致：0.6 的进度推到最外圈，其余时间原地淡出
            float t = MathHelper.clamp(p / 0.6F, 0.0F, 1.0F);
            float ease = (float) Math.pow(t, 0.7D);
            double r = pulse.radius * (0.08D + 0.92D * ease);

            int count = MathHelper.clamp((int) Math.round(r * 4.0D), 6, 30);
            for (int i = 0; i < count; i++) {
                // 每撒一次错开一点角度，避免留下规则的"针轮"痕迹
                double a = Math.PI * 2.0D * (i + (age % 4) * 0.25D) / count;
                double cos = Math.cos(a);
                double sin = Math.sin(a);
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        pulse.x + cos * r, pulse.y + 0.22D, pulse.z + sin * r,
                        1, cos * 0.06D, 0.10D, sin * 0.06D, 0.01D);
            }

            // 每 4 tick 在环上补几块碎冰，让冰壁的边缘有"剥落"的颗粒感
            if (age % 4 == 0) {
                int shards = MathHelper.clamp((int) Math.round(r * 1.5D), 3, 12);
                for (int i = 0; i < shards; i++) {
                    double a = Math.PI * 2.0D * (i + 0.5D) / shards;
                    world.spawnParticles(ParticleTypes.ITEM_SNOWBALL,
                            pulse.x + Math.cos(a) * r, pulse.y + 0.38D,
                            pulse.z + Math.sin(a) * r,
                            1, 0.05D, 0.12D, 0.05D, 0.03D);
                }
            }
        }
    }

    private static void spawnNova(ServerWorld world, double x, double y, double z, float radius) {
        FrostNovaEntity visual = new FrostNovaEntity(EntityRegistry.FROST_NOVA, world);
        // 贴地画：抬高 3 厘米，和渲染器里的 0.03 对齐
        visual.setPosition(x, y + 0.03D, z);
        visual.setRadius(radius);
        world.spawnEntity(visual);
    }
}
