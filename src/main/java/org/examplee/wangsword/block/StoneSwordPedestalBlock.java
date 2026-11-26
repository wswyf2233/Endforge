package org.examplee.wangsword.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 石中剑基座：台座 + 一把斜插着的剑，空手右键把剑拔出来。
 *
 * <p>剑本体<b>不是</b>方块模型里的元素，而是由 {@link StoneSwordPedestalBlockEntity} 存一件
 * {@link ItemStack}，交给客户端渲染器用原版物品模型画出来。这样剑有真实厚度和完整轮廓，
 * 换武器只要改方块实体里的物品，不用重画任何贴图或改模型 JSON。
 *
 * <p>只有两个状态：{@code has_item} 为 true 时插着剑，拔出后切成 false，
 * 之后再右键没有任何反应（一次性）。
 */
public class StoneSwordPedestalBlock extends BlockWithEntity implements BlockEntityProvider {

    public static final BooleanProperty HAS_ITEM = BooleanProperty.of("has_item");

    /** 有剑时：台座到 8 像素（中心石台） */
    private static final VoxelShape SHAPE_FULL = Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    /** 拔掉之后只剩台座 + 四角金柱，7 像素高 */
    private static final VoxelShape SHAPE_EMPTY = Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 7.0D, 16.0D);

    public StoneSwordPedestalBlock(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HAS_ITEM, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HAS_ITEM);
    }

    // ---------------------------------------------------------------- 渲染 / 实体

    /** 台座本身仍然走普通 JSON 模型，剑由方块实体渲染器补上 */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StoneSwordPedestalBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- 形状

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(HAS_ITEM) ? SHAPE_FULL : SHAPE_EMPTY;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(HAS_ITEM) ? SHAPE_FULL : SHAPE_EMPTY;
    }

    /**
     * 放下来默认是「插着剑」的状态。
     *
     * <p>方块物品放置走的是 {@code getDefaultState()}，默认值是 false，
     * 所以直接摆出来永远是空台座。空状态只能由「拔出」产生。
     */
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(HAS_ITEM, true);
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (!state.get(HAS_ITEM)) {
            return ActionResult.PASS;
        }
        if (!player.getStackInHand(hand).isEmpty()) {
            return ActionResult.PASS;
        }
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.PASS;
        }
        if (!(world.getBlockEntity(pos) instanceof StoneSwordPedestalBlockEntity pedestal)) {
            return ActionResult.PASS;
        }

        ItemStack taken = pedestal.getStoredItem();
        if (taken.isEmpty()) {
            return ActionResult.PASS;
        }

        player.getInventory().offerOrDrop(taken.copy());
        pedestal.setStoredItem(ItemStack.EMPTY);
        serverWorld.setBlockState(pos, state.with(HAS_ITEM, false), Block.NOTIFY_ALL);
        serverWorld.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.BLOCKS, 1.0F, 0.9F);
        serverWorld.spawnParticles(ParticleTypes.CRIT,
                pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D,
                14, 0.3D, 0.35D, 0.3D, 0.08D);
        return ActionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- 破坏

    /**
     * 有剑时把方块实体里那件物品单独掉出来。
     *
     * <p>战利品表只能读到方块状态、读不到方块实体里的 {@link ItemStack}，
     * 所以物品这部分必须在这里手动掉；战利品表那边只负责掉台座本身。
     */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world instanceof ServerWorld
                    && world.getBlockEntity(pos) instanceof StoneSwordPedestalBlockEntity pedestal) {
                ItemStack stored = pedestal.getStoredItem();
                if (!stored.isEmpty()) {
                    ItemEntity drop = new ItemEntity(world, pos.getX() + 0.5D, pos.getY() + 0.4D,
                            pos.getZ() + 0.5D, stored);
                    drop.setToDefaultPickupDelay();
                    world.spawnEntity(drop);
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
