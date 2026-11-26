package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * 烈火大刀 · 通天火柱的视觉本体。
 *
 * <p>纯装饰实体：伤害和点燃在 {@link org.examplee.wangsword.util.FlameBladeHandler} 里算，
 * 这里只负责让客户端知道"在某处有一根 30 格高的火柱"，由
 * {@link org.examplee.wangsword.client.FlamePillarRenderer} 用自绘贴图铺成圆柱。
 *
 * <p>和 {@link GoldenBurstEntity} 的区别是那个是一次性爆炸光柱，
 * 这根要更细、更持久、贴图向上滚，看起来才像持续燃烧的火柱。
 */
public class FlamePillarEntity extends Entity {

    /** 存续 tick（5 秒），和 Handler 里的 PILLAR_LIFE 对齐 */
    public static final int LIFE = 100;
    /** 柱高（格） */
    public static final float HEIGHT = 30.0F;
    /**
     * 火柱的杀伤半径（格）。
     *
     * <p>放在这里而不是 Handler 里，是为了让<b>判定和渲染共用同一个数</b>：
     * {@code FlameBladeHandler} 用它算伤害范围，{@code FlamePillarRenderer} 用它换算
     * 三层圆柱的粗细。以前两边各写一套数字，改判定半径就会出现"打得到、看着没到"。
     */
    public static final float RADIUS = 3.4F;

    public FlamePillarEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient() && this.age > LIFE) {
            this.discard();
        }
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 512.0D * 512.0D;
    }
}
