package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import org.examplee.wangsword.registry.ItemsRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 猎魂项链：咬着同一个目标不放，印记越叠越深，打死时炸开。
 *
 * <p>规则：
 * <ul>
 *   <li>命中一个目标 +1 层印记（上限 {@value #MAX_MARKS} 层），每层 +3% 攻击力；</li>
 *   <li>换目标立刻从 1 层重新开始，{@value #DECAY_TICKS} tick 没命中也会清零；</li>
 *   <li>印记目标被击杀时炸开：{@value #BURST_RADIUS} 格内敌对生物受到 层数×2 点魔法伤害，
 *       同时给玩家回 {@value #KILL_HEAL} 点生命。</li>
 * </ul>
 *
 * <p>攻击加成用临时属性修正器实现，所以它是<b>全局</b>加成——印记不掉，打谁都有这份伤害。
 * 这是刻意的：加成的作用是奖励"别换目标"，而不是只对标记目标生效。
 *
 * <p>印记写进存档（{@link PlayerStore}），重登、换维度、死亡复活都不清零；目标实体本身
 * 是 UUID，重登后如果那只怪已经没了，靠超时自动散掉。
 */
public final class HuntsoulHandler {

    /** 印记上限 */
    private static final int MAX_MARKS = 5;
    /** 每层印记提供的攻击力百分比（MULTIPLY_TOTAL） */
    private static final double DAMAGE_PER_MARK = 0.03D;
    /** 多久没命中就清零：6 秒 */
    private static final int DECAY_TICKS = 120;
    /** 击杀回血量 */
    private static final float KILL_HEAL = 2.0F;
    /** 印记炸开的半径 */
    private static final double BURST_RADIUS = 4.0D;
    /** 每层印记的爆炸伤害 */
    private static final float BURST_PER_MARK = 2.0F;
    /** 重登时的衰减缓冲：2 秒 */
    private static final int REJOIN_GRACE = 40;
    /** 存档里的模块名 */
    private static final String STORE_KEY = "huntsoul";

    private static final UUID MODIFIER_ID = UUID.fromString("1f7a2c94-3b8e-4d05-9c61-58d0a2b4e7f3");

    private static final class State {
        UUID target;
        int marks;
        long lastHit;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private HuntsoulHandler() {
    }

    public static void init() {
        // 命中：叠印记
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient()
                    && player instanceof ServerPlayerEntity serverPlayer
                    && entity instanceof LivingEntity target) {
                onHit(serverPlayer, target);
            }
            return ActionResult.PASS;
        });

        // 死亡：印记目标被打死就炸开
        ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity player) || !isWearing(player)) {
                return;
            }
            State state = STATES.get(player.getUuid());
            if (state == null || state.target == null || !state.target.equals(dead.getUuid())) {
                return;
            }

            int marks = state.marks;
            state.target = null;
            state.marks = 0;
            refresh(player);
            save(player);

            player.heal(KILL_HEAL);
            burst(player, dead, marks);
        });

        // 关存档时丢掉内存缓存
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }

    /** 每 tick：脱战超时就清空印记。由项链的 {@code Accessory#tick} 调用。 */
    public static void tick(ServerPlayerEntity player) {
        State state = state(player);
        if (state.marks <= 0) {
            return;
        }
        if (GameTime.now(player) - state.lastHit > DECAY_TICKS) {
            state.target = null;
            state.marks = 0;
            refresh(player);
            save(player);
        }
    }

    /** 摘下项链：印记与加成一起清掉（存档里的也一起删） */
    public static void clear(ServerPlayerEntity player) {
        STATES.remove(player.getUuid());
        PlayerStore.erase(player, STORE_KEY);
        refresh(player);
    }

    public static int marksOf(ServerPlayerEntity player) {
        State state = STATES.get(player.getUuid());
        return state == null ? 0 : state.marks;
    }

    /** 拿状态；内存里没有就从存档里捞回来 */
    private static State state(ServerPlayerEntity player) {
        State cached = STATES.get(player.getUuid());
        if (cached != null) {
            return cached;
        }
        State loaded = new State();
        NbtCompound nbt = PlayerStore.get(player, STORE_KEY);
        if (!nbt.isEmpty()) {
            loaded.marks = Math.max(0, Math.min(MAX_MARKS, nbt.getInt("marks")));
            String target = nbt.getString("target");
            if (!target.isEmpty() && loaded.marks > 0) {
                try {
                    loaded.target = UUID.fromString(target);
                } catch (IllegalArgumentException ignored) {
                    loaded.marks = 0;
                }
            }
            long lastHit = nbt.getLong("last_hit");
            long now = GameTime.now(player);
            // 离线时间照算，但至少留 2 秒缓冲，别刚进游戏印记就没了
            loaded.lastHit = now - lastHit > DECAY_TICKS ? now - (DECAY_TICKS - REJOIN_GRACE) : lastHit;
        }
        STATES.put(player.getUuid(), loaded);
        // 读档回来的印记要立刻把攻击力加成挂回去
        if (loaded.marks > 0) {
            refresh(player);
        }
        return loaded;
    }

    /** 把当前状态写回存档 */
    private static void save(ServerPlayerEntity player) {
        State state = STATES.get(player.getUuid());
        if (state == null) {
            return;
        }
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("marks", state.marks);
        nbt.putString("target", state.target == null ? "" : state.target.toString());
        nbt.putLong("last_hit", state.lastHit);
        PlayerStore.put(player, STORE_KEY, nbt);
    }

    private static void onHit(ServerPlayerEntity player, LivingEntity target) {
        if (!isWearing(player)) {
            return;
        }
        State state = state(player);
        if (state.target != null && state.target.equals(target.getUuid())) {
            state.marks = Math.min(MAX_MARKS, state.marks + 1);
        } else {
            state.target = target.getUuid();
            state.marks = 1;
        }
        state.lastHit = GameTime.now(player);
        refresh(player);
        save(player);
    }

    private static void burst(ServerPlayerEntity player, LivingEntity dead, int marks) {
        if (marks <= 0 || !(dead.getWorld() instanceof ServerWorld world)) {
            return;
        }
        float damage = marks * BURST_PER_MARK;
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class,
                dead.getBoundingBox().expand(BURST_RADIUS),
                candidate -> candidate instanceof HostileEntity && candidate.isAlive())) {
            victim.timeUntilRegen = 0;
            victim.damage(world.getDamageSources().magic(), damage);
        }
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                dead.getX(), dead.getY() + dead.getHeight() * 0.5D, dead.getZ(),
                6, 0.9D, 0.4D, 0.9D, 0.0D);
        world.spawnParticles(ParticleTypes.SOUL,
                dead.getX(), dead.getY() + dead.getHeight() * 0.5D, dead.getZ(),
                16, 0.5D, 0.5D, 0.5D, 0.02D);
        world.playSound(null, dead.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 1.0F, 0.7F);
    }

    /** 印记变化后重新挂攻击力加成；0 层就是把修正器摘掉 */
    private static void refresh(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        EntityAttributeModifier existing = instance.getModifier(MODIFIER_ID);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        State state = STATES.get(player.getUuid());
        if (state == null || state.marks <= 0) {
            return;
        }
        instance.addTemporaryModifier(new EntityAttributeModifier(
                MODIFIER_ID, "ws_huntsoul",
                state.marks * DAMAGE_PER_MARK,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return io.wispforest.accessories.api.AccessoriesCapability.getOptionally(player)
                .map(capability -> capability.isEquipped(ItemsRegistry.WS_NECKLACE_HUNTSOUL))
                .orElse(false);
    }
}
