package org.examplee.wangsword.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.BlocksRegistry;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 剑冢祭坛的方块实体：只干两件事——播 idle 的小循环，以及献祭时播一次 3 秒的 summon。
 *
 * <p>守卫<b>不是</b>在动画开始时就生成的，而是等服务端的 {@link #summonTicks} 走完
 * （正好等于动画长度）才由 {@link SwordTombAltarBlock#finishSummon} 放出来：
 * 动画里剑沉下去、光聚起来，最后一下爆开，人这时候才站起来，节奏才对得上。
 */
public class SwordTombAltarBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** 献祭动画 3 秒 = 60 tick，播完才召唤 */
    public static final int SUMMON_TICKS = 60;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SUMMON = RawAnimation.begin().thenPlay("summon");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 仪式剩余 tick；-1 表示没有在召唤 */
    private int summonTicks = -1;

    public SwordTombAltarBlockEntity(BlockPos pos, BlockState state) {
        super(BlocksRegistry.SWORD_TOMB_ALTAR_BE, pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 过渡 2 tick：idle 和 summon 之间不要硬切，不然法阵会"跳"一下
        controllers.add(new AnimationController<>(this, "altar", 2, state -> state.setAndContinue(IDLE))
                .triggerableAnim("summon", SUMMON));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public boolean isSummoning() {
        return this.summonTicks >= 0;
    }

    /** 开始仪式：动画交给 GeckoLib，倒计时留给服务端 */
    public void beginSummon() {
        this.summonTicks = SUMMON_TICKS;
        this.triggerAnim("altar", "summon");
        this.markDirty();
    }

    public static void tick(World world, BlockPos pos, BlockState state, SwordTombAltarBlockEntity altar) {
        if (world.isClient() || altar.summonTicks < 0) {
            return;
        }
        if (--altar.summonTicks <= 0) {
            altar.summonTicks = -1;
            altar.markDirty();
            SwordTombAltarBlock.finishSummon(world, pos);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("SummonTicks", this.summonTicks);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.summonTicks = nbt.contains("SummonTicks") ? nbt.getInt("SummonTicks") : -1;
    }
}
