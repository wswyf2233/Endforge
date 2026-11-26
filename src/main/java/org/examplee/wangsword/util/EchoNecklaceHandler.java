package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.examplee.wangsword.registry.ItemsRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 残响项链：大伤害不会立刻全部落下，超出阈值的部分会被拆成 4 秒 8 段慢慢结算。
 *
 * <p>为什么这么写：{@code ServerLivingEntityEvents.ALLOW_DAMAGE} 只能返回 true/false，
 * 改不了数值。所以这里先<b>取消</b>原始伤害，把「阈值内」的部分记成 {@code immediate}，
 * 下一 tick 原样补一刀；剩下的记成 {@code pending}，每 {@value #CHUNK_INTERVAL} tick 扣一段。
 *
 * <p>规则：
 * <ul>
 *   <li>单次伤害 ≤ 最大生命 × {@value #THRESHOLD_PERCENT}% 时正常结算，项链不介入；</li>
 *   <li>超过的部分拆成 {@value #CHUNKS} 段，4 秒内结算完；</li>
 *   <li>结算期间生命每回升 1 点，就抵扣 1 点待结算残响——回血就是消解残响的方式；</li>
 *   <li>残响未清空期间攻击力 +{@value #ATTACK_BONUS_PERCENT}%；</li>
 *   <li>摘下项链时剩余残响一次性结算，不能靠脱饰品白嫖。</li>
 * </ul>
 *
 * <p>没结算完的残响写进存档（{@link PlayerStore}）：重登、换维度、死亡复活之后接着扣，
 * 不会靠退游戏把这一刀赖掉。代价是原来的伤害来源没法一起存，读档后补的几段算作
 * {@code generic} 伤害——护甲照样吃，只是死亡信息不再追究是哪只怪打的。
 */
public final class EchoNecklaceHandler {

    /** 触发阈值：最大生命的 20% */
    private static final float THRESHOLD_PERCENT = 0.20F;
    /** 拆成几段 */
    private static final int CHUNKS = 8;
    /** 每段间隔 tick（8 × 10 = 80 tick = 4 秒） */
    private static final int CHUNK_INTERVAL = 10;
    /** 残响期间攻击力加成 */
    private static final double ATTACK_BONUS = 0.10D;
    /** 存档里的模块名 */
    private static final String STORE_KEY = "echo";

    private static final UUID MODIFIER_ID = UUID.fromString("c47e9a10-2d53-4b8f-9e21-73a6b8d40f52");

    /** 正在补伤害的玩家：避免自己造成的那一刀又被自己的 ALLOW_DAMAGE 二次延迟 */
    private static final Set<UUID> APPLYING = new HashSet<>();

    private static final class State {
        /** 还没结算的残响总量 */
        float pending;
        /** 当前每段结算量 */
        float chunk;
        /** 还有多少 tick 到下一段 */
        int ticksUntilNext;
        /** 下一 tick 要立刻补的那一刀（阈值内的部分） */
        float immediate;
        /** 原始伤害来源 */
        DamageSource source;
        /** 上一 tick 的生命值，用来检测「治疗抵扣」 */
        float lastHealth;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private EchoNecklaceHandler() {
    }

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player) || !isWearing(player)) {
                return true;
            }
            if (APPLYING.contains(player.getUuid())) {
                return true;
            }
            // 自伤与掉虚空不参与：残响不是免死金牌，掉虚空也不该被拆段
            if (source.getAttacker() == player || source.isOf(DamageTypes.OUT_OF_WORLD)) {
                return true;
            }

            float threshold = player.getMaxHealth() * THRESHOLD_PERCENT;
            if (amount <= threshold) {
                return true;
            }

            State state = state(player);
            float deferred = amount - threshold;

            state.immediate += threshold;
            state.pending += deferred;
            state.chunk = state.pending / CHUNKS;
            state.ticksUntilNext = CHUNK_INTERVAL;
            state.source = source;
            state.lastHealth = player.getHealth();
            refreshAttack(player);

            playTriggerEffect(player);
            // 取消原始伤害，改由 tick 里分段补
            return false;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tick(player);
            }
        });

        // 死亡时清空，避免复活后还带着上一轮的残响
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                clearState(player);
            }
        });

        // 关存档时丢掉内存缓存
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }

    public static void tick(ServerPlayerEntity player) {
        if (player.isDead()) {
            return;
        }
        // 项链已经不在了（换装/丢失）：清空。正常脱装备会先走 onUnequip 结清，这里是兜底
        if (!isWearing(player)) {
            if (STATES.containsKey(player.getUuid())) {
                clearState(player);
            }
            return;
        }

        State state = STATES.get(player.getUuid());
        if (state == null) {
            // 读档：上一次没结算完的残响接着扣
            state = load(player);
            if (state.pending <= 0.0F && state.immediate <= 0.0F) {
                return;
            }
            STATES.put(player.getUuid(), state);
            refreshAttack(player);
        }

        boolean changed = false;

        // 1. 先补上阈值内的那一刀
        if (state.immediate > 0.0F) {
            float immediate = state.immediate;
            state.immediate = 0.0F;
            apply(player, state.source, immediate, false);
            changed = true;
        }

        // 2. 治疗抵扣：这一 tick 生命涨了多少，就抵掉多少残响
        float health = player.getHealth();
        if (health > state.lastHealth) {
            float healed = Math.min(state.pending, health - state.lastHealth);
            state.pending = Math.max(0.0F, state.pending - (health - state.lastHealth));
            if (healed > 0.0F) {
                changed = true;
            }
            if (state.pending > 0.0F) {
                // 抵扣后每段的量跟着缩小，保证仍然 8 段打完
                state.chunk = Math.min(state.chunk, state.pending / CHUNKS);
            }
        }
        state.lastHealth = player.getHealth();

        // 3. 分段结算
        if (state.pending > 0.0F && --state.ticksUntilNext <= 0) {
            state.ticksUntilNext = CHUNK_INTERVAL;
            float deal = Math.min(state.pending, state.chunk);
            state.pending -= deal;
            apply(player, state.source, deal, true);
            state.lastHealth = player.getHealth();
            changed = true;
        }

        if (state.pending <= 0.0F && state.immediate <= 0.0F) {
            state.pending = 0.0F;
            refreshAttack(player);
        }

        if (changed) {
            save(player);
        }
    }

    /** 摘下项链：剩余残响一次结清，顺便清掉攻击加成与存档 */
    public static void onUnequip(ServerPlayerEntity player) {
        State state = STATES.remove(player.getUuid());
        if (state != null) {
            float total = state.immediate + state.pending;
            if (total > 0.0F) {
                apply(player, state.source, total, true);
            }
        }
        PlayerStore.erase(player, STORE_KEY);
        refreshAttack(player);
    }

    public static float pendingOf(ServerPlayerEntity player) {
        State state = STATES.get(player.getUuid());
        return state == null ? 0.0F : state.pending;
    }

    /** 拿状态；内存里没有就从存档里捞回来 */
    private static State state(ServerPlayerEntity player) {
        State cached = STATES.get(player.getUuid());
        if (cached != null) {
            return cached;
        }
        State loaded = load(player);
        STATES.put(player.getUuid(), loaded);
        if (loaded.pending > 0.0F) {
            refreshAttack(player);
        }
        return loaded;
    }

    private static State load(ServerPlayerEntity player) {
        State state = new State();
        NbtCompound nbt = PlayerStore.get(player, STORE_KEY);
        if (!nbt.isEmpty()) {
            state.pending = Math.max(0.0F, nbt.getFloat("pending"));
            state.chunk = Math.max(0.0F, nbt.getFloat("chunk"));
            state.ticksUntilNext = Math.max(1, nbt.getInt("ticks_until_next"));
            state.immediate = Math.max(0.0F, nbt.getFloat("immediate"));
        }
        // 读档后没法把 DamageSource 一起还原，后续几段按 generic 结算；护甲照吃
        state.source = null;
        state.lastHealth = player.getHealth();
        return state;
    }

    private static void save(ServerPlayerEntity player) {
        State state = STATES.get(player.getUuid());
        if (state == null) {
            return;
        }
        NbtCompound nbt = new NbtCompound();
        nbt.putFloat("pending", state.pending);
        nbt.putFloat("chunk", state.chunk);
        nbt.putInt("ticks_until_next", state.ticksUntilNext);
        nbt.putFloat("immediate", state.immediate);
        PlayerStore.put(player, STORE_KEY, nbt);
    }

    private static void clearState(ServerPlayerEntity player) {
        STATES.remove(player.getUuid());
        PlayerStore.erase(player, STORE_KEY);
        refreshAttack(player);
    }

    /** 补伤害：套一层标记，防止这一刀再被自己延迟一次 */
    private static void apply(ServerPlayerEntity player, DamageSource source, float amount, boolean keepMomentum) {
        if (amount <= 0.0F || player.isDead()) {
            return;
        }
        // 读档回来的残响没有原始来源，按 generic 结算（吃护甲，不是魔法伤害）
        DamageSource actual = source != null ? source : player.getDamageSources().generic();
        APPLYING.add(player.getUuid());
        try {
            // 分段结算正好每 10 tick 一次，不把无敌帧清掉的话后几段会被吃掉
            player.timeUntilRegen = 0;
            net.minecraft.util.math.Vec3d velocity = keepMomentum ? player.getVelocity() : null;
            player.damage(actual, amount);
            // 残响的 8 段不应该把玩家一路推走：还原速度，只保留掉血
            if (velocity != null && !player.isDead()) {
                player.setVelocity(velocity);
                player.velocityModified = true;
            }
        } finally {
            APPLYING.remove(player.getUuid());
        }
    }

    private static void refreshAttack(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        EntityAttributeModifier existing = instance.getModifier(MODIFIER_ID);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        State state = STATES.get(player.getUuid());
        if (state == null || state.pending <= 0.0F) {
            return;
        }
        instance.addTemporaryModifier(new EntityAttributeModifier(
                MODIFIER_ID, "ws_echo_necklace", ATTACK_BONUS,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void playTriggerEffect(ServerPlayerEntity player) {
        if (player.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.SCULK_SOUL,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    18, 0.4D, 0.5D, 0.4D, 0.02D);
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 0.9F, 0.6F);
        }
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return io.wispforest.accessories.api.AccessoriesCapability.getOptionally(player)
                .map(capability -> capability.isEquipped(ItemsRegistry.WS_NECKLACE_ECHO))
                .orElse(false);
    }
}
