package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.examplee.wangsword.item.unique.FlameBladeItem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 烈火大刀的两个主动技能：右键通天火柱、X 键持续喷火。
 *
 * <p>两个技能都会持续一小段时间，所以没法像即时技能那样当场算完，
 * 统一挂在这张表里由服务端每 tick 推进：
 * <ul>
 *   <li><b>通天火柱</b>：准心落点竖起 30 格高的火柱，存在 5 秒，
 *       每 0.75 秒对柱身半径 2.2 格内的生物造成 9 点魔法伤害并点燃。
 *       柱体不是粒子堆——生成一个 {@code FlamePillarEntity}，由客户端
 *       {@code FlamePillarRenderer} 用自绘贴图铺成三层圆柱，这里只补贴火星；</li>
 *   <li><b>烈火喷射</b>：视线方向喷 3 秒火焰，每 0.25 秒对沿途 1.4 格内的生物
 *       造成 6 点伤害并点燃，射程 12 格。火舌不是粒子堆——和誓约胜利之剑的风王结界
 *       同一套做法，每 2 tick 在眼前重生一段横放的火锥实体（{@code FlameJetEntity}），
 *       由 {@code FlameJetRenderer} 画成贴图往末端滚的锥体，这里再补火环和火星。</li>
 * </ul>
 *
 * <p>只结算生物，不动方块——火柱不铺火、喷射不烧地，否则这两招在树林和村庄里
 * 等于一键清场，服务器也扛不住。玩家（含其他玩家）不吃伤害，避免误伤队友。
 */
public final class FlameBladeHandler {

    /** 右键火柱冷却：10 秒 */
    public static final int PILLAR_COOLDOWN = 200;
    /** X 键喷射冷却：25 秒 */
    public static final int JET_COOLDOWN = 500;
    /** 喷射持续：3 秒 */
    public static final int JET_DURATION = 60;

    /** 技能 ID：冷却按技能算，火柱和喷射各占一槽，互不锁 */
    public static final String SKILL_PILLAR = "flame_pillar";
    public static final String SKILL_JET = "flame_jet";

    /** 火柱半径（格）。取实体常量，和渲染器共用同一个数，改一处两边一起变 */
    private static final double PILLAR_RADIUS = org.examplee.wangsword.entity.FlamePillarEntity.RADIUS;
    /** 火柱高度（格） */
    private static final double PILLAR_HEIGHT = 30.0D;
    /** 火柱存在 tick：5 秒 */
    private static final int PILLAR_LIFE = 100;
    /** 火柱的最远投放距离（格） */
    private static final double PILLAR_RANGE = 32.0D;
    /** 火柱伤害脉冲间隔：0.75 秒 */
    public static final int PILLAR_PULSE = 15;
    /** 火柱单次脉冲伤害 */
    public static final float PILLAR_DAMAGE = 9.0F;

    // ================== 【右键 · 烈焰十字】 ==================
    /** 单道十字剑气的伤害（两道分别结算） */
    public static final float CROSS_DAMAGE = 42.0F;
    /** 飞行速度（格/tick） */
    private static final float CROSS_SPEED = 1.15F;
    /** 寿命（tick） */
    private static final int CROSS_LIFE = 26;
    /** 视觉放大 */
    private static final float CROSS_SCALE = 1.5F;

    /** 喷射射程（格） */
    private static final double JET_RANGE = 12.0D;
    /** 喷射判定半径（格） */
    private static final double JET_RADIUS = 1.4D;
    /** 喷射伤害脉冲间隔：0.25 秒 */
    public static final int JET_PULSE = 5;
    /** 喷吐单次伤害 */
    public static final float JET_DAMAGE = 6.0F;

    private static final Map<UUID, Pillar> PILLARS = new HashMap<>();
    private static final Map<UUID, Jet> JETS = new HashMap<>();

    /** 一根正在燃烧的火柱 */
    private static final class Pillar {
        final RegistryKey<World> world;
        final Vec3d base;
        final long endTick;
        long nextPulse;

        Pillar(RegistryKey<World> world, Vec3d base, long endTick, long nextPulse) {
            this.world = world;
            this.base = base;
            this.endTick = endTick;
            this.nextPulse = nextPulse;
        }
    }

    /** 一次正在进行的喷射 */
    private static final class Jet {
        final RegistryKey<World> world;
        final long endTick;
        long nextPulse;
        int soundTimer;

        Jet(RegistryKey<World> world, long endTick, long nextPulse) {
            this.world = world;
            this.endTick = endTick;
            this.nextPulse = nextPulse;
        }
    }

    private FlameBladeHandler() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FlameBladeHandler::onServerTick);
        // 掉线就把自己的火柱和喷射清掉，免得重连后还有一根看不见的柱子在烧人
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            PILLARS.remove(id);
            JETS.remove(id);
            AURA_RINGS.remove(id);
        });
    }

    // ================== 【被动 · 焚身领域的脚环】 ==================

    /** 一个玩家一圈，火刀拿在主手时存在 */
    private static final java.util.Map<UUID, org.examplee.wangsword.entity.ThunderRingEntity> AURA_RINGS =
            new java.util.HashMap<>();

    /**
     * 维持被动火环。
     *
     * <p>这个环是<b>常驻</b>的：只要火刀拿在主手就一直在，换手、收刀、死亡时掐掉。
     * 所以不能像万雷那样每次重新 spawn——那会每 tick 叠一个实体。
     *
     * @param active 当前是否应该显示（主手拿着这把刀且没死）
     * @param radius 领域半径
     */
    public static void tickAuraRing(ServerPlayerEntity player, boolean active, double radius) {
        UUID id = player.getUuid();
        org.examplee.wangsword.entity.ThunderRingEntity ring = AURA_RINGS.get(id);

        if (!active || player.isDead()) {
            if (ring != null) {
                if (!ring.isRemoved()) {
                    ring.discard();
                }
                AURA_RINGS.remove(id);
            }
            return;
        }
        // 已经有一圈在跑：位置由实体自己跟主人，这里什么都不用做
        if (ring != null && !ring.isRemoved()) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        ring = new org.examplee.wangsword.entity.ThunderRingEntity(
                org.examplee.wangsword.registry.EntityRegistry.THUNDER_RING, world);
        ring.setStyle(org.examplee.wangsword.entity.ThunderRingEntity.STYLE_FIRE);
        ring.setRadius((float) radius);
        ring.setOwner(player);
        // 常驻环：寿命给到极大，什么时候消失只由上面的 active 判断决定
        ring.setLifeTicks(Integer.MAX_VALUE / 2);
        ring.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        world.spawnEntity(ring);
        AURA_RINGS.put(id, ring);
    }

    // ================== 【右键 · 通天火柱】 ==================

    /**
     * 右键 · 烈焰十字：朝准心同时甩出两道交叉的火焰剑气。
     *
     * <p>每道独立飞行、独立结算，命中生物点燃 4 秒，掠过方块表面也会点火，但不破坏地形。
     */
    public static void castCrossSlash(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVector().normalize();
        Vec3d up = dir.crossProduct(dir.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D)));
        if (up.lengthSquared() < 1.0E-6D) {
            up = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            up = up.normalize();
        }

        for (int i = 0; i < 2; i++) {
            org.examplee.wangsword.entity.ArcSlashEntity slash =
                    new org.examplee.wangsword.entity.ArcSlashEntity(
                            org.examplee.wangsword.registry.EntityRegistry.ARC_SLASH_GOLD,
                            player, world, CROSS_DAMAGE, dir, up);
            slash.setGolden(true);
            slash.setFlame(true);
            slash.setBreakBlocks(false);
            slash.setMeleeExclusion(0.6D);
            slash.setMoveSpeed(CROSS_SPEED);
            slash.setMaxLife(CROSS_LIFE);
            slash.setScaleMult(CROSS_SCALE);
            slash.setRollDegrees(i * 90.0F);
            slash.setPosition(eye.add(dir.multiply(0.6D)));
            world.spawnEntity(slash);
        }

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.3F, 0.75F);
        player.sendMessage(Text.translatable("message.wangsword.flame_blade.pillar"), true);
    }

    public static void castPillar(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        long now = world.getTime();

        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector().normalize();
        Vec3d reach = eye.add(look.multiply(PILLAR_RANGE));

        // 方块射线和生物射线各打一次。
        // 只做方块射线的话，准心对着怪时射线会直接"穿"过它打在身后的地面上，
        // 火柱就长在怪背后了 —— 玩家瞄准的目标和落点对不上。
        BlockHitResult hit = world.raycast(new RaycastContext(
                eye, reach,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        LivingEntity target = findPillarTarget(player, eye, look, reach);

        Vec3d base;
        if (target != null && (hit.getType() != HitResult.Type.BLOCK
                || eye.squaredDistanceTo(target.getPos()) <= eye.squaredDistanceTo(hit.getPos()))) {
            // 锁到生物：柱底落在它脚下，目标正好站在柱心
            base = new Vec3d(target.getX(), target.getY(), target.getZ());
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            // 落点抬 5 厘米，柱底不会嵌进方块顶面
            base = hit.getPos().add(0.0D, 0.05D, 0.0D);
        } else {
            // 对着天空放：柱子在视线方向 16 格处、脚下同高度竖起，不至于悬在天上
            base = new Vec3d(eye.x + look.x * 16.0D, player.getY(), eye.z + look.z * 16.0D);
        }

        PILLARS.put(player.getUuid(), new Pillar(world.getRegistryKey(), base, now + PILLAR_LIFE, now));

        // 柱体本身交给 FlamePillarEntity + 自绘贴图渲染，这里只补火星和起手噪音
        org.examplee.wangsword.entity.FlamePillarEntity visual =
                new org.examplee.wangsword.entity.FlamePillarEntity(
                        org.examplee.wangsword.registry.EntityRegistry.FLAME_PILLAR, world);
        visual.setPosition(base.x, base.y, base.z);
        world.spawnEntity(visual);

        // 起手：柱底炸一圈火环 + 一声闷响，让"柱子立起来"这件事有重量
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48.0D;
            world.spawnParticles(ParticleTypes.FLAME,
                    base.x + Math.cos(a) * (PILLAR_RADIUS + 0.6D), base.y + 0.1D,
                    base.z + Math.sin(a) * (PILLAR_RADIUS + 0.6D),
                    2, 0.05D, 0.08D, 0.05D, 0.03D);
        }
        world.spawnParticles(ParticleTypes.LAVA, base.x, base.y + 0.3D, base.z,
                40, 1.2D, 0.2D, 1.2D, 0.35D);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, base.x, base.y + 1.0D, base.z,
                24, 1.0D, 0.4D, 1.0D, 0.06D);
        world.playSound(null, base.x, base.y, base.z, SoundEvents.ITEM_FIRECHARGE_USE,
                SoundCategory.PLAYERS, 1.5F, 0.6F);
        world.playSound(null, base.x, base.y, base.z, SoundEvents.ENTITY_BLAZE_SHOOT,
                SoundCategory.PLAYERS, 1.2F, 0.65F);

        player.sendMessage(Text.translatable("message.wangsword.flame_blade.pillar"), true);
    }

    /**
     * 准心射线找一个生物作为落点（判定盒放宽 1 格，贴脸和擦边都算中）。
     *
     * <p>只返回"射线打到的第一个活物"，不在这里判断谁更近——由调用方和方块射线的距离
     * 一起比较。这样隔着墙放技能时，火柱会落在墙上，而不是穿墙砸到墙后的怪。
     */
    private static LivingEntity findPillarTarget(ServerPlayerEntity player,
                                                 Vec3d eye, Vec3d dir, Vec3d end) {
        EntityHitResult hit = ProjectileUtil.raycast(
                player, eye, end,
                player.getBoundingBox().stretch(dir.multiply(PILLAR_RANGE)).expand(1.0D),
                e -> e instanceof LivingEntity living && CombatTargets.isSkillTarget(living),
                PILLAR_RANGE * PILLAR_RANGE);
        return hit == null ? null : (LivingEntity) hit.getEntity();
    }

    // ================== 【X 键 · 烈火喷射】 ==================

    /** @return 是否真的开始了喷射（冷却中会返回 false） */
    public static boolean castJet(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        long now = world.getTime();

        // 冷却走通用技能池：按技能记，不碰物品冷却，右键火柱完全不受影响
        if (!SkillCooldowns.isReady(player, SKILL_JET)) {
            SkillCooldowns.notifyCooldown(player, SKILL_JET, "烈火喷射");
            return false;
        }
        if (JETS.containsKey(player.getUuid())) {
            return false;
        }

        JETS.put(player.getUuid(), new Jet(world.getRegistryKey(), now + JET_DURATION, now + JET_PULSE));
        SkillCooldowns.start(player, SKILL_JET, JET_COOLDOWN);

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT,
                SoundCategory.PLAYERS, 1.1F, 0.9F);
        player.sendMessage(Text.translatable("message.wangsword.flame_blade.jet"), true);
        return true;
    }

    // ================== 【每 tick 推进】 ==================

    private static void onServerTick(MinecraftServer server) {
        if (!PILLARS.isEmpty()) {
            Iterator<Map.Entry<UUID, Pillar>> it = PILLARS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Pillar> entry = it.next();
                Pillar pillar = entry.getValue();
                ServerWorld world = server.getWorld(pillar.world);
                if (world == null) {
                    it.remove();
                    continue;
                }
                // 时间一律取世界的 getTime()，和 cast 时用的是同一个钟
                long now = world.getTime();
                if (now >= pillar.endTick) {
                    it.remove();
                    continue;
                }
                tickPillar(world, pillar, now);
            }
        }
        if (!JETS.isEmpty()) {
            Iterator<Map.Entry<UUID, Jet>> it = JETS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Jet> entry = it.next();
                UUID id = entry.getKey();
                Jet jet = entry.getValue();
                ServerWorld world = server.getWorld(jet.world);
                // getPlayerByUuid 返回的是 PlayerEntity，服务端这边实际一定是 ServerPlayerEntity
                PlayerEntity found = world == null ? null : world.getPlayerByUuid(id);
                ServerPlayerEntity player = found instanceof ServerPlayerEntity sp ? sp : null;
                // 死了、切手了、掉线了，喷射立刻断
                if (world == null || player == null || player.isDead() || player.isSpectator()
                        || !(player.getMainHandStack().getItem() instanceof FlameBladeItem)) {
                    it.remove();
                    continue;
                }
                long now = world.getTime();
                if (now >= jet.endTick) {
                    it.remove();
                    continue;
                }
                tickJet(world, player, jet, now);
            }
        }
    }

    private static void tickPillar(ServerWorld world, Pillar pillar, long now) {
        Vec3d base = pillar.base;

        // 柱身交给渲染器画圆柱了，这里只撒少量火星：往上飘的余烬 + 偶尔爆开的熔岩点
        for (int i = 0; i < 4; i++) {
            double a = world.random.nextDouble() * Math.PI * 2.0D;
            double r = PILLAR_RADIUS * (0.35D + world.random.nextDouble() * 0.85D);
            double y = world.random.nextDouble() * PILLAR_HEIGHT;
            world.spawnParticles(ParticleTypes.FLAME,
                    base.x + Math.cos(a) * r, base.y + y, base.z + Math.sin(a) * r,
                    1, 0.05D, 0.18D, 0.05D, 0.035D);
        }
        // 每 4 tick 一颗从柱底往上窜的大火星
        if (now % 4L == 0L) {
            double a = world.random.nextDouble() * Math.PI * 2.0D;
            double r = PILLAR_RADIUS * 0.5D;
            world.spawnParticles(ParticleTypes.LAVA,
                    base.x + Math.cos(a) * r, base.y + 0.4D, base.z + Math.sin(a) * r,
                    1, 0.1D, 0.3D, 0.1D, 1.6D);
        }

        if (now >= pillar.nextPulse) {
            pillar.nextPulse = now + PILLAR_PULSE;
            damagePillar(world, pillar);
        }
    }

    private static void damagePillar(ServerWorld world, Pillar pillar) {
        Vec3d base = pillar.base;
        Box box = new Box(base.x - PILLAR_RADIUS, base.y, base.z - PILLAR_RADIUS,
                base.x + PILLAR_RADIUS, base.y + PILLAR_HEIGHT, base.z + PILLAR_RADIUS);

        for (Entity entity : world.getOtherEntities(null, box, e -> e instanceof LivingEntity)) {
            LivingEntity target = (LivingEntity) entity;
            if (target instanceof PlayerEntity || !target.isAlive()) {
                continue;
            }
            double dx = target.getX() - base.x;
            double dz = target.getZ() - base.z;
            if (dx * dx + dz * dz > PILLAR_RADIUS * PILLAR_RADIUS) {
                continue;
            }
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().magic(), PILLAR_DAMAGE);
            target.setOnFireFor(8);
            // 往上顶一下，让被柱心扫到的怪有"被火柱托起来"的感觉
            target.setVelocity(target.getVelocity().add(0.0D, 0.32D, 0.0D));
            target.velocityModified = true;
        }
        world.spawnParticles(ParticleTypes.FLAME, base.x, base.y + 0.6D, base.z,
                20, PILLAR_RADIUS * 0.7D, 0.4D, PILLAR_RADIUS * 0.7D, 0.12D);
        world.playSound(null, base.x, base.y, base.z, SoundEvents.ENTITY_BLAZE_BURN,
                SoundCategory.PLAYERS, 1.0F, 0.55F);
    }

    private static void tickJet(ServerWorld world, ServerPlayerEntity player, Jet jet, long now) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVector().normalize();
        Vec3d end = eye.add(dir.multiply(JET_RANGE));

        // 火舌本体：和誓约胜利之剑的风筒一样，用横放的网格实体，每 2 tick 重生一根，
        // 旧的几 tick 后自己收掉——这样转头时火锥是跟着准星走的，不是留在原地
        if (now % 2L == 0L) {
            spawnFlameJet(world, eye, dir);
        }

        // 沿火舌铺一圈圈火环：风王结界靠这个读作"压力",火就靠它读作"热浪"
        Vec3d right = dir.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3d up = right.crossProduct(dir).normalize();
        for (double step = 1.2D; step <= JET_RANGE; step += 2.0D) {
            Vec3d center = eye.add(dir.multiply(step));
            double ringRadius = 0.28D + step * 0.10D;
            double spin = step * 1.4D + now * 0.35D;
            for (int i = 0; i < 14; i++) {
                double ang = Math.PI * 2.0D * i / 14.0D + spin;
                Vec3d point = center
                        .add(right.multiply(Math.cos(ang) * ringRadius))
                        .add(up.multiply(Math.sin(ang) * ringRadius));
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (i % 2 == 0) {
                    world.spawnParticles(ParticleTypes.FLAME,
                            point.x, point.y, point.z, 1, 0.03D, 0.03D, 0.03D, 0.01D);
                }
            }
        }

        // 枪口的爆燃：每 tick 一小撮从手里喷出去的火，接住火锥的起点
        Vec3d muzzle = eye.add(dir.multiply(1.1D));
        world.spawnParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z,
                3, 0.14D, 0.14D, 0.14D, 0.08D);
        world.spawnParticles(ParticleTypes.LAVA, end.x, end.y, end.z,
                4, 0.4D, 0.4D, 0.4D, 0.05D);

        if (now >= jet.nextPulse) {
            jet.nextPulse = now + JET_PULSE;
            damageJet(world, player, eye, end, dir);
        }
        if (--jet.soundTimer <= 0) {
            jet.soundTimer = 10;
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_AMBIENT,
                    SoundCategory.PLAYERS, 0.8F, 1.4F);
        }
    }

    /** 生成一段横放在视线上的火锥（纯视觉；伤害仍由 damageJet 结算） */
    private static void spawnFlameJet(ServerWorld world, Vec3d eye, Vec3d dir) {
        org.examplee.wangsword.entity.FlameJetEntity visual =
                new org.examplee.wangsword.entity.FlameJetEntity(
                        org.examplee.wangsword.registry.EntityRegistry.FLAME_JET, world);
        // 放在眼前一格：渲染器的火锥从这个点往视线方向铺 11.5 格
        visual.setPosition(eye.x + dir.x * 0.9D, eye.y + dir.y * 0.9D, eye.z + dir.z * 0.9D);
        visual.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        visual.setPitch((float) -Math.toDegrees(Math.asin(dir.y)));
        world.spawnEntity(visual);
    }

    private static void damageJet(ServerWorld world, ServerPlayerEntity player, Vec3d from,
                                  Vec3d to, Vec3d dir) {
        Box box = new Box(from, to).expand(JET_RADIUS + 0.6D);
        for (Entity entity : world.getOtherEntities(player, box, e -> e instanceof LivingEntity)) {
            LivingEntity target = (LivingEntity) entity;
            if (!target.isAlive() || target instanceof PlayerEntity) {
                continue;
            }
            if (distanceToSegment(target.getX(), target.getY() + target.getHeight() * 0.5D,
                    target.getZ(), from, to) > JET_RADIUS) {
                continue;
            }
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().playerAttack(player), JET_DAMAGE);
            target.setOnFireFor(6);
            target.setVelocity(target.getVelocity().add(dir.x * 0.12D, 0.05D, dir.z * 0.12D));
            target.velocityModified = true;
        }
    }

    /** 点到线段的最短距离，用来把整条火舌当成一根粗管 */
    private static double distanceToSegment(double px, double py, double pz, Vec3d a, Vec3d b) {
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double abz = b.z - a.z;
        double len2 = abx * abx + aby * aby + abz * abz;
        double t = len2 < 1.0E-9D ? 0.0D
                : (((px - a.x) * abx + (py - a.y) * aby + (pz - a.z) * abz) / len2);
        t = MathHelper.clamp(t, 0.0D, 1.0D);
        double dx = px - (a.x + abx * t);
        double dy = py - (a.y + aby * t);
        double dz = pz - (a.z + abz * t);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
