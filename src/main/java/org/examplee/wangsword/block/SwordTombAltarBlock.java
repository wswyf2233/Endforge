package org.examplee.wangsword.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.SwordTombGuardianEntity;
import org.examplee.wangsword.registry.BlocksRegistry;
import org.examplee.wangsword.registry.EntityRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * 剑冢祭坛：把钻石剑献祭上去，祭坛自己运转 3 秒，然后从法阵里升起一只剑冢守卫。
 *
 * <p>规则：
 * <ul>
 *   <li>必须手持<b>钻石剑</b>右键，剑会被消耗（创造模式不消耗）；</li>
 *   <li>祭坛上方要有 5 格净空，守卫本体 4.4 格高，塞不下就不召唤；</li>
 *   <li>周围 24 格内已经有活着的守卫时不再召唤，避免一坛刷成一群；</li>
 *   <li>召唤出来的是<b>石化状态</b>的石像：创造模式/旁观者不会触发苏醒，
 *       生存玩家靠近 10 格内且能看到它时才会播放 awake 动画醒来，打它也会醒。</li>
 *   <li>动画是 GeckoLib 的方块动画（idle 循环 + summon 一次），守卫在 summon 播完后才出现。</li>
 * </ul>
 */
public class SwordTombAltarBlock extends Block implements BlockEntityProvider {

    /** 守卫身高 4.4 格，上方要给够 5 格才站得下 */
    private static final int HEADROOM = 5;
    /** 同一座祭坛附近已有守卫时的判定半径 */
    private static final double OCCUPIED_RANGE = 24.0D;

    public SwordTombAltarBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SwordTombAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (world.isClient() || !BlocksRegistry.SWORD_TOMB_ALTAR_BE.equals(type)) {
            return null;
        }
        return (tickWorld, tickPos, tickState, blockEntity) ->
                SwordTombAltarBlockEntity.tick(tickWorld, tickPos, tickState,
                        (SwordTombAltarBlockEntity) blockEntity);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                              BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(Items.DIAMOND_SWORD)) {
            // 手上不是钻石剑：交互让回给原版，只顺手给一条手势提示
            if (!world.isClient()) {
                hintOffering(player, hand, stack);
            }
            return ActionResult.PASS;
        }
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof SwordTombAltarBlockEntity altar)) {
            return ActionResult.SUCCESS;
        }
        if (altar.isSummoning()) {
            player.sendMessage(Text.translatable("message.wangsword.sword_tomb_altar.busy")
                    .formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        // 头顶那一格本来是守卫死后长出石中剑基座的位置。基座一旦留在原处，
        // hasHeadroom 第一条就过不去，这座祭坛从此再也召不出守卫。
        // 所以先处理它：有剑让玩家自己拔，空石座由祭坛吞掉（掉落石座本体）。
        BlockPos above = pos.up();
        BlockState aboveState = world.getBlockState(above);
        if (aboveState.isOf(BlocksRegistry.STONE_SWORD_PEDESTAL)) {
            if (aboveState.get(StoneSwordPedestalBlock.HAS_ITEM)) {
                player.sendMessage(Text.translatable("message.wangsword.sword_tomb_altar.take_sword")
                        .formatted(Formatting.RED), true);
                return ActionResult.SUCCESS;
            }
            // 上面几格要是有别的方块，先别急着吞石座，不然白拆一次
            if (!hasHeadroom(world, pos, 2)) {
                player.sendMessage(Text.translatable("message.wangsword.sword_tomb_altar.no_room")
                        .formatted(Formatting.RED), true);
                return ActionResult.SUCCESS;
            }
            world.breakBlock(above, true, player);
            world.playSound(null, above, SoundEvents.BLOCK_STONE_BREAK,
                    SoundCategory.BLOCKS, 1.0F, 0.7F);
            serverWorld.spawnParticles(ParticleTypes.CLOUD,
                    above.getX() + 0.5D, above.getY() + 0.35D, above.getZ() + 0.5D,
                    16, 0.35D, 0.15D, 0.35D, 0.03D);
            player.sendMessage(Text.translatable("message.wangsword.sword_tomb_altar.absorb_pedestal")
                    .formatted(Formatting.GRAY), true);
        }

        if (!hasHeadroom(world, pos)) {
            player.sendMessage(Text.translatable("message.wangsword.sword_tomb_altar.no_room")
                    .formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        if (hasGuardianNearby(serverWorld, pos)) {
            player.sendMessage(Text.translatable("message.wangsword.sword_tomb_altar.occupied")
                    .formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }

        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        altar.beginSummon();
        // 起手：法阵亮起来，声音压低，给 3 秒动画起个头
        world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.BLOCKS, 1.2F, 0.65F);
        world.playSound(null, pos, SoundEvents.ENTITY_EVOKER_CAST_SPELL,
                SoundCategory.HOSTILE, 1.0F, 0.6F);
        return ActionResult.SUCCESS;
    }

    /**
     * 手上不是钻石剑时给一条动作栏提示。
     *
     * <p>只在<b>空手</b>或<b>拿着别的剑</b>时才说话——拿方块/食物点祭坛很可能是想放置或正常交互，
     * 每次都弹字会很烦。另外原版交互会先走主手、失败再走副手，所以这里只在主手判定，
     * 并且副手已经握着钻石剑时闭嘴，交给副手那一次真正去召唤。
     */
    private static void hintOffering(PlayerEntity player, Hand hand, ItemStack stack) {
        if (hand != Hand.MAIN_HAND) {
            return;
        }
        if (player.getStackInHand(Hand.OFF_HAND).isOf(Items.DIAMOND_SWORD)) {
            return;
        }
        boolean emptyHanded = stack.isEmpty();
        boolean otherSword = stack.getItem() instanceof SwordItem;
        if (!emptyHanded && !otherSword) {
            return;
        }
        player.sendMessage(Text.translatable("message.wangsword.sword_tomb_altar.need_diamond"), true);
    }

    /**
     * 动画播完，把守卫放出来。由 {@link SwordTombAltarBlockEntity#tick} 在服务端调用。
     */
    static void finishSummon(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        // 3 秒里玩家可能往上面堆了方块，这里再确认一次
        if (!hasHeadroom(world, pos) || hasGuardianNearby(serverWorld, pos)) {
            serverWorld.spawnParticles(ParticleTypes.SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, 20, 0.4D, 0.2D, 0.4D, 0.02D);
            return;
        }

        SwordTombGuardianEntity guardian = EntityRegistry.SWORD_TOMB_GUARDIAN.create(serverWorld);
        if (guardian == null) {
            return;
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.0D;
        double z = pos.getZ() + 0.5D;
        // 脸朝着最近的玩家，没人就朝 -Z
        PlayerEntity nearest = world.getClosestPlayer(x, y, z, 32.0D, false);
        float yaw = nearest == null
                ? 0.0F
                : (float) (Math.toDegrees(Math.atan2(nearest.getZ() - z, nearest.getX() - x))) - 90.0F;
        guardian.refreshPositionAndAngles(x, y, z, yaw, 0.0F);
        guardian.setPersistent();
        // 记下祭坛坐标：这尊守卫死后，石中剑会从这个位置长出来
        guardian.setAltarPos(pos);
        // 保持默认的石化状态：召唤出来是一尊石像，等人靠近（10 格）或者动手打它才苏醒。
        // 这里绝对不要 setAwake(true)——那样会直接跳过 awake 动画和石化外观。
        serverWorld.spawnEntity(guardian);

        playBurst(serverWorld, pos, x, y, z);
    }

    /** 祭坛上方 5 格必须都是空气/可替换方块 */
    private static boolean hasHeadroom(World world, BlockPos pos) {
        return hasHeadroom(world, pos, 1);
    }

    /** 从第 {@code from} 格开始检查净空（1 = 紧邻祭坛上方那一格） */
    private static boolean hasHeadroom(World world, BlockPos pos, int from) {
        for (int i = from; i <= HEADROOM; i++) {
            BlockPos above = pos.up(i);
            BlockState state = world.getBlockState(above);
            if (!state.isAir() && !state.isReplaceable()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasGuardianNearby(ServerWorld world, BlockPos pos) {
        Box occupied = new Box(pos).expand(OCCUPIED_RANGE);
        return !world.getEntitiesByClass(SwordTombGuardianEntity.class, occupied,
                guardian -> guardian.isAlive() && !guardian.isRemoved()).isEmpty();
    }

    /** 破土而出：一圈魂焰往上冲、一道竖向光柱、一次闪光 */
    private static void playBurst(ServerWorld world, BlockPos pos, double x, double y, double z) {
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;

        for (int i = 0; i < 40; i++) {
            double angle = i * Math.PI * 2.0D / 40.0D;
            double radius = 1.6D;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    centerX + Math.cos(angle) * radius,
                    pos.getY() + 1.05D,
                    centerZ + Math.sin(angle) * radius,
                    1, 0.0D, 0.03D, 0.0D, 0.01D);
        }
        for (int i = 0; i < 30; i++) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    centerX, pos.getY() + 1.0D + i * 0.2D, centerZ,
                    1, 0.08D, 0.0D, 0.08D, 0.0D);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION, x, y + 1.0D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.FLASH, x, y + 2.6D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);

        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 1.2F, 0.55F);
        world.playSound(null, pos, SoundEvents.BLOCK_DEEPSLATE_BREAK,
                SoundCategory.BLOCKS, 1.6F, 0.5F);
        world.playSound(null, pos, SoundEvents.ENTITY_WARDEN_ROAR,
                SoundCategory.HOSTILE, 0.8F, 0.75F);
    }
}
