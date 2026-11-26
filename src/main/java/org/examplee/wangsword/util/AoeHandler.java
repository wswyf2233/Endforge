package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.ToolRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 「火力全开」模式：
 * 按 V 切换，开启后紫金 / 末地合金工具的挖掘、耕种变成范围作业。
 *
 * <p>区别：
 * <ul>
 *   <li>紫金：3×3</li>
 *   <li>末地合金：5×5，镐掉落 ×3，斧连树叶一并清除，另外自带
 *       「末地共鸣」——掉落物直接进背包，并有 10% 概率多掉一份</li>
 * </ul>
 */
public class AoeHandler {

    /** 客户端 -> 服务端：切换模式 */
    public static final Identifier MODE_PACKET = new Identifier("wangsword", "aoe_toggle");

    /** 已开启模式的玩家 */
    private static final Set<UUID> AOE_PLAYERS = new HashSet<>();

    public static void toggle(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        boolean on;
        if (AOE_PLAYERS.remove(id)) {
            on = false;
        } else {
            AOE_PLAYERS.add(id);
            on = true;
        }
        Text message = on
                ? Text.translatable("message.wangsword.aoe.on")
                : Text.translatable("message.wangsword.aoe.off");

        player.sendMessage(message, false);
        if (on) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 1.6F);
        } else {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_BEACON_DEACTIVATE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 1.2F);
        }
    }

    private static boolean isPurpleTool(ItemStack stack) {
        Item item = stack.getItem();
        return item == ToolRegistry.WS_PURPLE_PICKAXE
                || item == ToolRegistry.WS_PURPLE_AXE
                || item == ToolRegistry.WS_PURPLE_SHOVEL
                || item == ToolRegistry.WS_PURPLE_HOE;
    }

    private static boolean isTaiguTool(ItemStack stack) {
        Item item = stack.getItem();
        return item == ToolRegistry.WS_TAIGU_PICKAXE
                || item == ToolRegistry.WS_TAIGU_AXE
                || item == ToolRegistry.WS_TAIGU_SHOVEL
                || item == ToolRegistry.WS_TAIGU_HOE;
    }

    private static boolean isAoeTool(ItemStack stack) {
        return isPurpleTool(stack) || isTaiguTool(stack);
    }

    /** 末地共鸣：掉落物直接进背包，另有 10% 概率多掉一份 */
    private static void resonance(ServerWorld world, BlockPos pos, ServerPlayerEntity player, ItemStack tool) {
        Box area = new Box(pos).expand(3.0D);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, area,
                item -> item.isAlive() && item.getItemAge() <= 2);
        for (ItemEntity entity : items) {
            ItemStack stack = entity.getStack().copy();
            player.getInventory().insertStack(stack);
            if (stack.isEmpty()) {
                entity.discard();
            } else {
                entity.setStack(stack);
            }
        }
        if (world.random.nextFloat() < 0.10F) {
            List<ItemStack> drops = Block.getDroppedStacks(world.getBlockState(pos), world, pos, null, player, tool);
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    player.getInventory().insertStack(drop.copy());
                }
            }
        }
    }

    public static void init() {
        // 0. 斧：砍整棵树（在方块被破坏之前触发，最可靠）
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return true;

            ItemStack tool = serverPlayer.getMainHandStack();
            boolean isAxe = tool.getItem() == ToolRegistry.WS_PURPLE_AXE
                    || tool.getItem() == ToolRegistry.WS_TAIGU_AXE;
            if (!isAxe) return true;
            if (!state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return true;

            boolean taigu = isTaiguTool(tool);
            fellTree((ServerWorld) world, pos, serverPlayer, tool, taigu);
            return true;
        });
        // 1. 挖掘：范围破坏
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return;

            ItemStack tool = serverPlayer.getMainHandStack();
            if (!isAoeTool(tool)) return;

            boolean taigu = isTaiguTool(tool);
            int radius = taigu ? 2 : 1;

            if (AOE_PLAYERS.contains(serverPlayer.getUuid())) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos target = pos.add(dx, dy, dz);
                            BlockState targetState = world.getBlockState(target);
                            if (targetState.isAir()) continue;
                            if (targetState.getHardness(world, target) < 0.0F) continue;
                            if (!tool.isSuitableFor(targetState)) continue;

                            boolean isPickaxe = tool.getItem() == ToolRegistry.WS_PURPLE_PICKAXE
                                    || tool.getItem() == ToolRegistry.WS_TAIGU_PICKAXE;

                            if (isPickaxe) {
                                // 紫金镐：只对矿石生效；末地合金镐：所有能挖的方块都算
                                if (!taigu) {
                                    if (!targetState.isIn(net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags.ORES)) continue;
                                    if (!state.isIn(net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags.ORES)) continue;
                                }
                            } else {
                                if (targetState.getBlock() != state.getBlock()) continue;
                            }

                            if (taigu) {
                                List<ItemStack> drops = Block.getDroppedStacks(targetState, (ServerWorld) world,
                                        target, null, serverPlayer, tool);
                                world.breakBlock(target, false, serverPlayer);
                                for (ItemStack drop : drops) {
                                    if (!drop.isEmpty()) {
                                        serverPlayer.getInventory().insertStack(drop.copy());
                                    }
                                }
                            } else {
                                world.breakBlock(target, true, serverPlayer);
                            }
                        }
                    }
                }
            }

            // 镐：矿石掉落翻倍（紫金再 ×2，末地合金 ×3）
            boolean isPickaxe = tool.getItem() == ToolRegistry.WS_PURPLE_PICKAXE
                    || tool.getItem() == ToolRegistry.WS_TAIGU_PICKAXE;
            if (isPickaxe && state.isIn(net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags.ORES)) {
                int extraCopies = taigu ? 2 : 1;
                if (world instanceof ServerWorld serverWorld) {
                    List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, null, serverPlayer, tool);
                    for (int i = 0; i < extraCopies; i++) {
                        for (ItemStack drop : drops) {
                            if (drop.isEmpty()) continue;
                            if (taigu) {
                                serverPlayer.getInventory().insertStack(drop.copy());
                            } else {
                                Block.dropStack(world, pos, drop.copy());
                            }
                        }
                    }
                }
            }


            // 末地共鸣：掉落进背包 + 10% 额外
            if (taigu) {
                resonance((ServerWorld) world, pos, serverPlayer, tool);
            }
        });

        // 2. 耕种：范围翻地；末地合金锄额外支持范围收割
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            ItemStack tool = serverPlayer.getStackInHand(hand);
            if (!(tool.getItem() instanceof HoeItem)) return ActionResult.PASS;

            boolean taigu = isTaiguTool(tool);
            if (!taigu && !isPurpleTool(tool)) return ActionResult.PASS;

            int radius = taigu ? 2 : 1;
            BlockPos center = hitResult.getBlockPos();
            ServerWorld serverWorld = (ServerWorld) world;

            // 末地合金锄：右键成熟作物 → 范围收割
            if (taigu) {
                BlockState centerState = serverWorld.getBlockState(center);
                if (centerState.getBlock() instanceof CropBlock crop && crop.isMature(centerState)) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            BlockPos cropPos = center.add(dx, 0, dz);
                            BlockState cropState = serverWorld.getBlockState(cropPos);
                            if (!(cropState.getBlock() instanceof CropBlock other) || !other.isMature(cropState)) continue;
                            List<ItemStack> drops = Block.getDroppedStacks(cropState, serverWorld, cropPos,
                                    null, serverPlayer, tool);
                            serverWorld.breakBlock(cropPos, false, serverPlayer);
                            for (ItemStack drop : drops) {
                                if (!drop.isEmpty()) {
                                    serverPlayer.getInventory().insertStack(drop.copy());
                                }
                            }
                        }
                    }
                    return ActionResult.SUCCESS;
                }
            }

            if (!AOE_PLAYERS.contains(serverPlayer.getUuid())) return ActionResult.PASS;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = center.add(dx, 0, dz);
                    if (canTill(serverWorld, p)) {
                        serverWorld.setBlockState(p, Blocks.FARMLAND.getDefaultState());
                    }
                }
            }
            return ActionResult.PASS;
        });
    }

    /**
     * 砍整棵树：从起始木头出发做连通搜索，把连着的原木全部挖掉，
     * 再把附近和这些木头相连的树叶一起清掉。有数量上限，防止大型建筑卡死。
     */
    private static void fellTree(ServerWorld world, BlockPos origin, ServerPlayerEntity player,
                                 ItemStack tool, boolean taigu) {
        final int maxLogs = 256;
        final int maxLeaves = taigu ? 800 : 400;
        final int leafRadius = taigu ? 3 : 2;

        Set<BlockPos> logs = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(origin);
        logs.add(origin);

        while (!queue.isEmpty() && logs.size() < maxLogs) {
            BlockPos current = queue.poll();
            for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                BlockPos next = current.offset(dir);
                if (logs.contains(next)) continue;
                if (!world.isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)) continue;
                BlockState nextState = world.getBlockState(next);
                if (!nextState.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) continue;
                logs.add(next);
                queue.add(next);
                if (logs.size() >= maxLogs) break;
            }
        }

        // 先挖木头
        for (BlockPos logPos : logs) {
            if (logPos.equals(origin)) continue;
            world.breakBlock(logPos, true, player);
        }

        // 再清树叶
        int leaves = 0;
        Set<BlockPos> checked = new HashSet<>();
        for (BlockPos logPos : logs) {
            for (int dx = -leafRadius; dx <= leafRadius; dx++) {
                for (int dy = -1; dy <= leafRadius; dy++) {
                    for (int dz = -leafRadius; dz <= leafRadius; dz++) {
                        BlockPos leafPos = logPos.add(dx, dy, dz);
                        if (!checked.add(leafPos)) continue;
                        if (!world.isChunkLoaded(leafPos.getX() >> 4, leafPos.getZ() >> 4)) continue;
                        BlockState leafState = world.getBlockState(leafPos);
                        if (!leafState.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) continue;
                        world.breakBlock(leafPos, true, player);
                        if (++leaves >= maxLeaves) return;
                    }
                }
            }
        }
    }

    private static boolean canTill(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.DIRT) && !state.isOf(Blocks.GRASS_BLOCK)
                && !state.isOf(Blocks.DIRT_PATH) && !state.isOf(Blocks.COARSE_DIRT)) {
            return false;
        }
        return world.getBlockState(pos.up()).isAir();
    }
}
