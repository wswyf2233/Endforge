package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * 烈火大刀 · X 键「烈火喷射」的火舌本体。
 *
 * <p>纯视觉实体：伤害在 {@link org.examplee.wangsword.util.FlameBladeHandler} 里结算，
 * 这里只告诉客户端"从某点朝某个方向有一道火"。
 *
 * <p>和通天火柱的区别是它**沿视线横放**：渲染器会把 +Y 轴转到玩家视线方向，
 * 所以它是一段从枪口往前烧的火锥，不是立在地上的柱子。
 * 生命只有 {@link #LIFE} tick，靠 Handler 隔几 tick 重生一根来跟住准星
 * （誓约胜利之剑的风筒也是这套做法）。
 */
public class FlameJetEntity extends Entity {

    /** 单根火舌存续 tick：短命，靠不断重生来接成一条连续的火 */
    public static final int LIFE = 9;

    public FlameJetEntity(EntityType<?> type, World world) {
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
