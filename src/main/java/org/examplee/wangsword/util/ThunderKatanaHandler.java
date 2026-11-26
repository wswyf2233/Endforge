package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.ThunderBoltEntity;
import org.examplee.wangsword.entity.ThunderRingEntity;
import org.examplee.wangsword.registry.EntityRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 雷电太刀的两件套：被动雷纹、右键万雷、X 键雷怒。
 *
 * <ol>
 *   <li><b>被动 · 雷纹</b>：每次命中叠一层，最多 5 层、6 秒不掉，每层 +4% 攻速；
 *       叠满之后<em>下一刀</em>自动落一道小雷，追加 5 点雷伤并清空层数。</li>
 *   <li><b>右键 · 万雷</b>：准心 18 格内定一片 7 格半径的雷雨，1.8 秒内落满 18 道落雷，
 *       前 4 道锁在准心（保证单体打得死）、其余随机散布；每道对 2.5 格内造成 7 点雷伤并麻痹 1 秒。冷却 12 秒。</li>
 *   <li><b>X 键 · 雷怒</b>：15 秒内攻击力 +5、攻速 +30%，期间<em>每一刀</em>都往目标身上引一道落雷
 *       （7 点雷伤 + 2 格内 3 点溅射）；脚下同时张开一圈 3 格雷环，跟着人走满 15 秒。冷却 40 秒。</li>
 * </ol>
 *
 * <p>「素雷」与「真雷」：原版 {@link LightningEntity} 会点燃落点、充能苦力怕、把村民变女巫。
 * 万雷一次就是 18 道、雷怒 15 秒可能几十道——全部用 {@code setCosmetic(true)} 的素雷，
 * 只出画面和音效，伤害全部走本类自己结算，不然自家基地会被劈成火海。
 */
public final class ThunderKatanaHandler {

    // ================== 【被动 · 雷纹】 ==================

    /** 最多 5 层 */
    public static final int MAX_STACKS = 5;
    /** 6 秒不命中就掉光 */
    public static final int STACK_DECAY = 120;
    /** 每层 +4% 攻速 */
    public static final double ATTACK_SPEED_PER_STACK = 0.04D;
    /** 满层小雷：主目标伤害 */
    public static final float SMALL_BOLT_DAMAGE = 5.0F;
    /** 满层小雷：溅射伤害 */
    public static final float SMALL_BOLT_SPLASH = 2.0F;
    /** 满层小雷：溅射半径 */
    public static final double SMALL_BOLT_RADIUS = 2.0D;

    // ================== 【右键 · 万雷】 ==================

    /** 万雷在通用冷却池里的技能 ID（客户端图标也用这个） */
    public static final String SKILL_VOLLEY = "katana_volley";

    /** 万雷冷却：12 秒 */
    public static final int VOLLEY_COOLDOWN = 240;
    /** 引雷距离（格） */
    public static final double VOLLEY_RANGE = 18.0D;
    /** 雷雨半径（格） */
    public static final double VOLLEY_SPREAD = 7.0D;
    /** 一共落几道 */
    public static final int VOLLEY_COUNT = 18;
    /**
     * 前几道锁死在准心落点。
     *
     * <p>为什么需要它：其余道次在 {@link #VOLLEY_SPREAD}（7 格）半径内均匀撒，
     * 而单道只判定 {@link #VOLLEY_RADIUS}（2.5 格），命中率只有 (2.5/7)² ≈ 13%。
     * 只有第 1 道锁准心时，一只站着不动的僵尸期望只吃 3 道、约 18 点，
     * 刚好差 2 点打不死——玩家体感就是「这么大一片雷，连僵尸都杀不掉」。
     * 锁 4 道之后单体保底 4 × 单道伤害，稳定收掉 20 血的普通怪，面杀伤手感不变。
     */
    public static final int VOLLEY_LOCKED = 4;
    /** 锁定道的抖动半径（格）：四道完全重合看不出层次，给一点点散开 */
    public static final double VOLLEY_LOCK_JITTER = 0.7D;
    /** 两道之间隔几 tick（18 道 × 2 = 1.8 秒） */
    public static final int VOLLEY_STEP = 2;
    /** 单道伤害 */
    public static final float VOLLEY_DAMAGE = 7.0F;
    /** 单道伤害半径 */
    public static final double VOLLEY_RADIUS = 2.5D;
    /** 单道麻痹时长：1 秒 */
    public static final int VOLLEY_PARALYZE_TICKS = 20;

    // ================== 【X 键 · 雷怒】 ==================

    /** 雷怒在通用冷却池里的技能 ID */
    public static final String SKILL_WRATH = "katana_wrath";

    /** 雷怒冷却：40 秒 */
    public static final int WRATH_COOLDOWN = 800;
    /** 持续 15 秒 */
    public static final int WRATH_DURATION = 300;
    /** 期间攻击力 +5 */
    public static final double WRATH_ATTACK_BONUS = 5.0D;
    /** 期间攻速 +30% */
    public static final double WRATH_SPEED_BONUS = 0.30D;
    /** 每刀引来的雷：主目标伤害 */
    public static final float WRATH_BOLT_DAMAGE = 7.0F;
    /** 每刀引来的雷：溅射伤害 */
    public static final float WRATH_BOLT_SPLASH = 3.0F;
    /** 每刀引来的雷：溅射半径 */
    public static final double WRATH_BOLT_RADIUS = 2.0D;
    /** 每刀引来的雷：麻痹时长 */
    public static final int WRATH_PARALYZE_TICKS = 20;
    /** 雷怒脚下雷环的半径（格） */
    public static final float RING_RADIUS = 3.0F;

    /** 雷纹攻速修改器 */
    private static final UUID SPEED_ID = UUID.fromString("3c7d1a52-8b64-4f19-9a0e-5d2c6b7f8a91");
    /** 雷怒：攻击力 +5 */
    private static final UUID WRATH_ATTACK_ID = UUID.fromString("a41f7b09-62d3-4c85-8e10-7b3d9f2c5a48");
    /** 雷怒：攻速 +30% */
    private static final UUID WRATH_SPEED_ID = UUID.fromString("5d8e2c74-9f31-4b06-a7c2-1e6f4d9b3a05");

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private static final Map<UUID, Long> WRATH_END = new HashMap<>();
    /** 每个人的脚下雷环（雷怒期间存在，结束/死亡/下线一并销毁） */
    private static final Map<UUID, ThunderRingEntity> WRATH_RINGS = new HashMap<>();
    private static final List<Volley> VOLLEYS = new ArrayList<>();

    /** 一个人的雷纹状态 */
    private static final class Charge {
        int stacks;
        long lastHitTick;
    }

    /** 一片还在落雷的雷雨 */
    private static final class Volley {
        final RegistryKey<World> world;
        final List<Vec3d> points;
        final long start;
        int next;

        Volley(RegistryKey<World> world, List<Vec3d> points, long start) {
            this.world = world;
            this.points = points;
            this.start = start;
        }
    }

    private ThunderKatanaHandler() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ThunderKatanaHandler::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            clearWrath(handler.getPlayer());
            removeRing(id);
            CHARGES.remove(id);
            WRATH_END.remove(id);
        });
    }

    // ================== 【被动 · 雷纹】 ==================

    /**
     * 命中结算：叠层，叠满那一刀落小雷；雷怒期间这一刀再额外引一道。
     *
     * <p>顺序是「先看满没满，再叠」——否则打满 5 层的下一刀会先变成 6 层，
     * 玩家永远等不到那道雷。
     */
    public static void onHit(ServerPlayerEntity player, LivingEntity target) {
        ServerWorld world = (ServerWorld) player.getWorld();
        long now = world.getTime();
        Charge charge = CHARGES.computeIfAbsent(player.getUuid(), id -> new Charge());

        if (now - charge.lastHitTick > STACK_DECAY) {
            charge.stacks = 0;
        }
        charge.lastHitTick = now;

        if (charge.stacks >= MAX_STACKS) {
            charge.stacks = 0;
            applySpeed(player, 0);
            strikeSmall(world, target);
        } else {
            charge.stacks++;
            applySpeed(player, charge.stacks);
            if (charge.stacks >= MAX_STACKS) {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                        SoundCategory.PLAYERS, 0.7F, 1.7F);
                player.sendMessage(Text.translatable("message.wangsword.thunder_katana.charge_full"), true);
            }
        }

        // 雷怒：每一刀都往目标身上引一道雷
        if (isWrathActive(player, now)) {
            strikeWrath(world, target);
        }

        // 刀身上的电弧：每层两颗粒，越满越亮
        Vec3d hand = player.getEyePos().add(player.getRotationVec(1.0F).multiply(1.1D));
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                hand.x, hand.y - 0.25D, hand.z, 1 + charge.stacks, 0.16D, 0.30D, 0.16D, 0.02D);
    }

    /** 满层小雷：素雷（只出画面），伤害自己算，免得每 6 刀烧一片地 */
    private static void strikeSmall(ServerWorld world, LivingEntity target) {
        Vec3d at = new Vec3d(target.getX(), target.getY(), target.getZ());
        spawnBolt(world, at);

        target.timeUntilRegen = 0;
        target.damage(world.getDamageSources().lightningBolt(), SMALL_BOLT_DAMAGE);

        for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class,
                target.getBoundingBox().expand(SMALL_BOLT_RADIUS), CombatTargets::isSkillTarget)) {
            if (other == target || other.squaredDistanceTo(target) > SMALL_BOLT_RADIUS * SMALL_BOLT_RADIUS) {
                continue;
            }
            other.timeUntilRegen = 0;
            other.damage(world.getDamageSources().lightningBolt(), SMALL_BOLT_SPLASH);
        }

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                at.x, at.y + 1.0D, at.z, 18, 0.35D, 0.55D, 0.35D, 0.10D);
        world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                SoundCategory.PLAYERS, 0.55F, 1.35F);
    }

    /** 雷怒期间每一刀附带的雷：主目标满伤 + 2 格内溅射 */
    private static void strikeWrath(ServerWorld world, LivingEntity target) {
        Vec3d at = new Vec3d(target.getX(), target.getY(), target.getZ());
        spawnBolt(world, at);

        target.timeUntilRegen = 0;
        target.damage(world.getDamageSources().lightningBolt(), WRATH_BOLT_DAMAGE);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, WRATH_PARALYZE_TICKS, 1));

        for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class,
                target.getBoundingBox().expand(WRATH_BOLT_RADIUS), CombatTargets::isSkillTarget)) {
            if (other == target || other.squaredDistanceTo(target) > WRATH_BOLT_RADIUS * WRATH_BOLT_RADIUS) {
                continue;
            }
            other.timeUntilRegen = 0;
            other.damage(world.getDamageSources().lightningBolt(), WRATH_BOLT_SPLASH);
        }

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                at.x, at.y + 1.0D, at.z, 14, 0.30D, 0.50D, 0.30D, 0.10D);
        world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                SoundCategory.PLAYERS, 0.45F, 1.45F);
    }

    // ================== 【右键 · 万雷】 ==================

    /** @return 是否真的放出去了（冷却由物品那边的 ItemCooldownManager 管） */
    public static boolean castVolley(ServerPlayerEntity player) {
        if (player.isDead() || player.isSpectator()) {
            return false;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        long now = world.getTime();
        Vec3d center = resolveImpact(player, world, VOLLEY_RANGE);

        // 前 VOLLEY_LOCKED 道锁准心（保底单体伤害），其余按 sqrt 随机铺开
        // （不用 sqrt 会全挤在圆心，面上就没雷了）
        List<Vec3d> points = new ArrayList<>(VOLLEY_COUNT);
        for (int i = 0; i < VOLLEY_COUNT; i++) {
            double a = world.random.nextDouble() * Math.PI * 2.0D;
            double r = i < VOLLEY_LOCKED
                    ? world.random.nextDouble() * VOLLEY_LOCK_JITTER
                    : Math.sqrt(world.random.nextDouble()) * VOLLEY_SPREAD;
            double x = center.x + Math.cos(a) * r;
            double z = center.z + Math.sin(a) * r;
            points.add(new Vec3d(x, groundY(world, x, z, center.y), z));
        }
        VOLLEYS.add(new Volley(world.getRegistryKey(), points, now));

        // 起手：地面铺一圈雷纹（贴图）+ 雷云压顶的音效
        spawnVolleyRing(world, center);
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2.0D * i / 20.0D;
            world.spawnParticles(ParticleTypes.CLOUD,
                    center.x + Math.cos(a) * VOLLEY_SPREAD * 0.85D,
                    center.y + 8.0D,
                    center.z + Math.sin(a) * VOLLEY_SPREAD * 0.85D,
                    1, 0.0D, 0.0D, 0.0D, 0.02D);
        }
        world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ITEM_TRIDENT_THUNDER,
                SoundCategory.PLAYERS, 1.3F, 0.8F);
        world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.WEATHER, 1.0F, 0.85F);
        return true;
    }

    /**
     * 万雷的落点雷纹。
     *
     * <p>原先是用 44 个 {@code END_ROD} 粒子拼一圈虚线——远看是一串点，读不出「这片地」。
     * 改成一张贴地雷环（{@code thunder_ring01.png}），半径直接等于杀伤半径，
     * 玩家一眼就能看出「这一圈里都会挨雷」。
     *
     * <p>{@code fixed = true}：钉在落点不动，不跟玩家脚走（那是雷怒的用法）。
     */
    private static void spawnVolleyRing(ServerWorld world, Vec3d center) {
        ThunderRingEntity ring = new ThunderRingEntity(EntityRegistry.THUNDER_RING, world);
        ring.setFixed(true);
        ring.setStyle(ThunderRingEntity.STYLE_THUNDER_FIELD);
        ring.setRadius((float) VOLLEY_SPREAD);
        // 雷雨本身 18 道 × 2 tick = 36 tick；多留一段，让收尾时范围还在视野里
        ring.setLifeTicks(VOLLEY_COUNT * VOLLEY_STEP + 14);
        ring.refreshPositionAndAngles(center.x, center.y, center.z, 0.0F, 0.0F);
        world.spawnEntity(ring);
    }

    private static void strikeVolley(ServerWorld world, Vec3d at) {
        spawnBolt(world, at);
        damageArea(world, at, VOLLEY_RADIUS, VOLLEY_DAMAGE, VOLLEY_PARALYZE_TICKS);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                at.x, at.y + 0.8D, at.z, 14, 0.30D, 0.65D, 0.30D, 0.12D);
    }

    // ================== 【X 键 · 雷怒】 ==================

    /** @return 是否真的开出来了（冷却中返回 false，并提示剩余秒数） */
    public static boolean castWrath(ServerPlayerEntity player) {
        if (player.isDead() || player.isSpectator()) {
            return false;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        long now = world.getTime();

        if (!SkillCooldowns.isReady(player, SKILL_WRATH)) {
            SkillCooldowns.notifyCooldown(player, SKILL_WRATH, "雷怒");
            return false;
        }

        WRATH_END.put(player.getUuid(), now + WRATH_DURATION);
        SkillCooldowns.start(player, SKILL_WRATH, WRATH_COOLDOWN);
        applyWrath(player);

        // 脚下那圈雷环：跟脚走满 15 秒
        spawnRing(player, world);

        // 起手：脚下一圈电弧往上卷 + 一记雷声
        Vec3d pos = player.getPos();
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y + 1.0D, pos.z, 80, 0.9D, 1.2D, 0.9D, 0.30D);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.2D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        // 边界先亮一圈，让「3 格」这件事一眼可见
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2.0D * i / 32.0D;
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + Math.cos(a) * RING_RADIUS, pos.y + 0.06D, pos.z + Math.sin(a) * RING_RADIUS,
                    1, 0.0D, 0.02D, 0.0D, 0.0D);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER,
                SoundCategory.PLAYERS, 1.5F, 1.25F);

        player.sendMessage(Text.translatable("message.wangsword.thunder_katana.wrath"), true);
        return true;
    }

    /** 雷怒在不在（以结束时刻为准，属性没了也能照常判定） */
    private static boolean isWrathActive(ServerPlayerEntity player, long now) {
        Long end = WRATH_END.get(player.getUuid());
        return end != null && now < end;
    }

    /** 上buff：攻击力 +5（加法）、攻速 +30%（乘法），已经在了就不重复加 */
    private static void applyWrath(ServerPlayerEntity player) {
        EntityAttributeInstance attack = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attack != null && attack.getModifier(WRATH_ATTACK_ID) == null) {
            attack.addTemporaryModifier(new EntityAttributeModifier(WRATH_ATTACK_ID, "ws_thunder_wrath_atk",
                    WRATH_ATTACK_BONUS, EntityAttributeModifier.Operation.ADDITION));
        }
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (speed != null && speed.getModifier(WRATH_SPEED_ID) == null) {
            speed.addTemporaryModifier(new EntityAttributeModifier(WRATH_SPEED_ID, "ws_thunder_wrath_spd",
                    WRATH_SPEED_BONUS, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** 撤buff */
    private static void clearWrath(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        EntityAttributeInstance attack = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attack != null) {
            EntityAttributeModifier m = attack.getModifier(WRATH_ATTACK_ID);
            if (m != null) {
                attack.removeModifier(m);
            }
        }
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (speed != null) {
            EntityAttributeModifier m = speed.getModifier(WRATH_SPEED_ID);
            if (m != null) {
                speed.removeModifier(m);
            }
        }
    }

    // ================== 【雷怒的脚下雷环】 ==================

    /** 生成一圈跟着脚走的雷环；重复开技能先掐掉旧的 */
    private static void spawnRing(ServerPlayerEntity player, ServerWorld world) {
        removeRing(player.getUuid());
        ThunderRingEntity ring = new ThunderRingEntity(EntityRegistry.THUNDER_RING, world);
        ring.setRadius(RING_RADIUS);
        ring.setOwner(player);
        ring.setLifeTicks(WRATH_DURATION);
        ring.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        world.spawnEntity(ring);
        WRATH_RINGS.put(player.getUuid(), ring);
    }

    private static void removeRing(UUID playerId) {
        ThunderRingEntity ring = WRATH_RINGS.remove(playerId);
        if (ring != null && !ring.isRemoved()) {
            ring.discard();
        }
    }

    /** 环上的电弧：每 4 tick 撒一圈电火花，每 20 tick 补一圈亮点当节拍 */
    private static void tickRing(ServerPlayerEntity player, ServerWorld world, long now) {
        if (now % 4L != 0L) {
            return;
        }
        Vec3d pos = player.getPos();
        int points = 10;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0D * i / points + now * 0.05D;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + Math.cos(a) * RING_RADIUS,
                    pos.y + 0.05D,
                    pos.z + Math.sin(a) * RING_RADIUS,
                    1, 0.02D, 0.10D, 0.02D, 0.03D);
        }
        if (now % 20L == 0L) {
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2.0D * i / 12.0D;
                world.spawnParticles(ParticleTypes.END_ROD,
                        pos.x + Math.cos(a) * RING_RADIUS,
                        pos.y + 0.05D,
                        pos.z + Math.sin(a) * RING_RADIUS,
                        1, 0.0D, 0.02D, 0.0D, 0.0D);
            }
        }
    }

    // ================== 【每 tick】 ==================

    private static void onServerTick(MinecraftServer server) {
        tickCharges(server);
        tickVolleys(server);
        tickWrath(server);
    }

    /** 雷纹超过 6 秒没命中就掉光 */
    private static void tickCharges(MinecraftServer server) {
        if (CHARGES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Charge> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            long now = player.getWorld().getTime();
            if (now - entry.getValue().lastHitTick > STACK_DECAY) {
                applySpeed(player, 0);
                it.remove();
            }
        }
    }

    /** 雷雨：每 2 tick 一道，落满 18 道收工 */
    private static void tickVolleys(MinecraftServer server) {
        if (VOLLEYS.isEmpty()) {
            return;
        }
        Iterator<Volley> it = VOLLEYS.iterator();
        while (it.hasNext()) {
            Volley volley = it.next();
            ServerWorld world = server.getWorld(volley.world);
            if (world == null) {
                it.remove();
                continue;
            }
            long now = world.getTime();
            while (volley.next < volley.points.size()
                    && now >= volley.start + (long) volley.next * VOLLEY_STEP) {
                strikeVolley(world, volley.points.get(volley.next));
                volley.next++;
            }
            if (volley.next >= volley.points.size()) {
                it.remove();
            }
        }
    }

    /** 雷怒：到点撤 buff；玩家死了也撤 */
    private static void tickWrath(MinecraftServer server) {
        if (WRATH_END.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Long>> it = WRATH_END.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.isDead()) {
                clearWrath(player);
                removeRing(entry.getKey());
                it.remove();
                continue;
            }
            long now = player.getWorld().getTime();
            if (now >= entry.getValue()) {
                clearWrath(player);
                removeRing(entry.getKey());
                player.sendMessage(Text.translatable("message.wangsword.thunder_katana.wrath_end"), true);
                it.remove();
                continue;
            }
            // 复活/重连后属性被重置的情况：还在持续就补回来
            applyWrath(player);
            // 环上的电弧
            if (player.getWorld() instanceof ServerWorld ringWorld) {
                tickRing(player, ringWorld, now);
            }
        }
    }

    // ================== 【工具方法】 ==================

    /** 攻速加成：每层 +4%，层数变了就整颗重算 */
    private static void applySpeed(ServerPlayerEntity player, int stacks) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (instance == null) {
            return;
        }
        EntityAttributeModifier existing = instance.getModifier(SPEED_ID);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        if (stacks <= 0) {
            return;
        }
        instance.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID, "ws_thunder_charge",
                ATTACK_SPEED_PER_STACK * stacks, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 落一道紫电（只有画面和音效，伤害全部由本类结算） */
    private static void spawnBolt(ServerWorld world, Vec3d at) {
        ThunderBoltEntity.spawn(world, at, ThunderBoltEntity.PURPLE, true);
    }

    /**
     * 准心落点。
     *
     * <p>先做一次「生物射线」：只看方块的话，准心对着怪物的胸口时射线会从它身上穿过去，
     * 落在身后好几格的地面上，雷就砸空了——玩家最自然的用法反而打不中。
     */
    private static Vec3d resolveImpact(ServerPlayerEntity player, ServerWorld world, double maxRange) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        Vec3d end = eye.add(dir.multiply(maxRange));

        net.minecraft.util.hit.EntityHitResult entityHit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                player, eye, end,
                player.getBoundingBox().stretch(dir.multiply(maxRange)).expand(1.0D),
                e -> e instanceof LivingEntity living && CombatTargets.isSkillTarget(living),
                maxRange * maxRange);

        Vec3d point;
        if (entityHit != null) {
            point = entityHit.getPos();
        } else {
            net.minecraft.util.hit.HitResult hit = player.raycast(maxRange, 1.0F, false);
            point = hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK ? hit.getPos() : end;
        }
        return new Vec3d(point.x, groundY(world, point.x, point.z, point.y), point.z);
    }

    /** 把 y 贴到该列的地面；和参考高度差太多（洞穴、悬空平台）就保留原高度 */
    private static double groundY(ServerWorld world, double x, double z, double yHint) {
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
        if (Math.abs(top - yHint) > 16.0D) {
            return yHint;
        }
        return Math.max(top, yHint - 1.0D);
    }

    /** 圈内伤害 + 麻痹；索敌走 isSkillTarget，牛羊这类也照打 */
    private static void damageArea(ServerWorld world, Vec3d center, double radius,
                                   float damage, int paralyzeTicks) {
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
                new net.minecraft.util.math.Box(center, center).expand(radius),
                CombatTargets::isSkillTarget)) {
            if (target.squaredDistanceTo(center) > radius * radius) {
                continue;
            }
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().lightningBolt(), damage);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, paralyzeTicks, 1));
        }
    }
}
