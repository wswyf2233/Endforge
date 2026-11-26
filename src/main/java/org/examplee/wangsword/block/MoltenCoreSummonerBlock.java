package org.examplee.wangsword.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.BlocksRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * 熔核唤石：立在熔核沉坛中央的那块石头，负责召唤这一层的 BOSS。
 *
 * <p>方块本体和祭坛一样是「空模型 + GeckoLib 渲染」：blockstate 指向一个没有
 * elements 的占位模型，屏幕上看到的外形完全由 {@code molten_core_summoner.geo.json}
 * 画出来，所以能播待机脉动和召唤脉动。
 *
 * <p>硬度 -1，和基岩同档，生存模式挖不掉。它是结构的固定节点，被搬走就失去意义。
 */
public class MoltenCoreSummonerBlock extends BlockWithEntity implements BlockEntityProvider {

    private static final VoxelShape SHAPE = VoxelShapes.fullCube();

    public MoltenCoreSummonerBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MoltenCoreSummonerBlockEntity(pos, state);
    }

    /** 方块本体走 blockstate 里的空模型，外形交给 GeckoLib */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return checkType(type, BlocksRegistry.MOLTEN_CORE_SUMMONER_BE, MoltenCoreSummonerBlockEntity::tick);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /**
     * 右键献祭熔核残息，播放召唤脉动。
     *
     * <p>手持的不是熔核残息时只给一条提示，不触发动画、不消耗任何东西。
     * 消耗一枚熔核残息，放熔核巨兽（{@code cinder_colossus}）出来。
     * 同一座沉坛 32 格内已有存活的巨兽时不会叠第二只。
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.PASS;
        }
        if (!(world.getBlockEntity(pos) instanceof MoltenCoreSummonerBlockEntity core)) {
            return ActionResult.PASS;
        }

        ItemStack held = player.getStackInHand(hand);
        if (!held.isOf(org.examplee.wangsword.registry.ItemsRegistry.WS_MOLTEN_BREATH)) {
            player.sendMessage(Text.translatable("message.wangsword.molten_core.need_breath"), true);
            return ActionResult.SUCCESS;
        }
        if (core.isSummoning()) {
            return ActionResult.SUCCESS;
        }

        if (!player.isCreative()) {
            held.decrement(1);
        }
        core.beginSummon();
        summon(serverWorld, pos);
        return ActionResult.SUCCESS;
    }

    /** 召唤本体：放熔核巨兽出来，再补音效和粒子 */
    private void summon(ServerWorld world, BlockPos pos) {
        // 同一座沉坛不叠第二只：32 格内已经有同类就只做特效
        boolean already = !world.getEntitiesByClass(
                org.examplee.wangsword.entity.CinderColossusEntity.class,
                new net.minecraft.util.math.Box(pos).expand(32.0D),
                e -> e.isAlive()).isEmpty();

        if (!already) {
            org.examplee.wangsword.entity.CinderColossusEntity boss =
                    org.examplee.wangsword.registry.EntityRegistry.CINDER_COLOSSUS.create(world);
            if (boss != null) {
                double x = pos.getX() + 0.5D;
                double y = pos.getY() + 1.0D;
                double z = pos.getZ() + 0.5D;
                boss.refreshPositionAndAngles(x, y, z, world.random.nextFloat() * 360.0F, 0.0F);
                boss.setPersistent();
                world.spawnEntity(boss);
                // 一层火柱，遮住"凭空出现"的那一帧
                world.spawnParticles(ParticleTypes.FLAME,
                        x, y + 1.0D, z, 90, 0.9D, 1.4D, 0.9D, 0.06D);
            }
        }

        world.playSound(null, pos, SoundEvents.ENTITY_BLAZE_SHOOT,
                SoundCategory.BLOCKS, 1.0F, 0.7F);
        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN,
                SoundCategory.HOSTILE, 0.5F, 0.6F);
        world.spawnParticles(ParticleTypes.FLAME,
                pos.getX() + 0.5D, pos.getY() + 0.55D, pos.getZ() + 0.5D,
                40, 0.6D, 0.25D, 0.6D, 0.02D);
        world.spawnParticles(ParticleTypes.LAVA,
                pos.getX() + 0.5D, pos.getY() + 0.55D, pos.getZ() + 0.5D,
                12, 0.4D, 0.15D, 0.4D, 0.0D);
    }
}
