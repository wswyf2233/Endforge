package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.SoundRegistry;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 守誓之阵：剑冢 · 守誓的 X 键大招。
 *
 * <p>把剑插进地里，以脚下为圆心撑开一圈 8 格的剑冢：
 * <ul>
 *   <li>阵内非玩家生物每 0.5 秒吃 3 点魔法伤害，并被压到缓慢 II；</li>
 *   <li>阵内玩家（不分敌我，自己人也能吃到）获得抗性 II，即 40% 减伤，正好补上
 *       "举剑格挡时没法移动"的那点代价；</li>
 *   <li>10 秒后阵法自行熄灭，45 秒后才能再插一次。</li>
 * </ul>
 *
 * <p>状态只存在内存里：结界最长 10 秒，重登丢失不构成体验问题，
 * 也就不必往 {@link PlayerStore} 里塞一份短命数据。冷却同样只走这张表，
 * 不碰原版物品冷却条——那把剑的右键是格挡，冷却条被大招占用会让玩家以为盾坏了。
 */
public class OathBarrierHandler {

    /** 持续 10 秒 */
    public static final int DURATION = 200;
    /** 冷却 45 秒 */
    public static final int COOLDOWN = 900;
    /** 阵半径（格） */
    public static final double RADIUS = 8.0D;

    /** 每次脉冲的伤害 */
    private static final float PULSE_DAMAGE = 3.0F;
    /** 脉冲间隔：0.5 秒 */
    private static final int PULSE_INTERVAL = 10;
    /** 缓慢持续时间：3 秒，留出被压制的余量 */
    private static final int SLOWNESS_TICKS = 60;
    /** 抗性刷新时长：2 秒，每 0.5 秒补一次，断了也不会闪 */
    private static final int RESISTANCE_TICKS = 40;

    private static final Map<UUID, Barrier> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_END = new HashMap<>();

    /** 一处正在燃烧的剑冢 */
    private static final class Barrier {
        final RegistryKey<World> world;
        final Vec3d center;
        final long endTick;

        Barrier(RegistryKey<World> world, Vec3d center, long endTick) {
            this.world = world;
            this.center = center;
            this.endTick = endTick;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(OathBarrierHandler::onServerTick);
        // 掉线清冷却和阵法，避免重连后残留一个看不见的阵还在打人
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            ACTIVE.remove(id);
            COOLDOWN_END.remove(id);
        });
    }

    // ================== 【释放】 ==================

    public static void cast(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        long now = world.getTime();

        Long until = COOLDOWN_END.get(player.getUuid());
        if (until != null && now < until) {
            int left = (int) Math.ceil((until - now) / 20.0D);
            player.sendMessage(Text.translatable("message.wangsword.oath.cd", left), true);
            return;
        }

        Vec3d center = player.getPos();
        ACTIVE.put(player.getUuid(), new Barrier(world.getRegistryKey(), center, now + DURATION));
        COOLDOWN_END.put(player.getUuid(), now + COOLDOWN);

        // 起手：脚下荡开两圈魂火 + 一圈冲击，配插剑的钝响
        for (int ring = 0; ring < 2; ring++) {
            double r = 0.8D + ring * 0.8D;
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48.0D;
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        center.x + Math.cos(a) * r, center.y + 0.15D + ring * 0.1D,
                        center.z + Math.sin(a) * r,
                        1, 0.0D, 0.02D, 0.0D, 0.0D);
            }
        }
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                center.x, center.y + 1.0D, center.z, 6, 1.4D, 0.15D, 1.4D, 0.0D);
        ring(world, center, RADIUS, 64);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_PLACE,
                SoundCategory.PLAYERS, 1.3F, 0.55F);
        world.playSound(null, player.getBlockPos(), SoundRegistry.SLASH_IMPACT,
                SoundCategory.PLAYERS, 1.1F, 0.7F);
        player.sendMessage(Text.translatable("message.wangsword.oath.cast"), true);
    }

    // ================== 【每 tick 维护】 ==================

    private static void onServerTick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Barrier>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Barrier> entry = iterator.next();
            Barrier barrier = entry.getValue();
            ServerWorld world = server.getWorld(barrier.world);
            if (world == null) {
                iterator.remove();
                continue;
            }

            long now = world.getTime();
            if (now >= barrier.endTick) {
                collapse(world, barrier.center);
                iterator.remove();
                continue;
            }

            // 边界面子：每 5 tick 描一圈，读作"阵法还在"
            if (now % 5L == 0L) {
                ring(world, barrier.center, RADIUS, 48);
            }
            if (now % PULSE_INTERVAL == 0L) {
                pulse(world, barrier.center);
            }
        }
    }

    /** 一次脉冲：阵内生物掉血 + 缓慢，阵内玩家上抗性 */
    private static void pulse(ServerWorld world, Vec3d center) {
        Box box = Box.from(center).expand(RADIUS, 4.0D, RADIUS);
        double radiusSq = RADIUS * RADIUS;

        // 只惩罚敌对生物：村民、动物、盔甲架、其他玩家的宠物在阵里站着不会掉血
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                CombatTargets::isHostile)) {
            if (target.getPos().squaredDistanceTo(center) > radiusSq) {
                continue;
            }
            target.damage(world.getDamageSources().magic(), PULSE_DAMAGE);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    SLOWNESS_TICKS, 1, false, false, true));
            world.spawnParticles(ParticleTypes.SOUL,
                    target.getX(), target.getY() + target.getHeight() * 0.5D, target.getZ(),
                    6, 0.2D, 0.2D, 0.2D, 0.02D);
        }

        for (PlayerEntity player : world.getPlayers()) {
            if (player.getPos().squaredDistanceTo(center) <= radiusSq) {
                // 抗性 II = 40% 减伤，和设计里的"阵内站得住"对齐
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,
                        RESISTANCE_TICKS, 1, false, false, true));
            }
        }
    }

    /** 在半径上画一圈魂火 */
    private static void ring(ServerWorld world, Vec3d center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    center.x + Math.cos(a) * radius, center.y + 0.12D, center.z + Math.sin(a) * radius,
                    1, 0.0D, 0.01D, 0.0D, 0.0D);
        }
    }

    /** 熄灭：从边到心收一圈，配一声闷响 */
    private static void collapse(ServerWorld world, Vec3d center) {
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2 * i / 64.0D;
            world.spawnParticles(ParticleTypes.SOUL,
                    center.x + Math.cos(a) * RADIUS, center.y + 0.2D, center.z + Math.sin(a) * RADIUS,
                    1, 0.0D, 0.06D, 0.0D, 0.01D);
        }
        world.spawnParticles(ParticleTypes.END_ROD,
                center.x, center.y + 0.6D, center.z, 18, 0.6D, 0.4D, 0.6D, 0.03D);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_DEACTIVATE,
                SoundCategory.PLAYERS, 0.9F, 0.6F);
    }
}
