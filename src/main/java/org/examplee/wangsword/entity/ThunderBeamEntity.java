package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * 雷电法杖的紫电光束：纯视觉实体。
 *
 * <p>和冰霜法杖的光束是两套东西——这张用的是 {@code thunder_bolt_purple.png}
 * 雷柱贴图，由 {@link org.examplee.wangsword.client.ThunderBeamRenderer} 画成
 * 沿视线抖动的电弧，而不是冰蓝色的圆柱。
 *
 * <p>命中判定在开火那一刻由 {@link org.examplee.wangsword.item.unique.ThunderStaffItem}
 * 一次性射线算完，这里不参与任何战斗结算。
 */
public class ThunderBeamEntity extends Entity {

    /** 默认存续 tick：和风暴轮间隔一致，光束不断档 */
    public static final int LIFE = 20;

    /** 视觉长度（格），由开火方按实际落点写入 */
    private static final TrackedData<Float> LENGTH =
            DataTracker.registerData(ThunderBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** 本次存续 tick：锁链要撑满整个囚笼时长，所以做成可变 */
    private static final TrackedData<Integer> MAX_LIFE =
            DataTracker.registerData(ThunderBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public ThunderBeamEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    public void setLength(float length) {
        this.dataTracker.set(LENGTH, length);
    }

    public float getLength() {
        return this.dataTracker.get(LENGTH);
    }

    public void setMaxLife(int ticks) {
        this.dataTracker.set(MAX_LIFE, ticks);
    }

    public int getMaxLife() {
        return this.dataTracker.get(MAX_LIFE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient() && this.age > this.getMaxLife()) {
            this.discard();
        }
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(LENGTH, 0.0F);
        this.dataTracker.startTracking(MAX_LIFE, LIFE);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("BeamLength", this.getLength());
        nbt.putInt("BeamMaxLife", this.getMaxLife());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.setLength(nbt.getFloat("BeamLength"));
        this.setMaxLife(nbt.contains("BeamMaxLife") ? nbt.getInt("BeamMaxLife") : LIFE);
    }

    /** 光束最长 48 格，包围盒只有 0.2 格，按原版距离判定会在半途整条消失 */
    @Override
    public boolean shouldRender(double distance) {
        return distance < 512.0D * 512.0D;
    }
}
