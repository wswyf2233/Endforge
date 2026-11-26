package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.GhostCrownWraithEntity;
import org.examplee.wangsword.registry.EntityRegistry;

/**
 * 击杀过剑冢守卫之后，裂冠幽灵开始在主世界地表的夜里出现。
 *
 * <p>没有往生物群系的刷怪表里塞，而是自己按 tick 挑点刷新。原因有两个：
 * 一是"必须击杀过守卫"是动态条件，刷怪表是静态的，塞进去也只能在 SpawnRestriction
 * 里拒绝，而结构刷怪和地表刷怪共用同一个 restriction，会把剑冢里的刷新一起挡掉；
 * 二是自己刷能顺便控制同类数量，不会一夜之间刷出一群。
 */
public final class GhostNightSpawner {

    /** 每多少 tick 尝试一次（2 秒） */
    private static final int INTERVAL = 40;
    /** 离玩家多近开始找位置 */
    private static final double MIN_DISTANCE = 24.0D;
    /** 最远找到多远 */
    private static final double MAX_DISTANCE = 44.0D;
    /** 每轮随机试几个点 */
    private static final int ATTEMPTS = 4;
    /** 16 格内同类上限 */
    private static final int MAX_NEARBY = 2;

    private GhostNightSpawner() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(GhostNightSpawner::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
            return;
        }
        if (world.getTime() % INTERVAL != 0) {
            return;
        }
        // 只在夜里
        if (world.isDay()) {
            return;
        }
        if (!WraithProgress.get(world).isGuardianSlain()) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            // 不是每个玩家每轮都刷，节奏慢一点
            if (world.random.nextInt(3) != 0) {
                continue;
            }
            spawnAround(world, player);
        }
    }

    private static void spawnAround(ServerWorld world, ServerPlayerEntity player) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0D;
            double distance = MIN_DISTANCE + world.random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = MathHelper.floor(player.getX() + Math.cos(angle) * distance);
            int z = MathHelper.floor(player.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }

            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            // 要两个空气格的高度，不然 1.9 格高的模型会卡进方块
            if (!world.isAir(pos) || !world.isAir(pos.up())) {
                continue;
            }
            BlockPos below = pos.down();
            if (!world.getBlockState(below).isSolidBlock(world, below)) {
                continue;
            }
            // 原版夜晚地表：方块光要暗，而且头顶能看见天
            if (world.getLightLevel(LightType.BLOCK, pos) > 7) {
                continue;
            }
            if (!world.isSkyVisible(pos)) {
                continue;
            }
            if (world.getClosestPlayer(x + 0.5D, y, z + 0.5D, MIN_DISTANCE, false) != null) {
                continue;
            }
            if (countNearby(world, pos) >= MAX_NEARBY) {
                continue;
            }

            GhostCrownWraithEntity wraith =
                    new GhostCrownWraithEntity(EntityRegistry.GHOST_CROWN_WRAITH, world);
            wraith.refreshPositionAndAngles(x + 0.5D, y, z + 0.5D,
                    world.random.nextFloat() * 360.0F, 0.0F);
            wraith.initialize(world, world.getLocalDifficulty(pos), SpawnReason.NATURAL, null, null);
            world.spawnEntity(wraith);
            return;
        }
    }

    private static int countNearby(ServerWorld world, BlockPos pos) {
        return world.getEntitiesByType(
                TypeFilter.instanceOf(GhostCrownWraithEntity.class),
                new Box(pos).expand(16.0D),
                living -> living.isAlive()).size();
    }
}
