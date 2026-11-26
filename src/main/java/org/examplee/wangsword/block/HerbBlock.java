package org.examplee.wangsword.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * 药草：四阶生长的草状植物。
 *
 * <p>age 0 = 幼苗，3 = 成熟。随机刻推进一阶（需要光照 ≥ 9），骨粉也能催一阶。
 * 四阶共用同一张贴图，靠模型面片尺寸做出"从小长到大"的效果。
 * 挖掉时实际得到什么由 {@code loot_tables/blocks/ws_herb.json} 按 age 决定。
 */
public class HerbBlock extends PlantBlock implements Fertilizable {

    public static final int MAX_AGE = 3;
    public static final IntProperty AGE = IntProperty.of("age", 0, MAX_AGE);

    /** 随机刻推进一阶的概率：1/10 */
    private static final int GROW_CHANCE = 10;

    /** 生长所需最低光照 */
    private static final int MIN_LIGHT = 9;

    /** 各阶轮廓箱，只用于选取判定 */
    private static final VoxelShape[] SHAPES = new VoxelShape[]{
            Block.createCuboidShape(4.0D, 0.0D, 4.0D, 12.0D, 6.0D, 12.0D),
            Block.createCuboidShape(3.0D, 0.0D, 3.0D, 13.0D, 10.0D, 13.0D),
            Block.createCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D),
            Block.createCuboidShape(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D)
    };

    public HerbBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[Math.min(state.get(AGE), MAX_AGE)];
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(AGE) >= MAX_AGE) {
            return;
        }
        if (world.getLightLevel(pos, 0) < MIN_LIGHT) {
            return;
        }
        if (random.nextInt(GROW_CHANCE) != 0) {
            return;
        }
        world.setBlockState(pos, state.with(AGE, state.get(AGE) + 1), Block.NOTIFY_ALL);
    }

    // ---------------------------------------------------------------- 骨粉

    @Override
    public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state, boolean isClient) {
        return state.get(AGE) < MAX_AGE;
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
        if (state.get(AGE) < MAX_AGE) {
            world.setBlockState(pos, state.with(AGE, state.get(AGE) + 1), Block.NOTIFY_ALL);
        }
    }

    @Override
    protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        return floor.isOf(Blocks.GRASS_BLOCK)
                || floor.isOf(Blocks.DIRT)
                || floor.isOf(Blocks.COARSE_DIRT)
                || floor.isOf(Blocks.ROOTED_DIRT)
                || floor.isOf(Blocks.PODZOL)
                || floor.isOf(Blocks.MOSS_BLOCK)
                || floor.isOf(Blocks.FARMLAND);
    }
}
