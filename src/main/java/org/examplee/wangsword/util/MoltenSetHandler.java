package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.registry.ArmorRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 熔岩套（战盔 / 胸甲 / 护腿 / 战靴）的套装技能。
 *
 * <p><b>A · 熔核层（被动）</b>：穿几件就有几层上限（最多 4）。近战命中 +1 层，
 * 每层 +5% 攻击力（用 {@code GENERIC_ATTACK_DAMAGE} 的 MULTIPLY_TOTAL 修饰符实现，
 * 只影响普通近战，不污染技能伤害）。满 4 层时下一击触发「熔核爆裂」：
 * 半径 3.5、伤害为本次伤害的 45%、点燃 4 秒，然后层数清零。
 * 8 秒不命中开始衰减，每 2 秒掉一层。
 *
 * <p><b>B · 熔岩喷发（主动）</b>：穿齐四件后按专用键（默认 G，见
 * {@code key.wangsword.armor_skill}，客户端发包到 {@link #ERUPTION_PACKET}）。
 * 半径 5 的环形爆发，伤害 8 + 5×层数，点燃 6 秒，把周围推开，消耗全部层数，冷却 20 秒。
 * 不依赖手持物品，也不占用右键——空手、拿剑、拿方块都能放。
 *
 * <p>爆裂必须延后一 tick 执行：{@code ALLOW_DAMAGE} 发生在伤害结算之前，
 * 当场打第二段会被无敌帧吃掉，反而是主伤害被吃掉。所以这里先入队，下一个
 * 服务器 tick 再结算。
 */
public final class MoltenSetHandler {

    /** 主动技能 id（跟 SkillCooldownHud 的注册对得上） */
    public static final String SKILL_ERUPTION = "caldera_eruption";
    /** 客户端按键 → 服务端放技能的包 */
    public static final Identifier ERUPTION_PACKET = new Identifier("wangsword", "caldera_eruption_cast");
    /** 喷发冷却 20 秒 */
    public static final int ERUPTION_COOLDOWN = 400;
    public static final int ERUPTION_COOLDOWN_SECONDS = ERUPTION_COOLDOWN / 20;

    /** 套装件数上限 */
    public static final int MAX_PIECES = 4;
    /** 每层增伤比例 */
    private static final double DAMAGE_PER_STACK = 0.05D;
    /** 每层增伤百分比，给物品介绍用 */
    public static final int DAMAGE_PERCENT_PER_STACK = (int) Math.round(DAMAGE_PER_STACK * 100.0D);
    /** 多久不命中开始衰减 */
    private static final int STACK_TIMEOUT = 160;
    /** 衰减间隔 */
    private static final int DECAY_INTERVAL = 40;
    public static final int STACK_TIMEOUT_SECONDS = STACK_TIMEOUT / 20;
    public static final int DECAY_INTERVAL_SECONDS = DECAY_INTERVAL / 20;

    /** 爆裂参数 */
    public static final double BURST_RADIUS = 3.5D;
    private static final float BURST_RATIO = 0.45F;
    public static final int BURST_PERCENT = Math.round(BURST_RATIO * 100.0F);
    public static final int BURST_FIRE_SECONDS = 4;

    /** 喷发参数 */
    private static final double ERUPTION_RADIUS = 5.0D;
    private static final float ERUPTION_BASE_DAMAGE = 8.0F;
    private static final float ERUPTION_DAMAGE_PER_STACK = 5.0F;
    private static final int ERUPTION_FIRE_SECONDS = 6;
    public static final int ERUPTION_RADIUS_INT = (int) ERUPTION_RADIUS;
    public static final int ERUPTION_BASE_DAMAGE_INT = (int) ERUPTION_BASE_DAMAGE;
    public static final int ERUPTION_DAMAGE_PER_STACK_INT = (int) ERUPTION_DAMAGE_PER_STACK;
    public static final int ERUPTION_FIRE_SECONDS_INT = ERUPTION_FIRE_SECONDS;

    /** 1.20.1 的修饰符 id 还是 UUID，固定写死一个，别每次 new 随机 */
    private static final UUID MODIFIER_ID = UUID.fromString("7b3f1d2c-6a4e-4c8b-9f5a-2d1e0c3b4a59");
    private static final String MODIFIER_NAME = "Molten Charge";

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** 爆裂期间别让范围伤害再触发叠层 */
    private static boolean resolving = false;

    /** 待结算的爆裂（下一 tick 执行） */
    private static final List<PendingBurst> PENDING = new ArrayList<>();

    private MoltenSetHandler() {
    }

    private static final class State {
        int stacks;
        int lastHit;
        int lastDecay;
        /** 上一 tick 的套装件数，-1 表示还没初始化过 */
        int lastPieces = -1;
        /** 上一 tick 技能是否还在冷却里，用来抓「冷却结束」那一刻 */
        boolean cooling;
    }

    private record PendingBurst(ServerPlayerEntity player, LivingEntity struck, float damage) {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(MoltenSetHandler::onServerTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MoltenSetHandler::onDamage);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        // 客户端按专用键（默认 G）→ 服务端校验套装与冷却后放技能
        ServerPlayNetworking.registerGlobalReceiver(ERUPTION_PACKET,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> castEruption(player)));
    }

    // ------------------------------------------------------------------
    // 层数

    /** 身上穿了几件熔岩套 */
    public static int pieces(PlayerEntity player) {
        int count = 0;
        if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(ArmorRegistry.CALDERA_HELM)) count++;
        if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(ArmorRegistry.CALDERA_CUIRASS)) count++;
        if (player.getEquippedStack(EquipmentSlot.LEGS).isOf(ArmorRegistry.CALDERA_GREAVES)) count++;
        if (player.getEquippedStack(EquipmentSlot.FEET).isOf(ArmorRegistry.CALDERA_BOOTS)) count++;
        return count;
    }

    /** 当前层数，给 HUD / 调试用 */
    public static int stacksOf(PlayerEntity player) {
        State state = STATES.get(player.getUuid());
        return state == null ? 0 : state.stacks;
    }

    private static State state(ServerPlayerEntity player) {
        return STATES.computeIfAbsent(player.getUuid(), key -> new State());
    }

    private static void clear(ServerPlayerEntity player) {
        STATES.remove(player.getUuid());
        EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (inst != null) {
            inst.removeModifier(MODIFIER_ID);
        }
    }

    /** 把层数同步成属性修饰符（只在变化时才动属性） */
    private static void applyModifier(ServerPlayerEntity player, int stacks) {
        EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (inst == null) {
            return;
        }
        EntityAttributeModifier current = inst.getModifier(MODIFIER_ID);
        double want = stacks <= 0 ? 0.0D : DAMAGE_PER_STACK * stacks;

        if (stacks <= 0) {
            if (current != null) {
                inst.removeModifier(MODIFIER_ID);
            }
            return;
        }
        if (current != null && Math.abs(current.getValue() - want) < 1.0E-4D) {
            return;
        }
        if (current != null) {
            inst.removeModifier(MODIFIER_ID);
        }
        inst.addPersistentModifier(new EntityAttributeModifier(
                MODIFIER_ID, MODIFIER_NAME, want, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 往物品介绍里写熔岩套的技能说明：四件（战盔/胸甲/护腿/战靴）共用 */
    public static void appendTooltip(List<Text> tooltip) {
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.caldera_set.title"));
        // 被动 · 熔核层
        tooltip.add(Text.translatable("tooltip.wangsword.caldera_set.charge",
                DAMAGE_PERCENT_PER_STACK, MAX_PIECES));
        tooltip.add(Text.translatable("tooltip.wangsword.caldera_set.decay",
                STACK_TIMEOUT_SECONDS, DECAY_INTERVAL_SECONDS));
        tooltip.add(Text.translatable("tooltip.wangsword.caldera_set.burst",
                MAX_PIECES, BURST_RADIUS, BURST_PERCENT, BURST_FIRE_SECONDS));
        // 主动 · 熔岩喷发
        tooltip.add(Text.translatable("tooltip.wangsword.caldera_set.eruption", MAX_PIECES));
        tooltip.add(Text.translatable("tooltip.wangsword.caldera_set.eruption.desc",
                ERUPTION_RADIUS_INT, ERUPTION_BASE_DAMAGE_INT,
                ERUPTION_DAMAGE_PER_STACK_INT, ERUPTION_FIRE_SECONDS_INT));
        tooltip.add(Text.translatable("tooltip.wangsword.caldera_set.cooldown", ERUPTION_COOLDOWN_SECONDS));
    }

    private static void showStacks(ServerPlayerEntity player, int stacks, int max) {        Text text = Text.translatable("message.wangsword.molten_charge", stacks, max)
                .formatted(stacks >= max ? Formatting.GOLD : Formatting.YELLOW);
        player.sendMessage(text, true);
    }

    /** 件数变化时的动作栏提示；{@code before < 0} 表示刚登录，还没记录过 */
    private static void notifyPiecesChanged(ServerPlayerEntity player, int before, int now) {
        if (now >= MAX_PIECES) {
            if (before < MAX_PIECES) {
                player.sendMessage(Text.translatable("message.wangsword.caldera_set.active")
                        .formatted(Formatting.GOLD), true);
            }
            return;
        }
        if (before >= MAX_PIECES) {
            player.sendMessage(Text.translatable("message.wangsword.caldera_set.lost")
                    .formatted(Formatting.RED), true);
            return;
        }
        if (before < 0) {
            // 刚登录，穿半套不打扰
            return;
        }
        // 只在「往上穿」的时候报进度，脱装备不刷屏
        if (now > 0 && now > before) {
            player.sendMessage(Text.translatable("message.wangsword.caldera_set.partial",
                    now, MAX_PIECES, MAX_PIECES - now).formatted(Formatting.YELLOW), true);
        }
    }

    // ------------------------------------------------------------------
    // 每 tick：衰减 + 结算爆裂

    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            State state = state(player);
            int max = pieces(player);

            // 套装件数变化 → 动作栏提示当前进度 / 激活 / 失效
            if (max != state.lastPieces) {
                notifyPiecesChanged(player, state.lastPieces, max);
                state.lastPieces = max;
            }

            // 冷却结束的那一瞬间 → 动作栏提示「就绪」
            if (!SkillCooldowns.isReady(player, SKILL_ERUPTION)) {
                state.cooling = true;
            } else if (state.cooling) {
                state.cooling = false;
                if (max >= MAX_PIECES) {
                    player.sendMessage(Text.translatable("message.wangsword.caldera_set.ready")
                            .formatted(Formatting.GOLD), true);
                }
            }

            if (max <= 0 || player.isDead()) {
                state.stacks = 0;
                applyModifier(player, 0);
                continue;
            }
            if (state.stacks > max) {
                state.stacks = max;
                applyModifier(player, state.stacks);
            }
            int now = player.age;
            if (state.stacks > 0 && now - state.lastHit > STACK_TIMEOUT
                    && now - state.lastDecay >= DECAY_INTERVAL) {
                state.stacks--;
                state.lastDecay = now;
                applyModifier(player, state.stacks);
                showStacks(player, state.stacks, max);
            }
        }

        if (PENDING.isEmpty()) {
            return;
        }
        resolving = true;
        try {
            for (PendingBurst burst : PENDING) {
                doBurst(burst);
            }
        } finally {
            PENDING.clear();
            resolving = false;
        }
    }

    // ------------------------------------------------------------------
    // 近战命中：叠层 / 满层爆裂

    private static boolean onDamage(LivingEntity entity, DamageSource source, float amount) {
        if (resolving || entity == null || entity.isDead()) {
            return true;
        }
        if (!(source.getAttacker() instanceof ServerPlayerEntity player) || entity == player) {
            return true;
        }
        // 只认近战：箭、法术的 source 是投射物/其它实体
        if (source.getSource() != player) {
            return true;
        }
        int max = pieces(player);
        if (max <= 0) {
            return true;
        }

        State state = state(player);
        state.lastHit = player.age;

        if (state.stacks >= MAX_PIECES && max >= MAX_PIECES) {
            state.stacks = 0;
            applyModifier(player, 0);
            PENDING.add(new PendingBurst(player, entity, Math.max(amount, 1.0F)));
            showStacks(player, 0, max);
        } else {
            state.stacks = Math.min(max, state.stacks + 1);
            applyModifier(player, state.stacks);
            showStacks(player, state.stacks, max);
        }
        return true;
    }

    private static void doBurst(PendingBurst burst) {
        if (!(burst.player().getWorld() instanceof ServerWorld world) || !burst.player().isAlive()) {
            return;
        }
        PlayerEntity player = burst.player();
        float damage = burst.damage() * BURST_RATIO;

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(BURST_RADIUS, 2.5D, BURST_RADIUS),
                candidate -> candidate != player && candidate.isAlive())) {
            if (target.squaredDistanceTo(player) > BURST_RADIUS * BURST_RADIUS) {
                continue;
            }
            // 主伤害刚结算，目标还在无敌帧里，先清掉才能吃到爆裂
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().playerAttack(player), damage);
            target.setOnFireFor(BURST_FIRE_SECONDS);
        }

        world.spawnParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.4D, player.getZ(),
                50, BURST_RADIUS * 0.6D, 0.35D, BURST_RADIUS * 0.6D, 0.12D);
        world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2D, player.getZ(),
                30, BURST_RADIUS * 0.5D, 0.1D, BURST_RADIUS * 0.5D, 0.05D);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8F, 1.4F);

        if (burst.struck() != null && burst.struck().isAlive()) {
            world.spawnParticles(ParticleTypes.LAVA, burst.struck().getX(), burst.struck().getBodyY(0.5D),
                    burst.struck().getZ(), 12, 0.3D, 0.3D, 0.3D, 0.08D);
        }
    }

    // ------------------------------------------------------------------
    // 主动：熔岩喷发

    private static boolean castEruption(ServerPlayerEntity player) {
        if (pieces(player) < MAX_PIECES) {
            player.sendMessage(Text.translatable(
                    "message.wangsword.caldera_set.need_pieces", MAX_PIECES).formatted(Formatting.RED), true);
            return false;
        }
        if (!SkillCooldowns.tryUse(player, SKILL_ERUPTION, "熔岩喷发", ERUPTION_COOLDOWN)) {
            // 冷却中，tryUse 已经发过动作栏提示，这里就当技能按过了，别再放
            return true;
        }

        State state = state(player);
        int stacks = state.stacks;
        float damage = ERUPTION_BASE_DAMAGE + ERUPTION_DAMAGE_PER_STACK * stacks;
        ServerWorld world = (ServerWorld) player.getWorld();

        resolving = true;
        try {
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
                    player.getBoundingBox().expand(ERUPTION_RADIUS, 3.0D, ERUPTION_RADIUS),
                    candidate -> candidate != player && candidate.isAlive())) {
                double distance = target.squaredDistanceTo(player);
                if (distance > ERUPTION_RADIUS * ERUPTION_RADIUS) {
                    continue;
                }
                target.damage(world.getDamageSources().playerAttack(player), damage);
                target.setOnFireFor(ERUPTION_FIRE_SECONDS);
                // 以玩家为中心的向外推
                double dx = target.getX() - player.getX();
                double dz = target.getZ() - player.getZ();
                double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
                double push = 0.9D * (1.0D - Math.min(1.0D, length / ERUPTION_RADIUS)) + 0.25D;
                target.setVelocity(dx / length * push, 0.45D, dz / length * push);
                target.velocityModified = true;
            }
        } finally {
            resolving = false;
        }

        // 一圈火环粒子
        for (int i = 0; i < 72; i++) {
            double angle = Math.PI * 2.0D * i / 72.0D;
            double x = player.getX() + Math.cos(angle) * ERUPTION_RADIUS * 0.85D;
            double z = player.getZ() + Math.sin(angle) * ERUPTION_RADIUS * 0.85D;
            world.spawnParticles(ParticleTypes.LAVA, x, player.getY() + 0.15D, z, 3, 0.18D, 0.25D, 0.18D, 0.05D);
            world.spawnParticles(ParticleTypes.FLAME, x, player.getY() + 0.3D, z, 2, 0.12D, 0.35D, 0.12D, 0.02D);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.6D, player.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.4F, 0.75F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2F, 0.9F);

        state.stacks = 0;
        state.lastDecay = player.age;
        applyModifier(player, 0);
        showStacks(player, 0, MAX_PIECES);
        return true;
    }
}
