package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.registry.ItemsRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 超级雷电宝石的「手持带电」。
 *
 * <ol>
 *   <li>拿在主手或副手时，每秒受 1 点雷伤——刚上手的 1 秒有宽限，免得切物品栏时被白扣血；</li>
 *   <li>如果此时人在室外（头顶见天）而且正在雷雨，<b>必被真雷劈</b>，每 5 秒一发。</li>
 * </ol>
 *
 * <p>「室外」用 {@code isSkyVisible} 判定：站在屋檐下、洞里、水里都不算，
 * 所以把宝石放箱子里搬回家是安全的，在旷野雷雨里举着它就是找死。
 *
 * <p>时间全部走所在世界的 {@code getTime()}，跨维度不会错位；
 * 一旦松手就把两条计时都清掉，再拿起来重新给 1 秒宽限。
 */
public final class SuperLightningGemHandler {

    /** 手持每秒扣的血量（1 点 = 半颗心） */
    public static final float HOLD_DAMAGE = 1.0F;
    /** 扣血间隔：1 秒 */
    public static final int HOLD_INTERVAL = 20;
    /** 雷雨天的落雷间隔：5 秒 */
    public static final int STRIKE_INTERVAL = 100;
    /** 刚暴露在雷雨里的第一发：2 秒后就来，让玩家马上明白「这玩意儿不能拿着淋雨」 */
    public static final int STRIKE_FIRST_DELAY = 40;
    /** 电弧粒子间隔：0.2 秒 */
    public static final int SPARK_INTERVAL = 4;

    /** 下一次扣血的时刻（按世界时间） */
    private static final Map<UUID, Long> NEXT_DAMAGE = new HashMap<>();
    /** 下一次落雷的时刻（按世界时间） */
    private static final Map<UUID, Long> NEXT_STRIKE = new HashMap<>();
    /** 下一次冒电花的时刻 */
    private static final Map<UUID, Long> NEXT_SPARK = new HashMap<>();

    private SuperLightningGemHandler() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SuperLightningGemHandler::onTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
    }

    private static void onTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();

            if (!isHolding(player)) {
                forget(id);
                continue;
            }
            if (player.isDead() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            if (!(player.getWorld() instanceof ServerWorld world)) {
                continue;
            }

            long now = world.getTime();

            // 电花：不管扣没扣血，拿着就一直在跳
            long sparkAt = orInit(NEXT_SPARK, id, now + SPARK_INTERVAL);
            if (now >= sparkAt) {
                NEXT_SPARK.put(id, now + SPARK_INTERVAL);
                spark(world, player);
            }

            // 每秒放电
            long damageAt = orInit(NEXT_DAMAGE, id, now + HOLD_INTERVAL);
            if (now >= damageAt) {
                NEXT_DAMAGE.put(id, now + HOLD_INTERVAL);
                player.timeUntilRegen = 0;
                player.damage(world.getDamageSources().lightningBolt(), HOLD_DAMAGE);
            }

            // 室外雷雨天：必被雷劈
            if (!isStormExposed(world, player)) {
                NEXT_STRIKE.remove(id);
                continue;
            }
            long strikeAt = orInit(NEXT_STRIKE, id, now + STRIKE_FIRST_DELAY);
            if (now >= strikeAt) {
                NEXT_STRIKE.put(id, now + STRIKE_INTERVAL);
                strike(world, player);
            }
        }
    }

    /** 主手或副手任意一只手拿着就成立 */
    private static boolean isHolding(ServerPlayerEntity player) {
        return player.getMainHandStack().getItem() == ItemsRegistry.WS_SUPER_LIGHTNINGSTONE
                || player.getOffHandStack().getItem() == ItemsRegistry.WS_SUPER_LIGHTNINGSTONE;
    }

    /** 室外 + 雷雨：头顶见天，且这一维正在打雷 */
    private static boolean isStormExposed(ServerWorld world, ServerPlayerEntity player) {
        return world.isThundering() && world.isSkyVisible(player.getBlockPos());
    }

    /** 手里那颗宝石往外炸火花 */
    private static void spark(ServerWorld world, ServerPlayerEntity player) {
        Vec3d front = player.getEyePos().add(player.getRotationVec(1.0F).multiply(0.75D));
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                front.x, front.y - 0.35D, front.z, 2, 0.10D, 0.12D, 0.10D, 0.02D);
    }

    /** 真雷。劈在玩家脚下，会点燃地面、充能苦力怕——这就是「必被雷劈」的代价 */
    private static void strike(ServerWorld world, ServerPlayerEntity player) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(player.getX(), player.getY(), player.getZ());
            world.spawnEntity(bolt);
        }
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.5D, 0.8D, 0.5D, 0.18D);
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER,
                SoundCategory.PLAYERS, 1.4F, 0.9F);
    }

    private static long orInit(Map<UUID, Long> map, UUID id, long fallback) {
        Long value = map.get(id);
        if (value == null) {
            map.put(id, fallback);
            return fallback;
        }
        return value;
    }

    private static void forget(UUID id) {
        NEXT_DAMAGE.remove(id);
        NEXT_STRIKE.remove(id);
        NEXT_SPARK.remove(id);
    }
}
