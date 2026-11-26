package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.examplee.wangsword.registry.ItemsRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 承魂项链：挨打攒层，层数换攻击力，攒够了可以抵一次致命伤。
 *
 * <p>规则：
 * <ul>
 *   <li>每次受到伤害 +1 层，上限 {@value #MAX_STACKS} 层；</li>
 *   <li>每层 +2% 攻击力（用临时属性修正器实现，层数变化时立刻重算）；</li>
 *   <li>层数 ≥ {@value #SAVE_STACKS} 且这次伤害足以致死时，燃尽全部层数直接豁免这次伤害，
 *       然后进入 {@value #SAVE_COOLDOWN_TICKS} tick 冷却；</li>
 *   <li>超过 {@value #DECAY_TICKS} tick 没再挨打，层数全部消散。</li>
 * </ul>
 *
 * <p>层数与冷却都写进存档（{@link PlayerStore}），重登、换维度、死亡复活都不清零；
 * 离线期间的时间照算，但重登时至少给 {@value #REJOIN_GRACE} tick 的缓冲，
 * 免得刚进游戏第一眼层数就没了。
 */
public final class SoulbearerHandler {

    /** 层数上限 */
    private static final int MAX_STACKS = 8;
    /** 每层提供的攻击力百分比（MULTIPLY_TOTAL） */
    private static final double DAMAGE_PER_STACK = 0.02D;
    /** 多久没挨打就清空层数：8 秒 */
    private static final int DECAY_TICKS = 160;
    /** 触发抵命至少需要的层数 */
    private static final int SAVE_STACKS = 4;
    /** 抵命冷却：60 秒 */
    private static final int SAVE_COOLDOWN_TICKS = 1200;
    /** 重登时的衰减缓冲：2 秒，避免读档第一 tick 就被判超时 */
    private static final int REJOIN_GRACE = 40;
    /** 存档里的模块名 */
    private static final String STORE_KEY = "soulbearer";

    private static final UUID MODIFIER_ID = UUID.fromString("9b3c1e42-5d77-4a10-8f2c-6a1d0e5b7c31");

    private static final class State {
        int stacks;
        long lastHit;
        long saveReadyAt;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private SoulbearerHandler() {
    }

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player) || !isWearing(player)) {
                return true;
            }
            // 自伤与掉虚空不参与：前者会被人刷层数，后者本来就不该救
            if (source.getAttacker() == player || source.isOf(DamageTypes.OUT_OF_WORLD)) {
                return true;
            }

            long now = GameTime.now(player);
            State state = state(player);

            if (amount >= player.getHealth()
                    && state.stacks >= SAVE_STACKS
                    && now >= state.saveReadyAt) {
                state.stacks = 0;
                state.lastHit = now;
                state.saveReadyAt = now + SAVE_COOLDOWN_TICKS;
                refresh(player);
                save(player);
                playSaveEffect(player);
                return false;
            }

            if (state.stacks < MAX_STACKS) {
                state.stacks++;
                refresh(player);
            }
            state.lastHit = now;
            save(player);
            return true;
        });

        // 关存档时丢掉内存缓存：同一个 JVM 里换存档，不能把上一个世界的层数带过去
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }

    /** 每 tick：脱战超时就清空层数。由项链的 {@code Accessory#tick} 调用。 */
    public static void tick(ServerPlayerEntity player) {
        State state = state(player);
        if (state.stacks <= 0) {
            return;
        }
        if (GameTime.now(player) - state.lastHit > DECAY_TICKS) {
            state.stacks = 0;
            refresh(player);
            save(player);
        }
    }

    /** 摘下项链：层数与加成一起清掉（存档里的也一起删） */
    public static void clear(ServerPlayerEntity player) {
        STATES.remove(player.getUuid());
        PlayerStore.erase(player, STORE_KEY);
        refresh(player);
    }

    public static int stacksOf(ServerPlayerEntity player) {
        State state = STATES.get(player.getUuid());
        return state == null ? 0 : state.stacks;
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
            loaded.stacks = Math.max(0, Math.min(MAX_STACKS, nbt.getInt("stacks")));
            loaded.saveReadyAt = nbt.getLong("save_ready_at");
            long lastHit = nbt.getLong("last_hit");
            long now = GameTime.now(player);
            // 离线时间照算：真断了很久，层数该散就散；但至少留 2 秒缓冲
            loaded.lastHit = now - lastHit > DECAY_TICKS ? now - (DECAY_TICKS - REJOIN_GRACE) : lastHit;
        }
        STATES.put(player.getUuid(), loaded);
        // 读档回来的层数要立刻把攻击力加成挂回去，不然加成要等下一次挨打才生效
        if (loaded.stacks > 0) {
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
        nbt.putInt("stacks", state.stacks);
        nbt.putLong("last_hit", state.lastHit);
        nbt.putLong("save_ready_at", state.saveReadyAt);
        PlayerStore.put(player, STORE_KEY, nbt);
    }

    /** 层数变化后重新挂攻击力加成；0 层就是把修正器摘掉 */
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
        if (state == null || state.stacks <= 0) {
            return;
        }
        instance.addTemporaryModifier(new EntityAttributeModifier(
                MODIFIER_ID, "ws_soulbearer",
                state.stacks * DAMAGE_PER_STACK,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void playSaveEffect(ServerPlayerEntity player) {
        if (player.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.SOUL,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    28, 0.45D, 0.7D, 0.45D, 0.03D);
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE,
                    SoundCategory.PLAYERS, 1.0F, 0.7F);
        }
        player.sendMessage(Text.translatable("message.wangsword.soulbearer.save")
                .formatted(Formatting.LIGHT_PURPLE), true);
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return io.wispforest.accessories.api.AccessoriesCapability.getOptionally(player)
                .map(capability -> capability.isEquipped(ItemsRegistry.WS_NECKLACE_SOULBEARER))
                .orElse(false);
    }
}
