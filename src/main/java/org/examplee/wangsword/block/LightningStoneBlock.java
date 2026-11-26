package org.examplee.wangsword.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 雷石：未激活雷电宝石放置后的形态。
 *
 * <p>雷暴天放置时会立刻引雷劈自己，方块消失并掉落激活的雷电宝石；
 * 非雷暴天气放置则只是摆在地上，破坏可取回。
 */
public class LightningStoneBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(3.0, 0.0, 3.0, 13.0, 13.0, 13.0);

    public LightningStoneBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world.isClient() || oldState.isOf(this)) {
            return;
        }
        if (world.getRegistryKey() != World.OVERWORLD || !world.isThundering()) {
            return;
        }
        activate(world, pos);
    }

    /** 落雷 + 换掉落物 */
    private void activate(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld)) {
            return;
        }
        world.removeBlock(pos, false);

        // 先落雷，再生成掉落物：否则掉落物会被落雷引起的火直接烧毁
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(pos));
            world.spawnEntity(bolt);
        }

        ItemEntity drop = new ItemEntity(world,
                pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D,
                new ItemStack(ItemsRegistry.WS_LIGHTNINGSTONE));
        drop.setVelocity(0.0D, 0.1D, 0.0D);
        // 免伤，避免被火/爆炸清掉；同时给它留出落地时间
        drop.setInvulnerable(true);
        drop.setPickupDelay(20);
        world.spawnEntity(drop);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, net.minecraft.world.BlockView world,
                                      BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.BlockView world,
                                        BlockPos pos, ShapeContext context) {
        return net.minecraft.util.shape.VoxelShapes.empty();
    }
}
