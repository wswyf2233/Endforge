package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * 冰霜之刃 · 霜环（右键）的视觉本体。
 *
 * <p>纯装饰实体：伤害与冰封都在 {@link org.examplee.wangsword.util.FrostBladeHandler} 里当场算完，
 * 这里只负责告诉客户端「以这里为圆心、半径多少，正在扩一圈冰环」，
 * 由 {@link org.examplee.wangsword.client.FrostNovaRenderer} 铺一张环形贴图画成地面冲击波。
 *
 * <p>半径由服务端写进 {@link #RADIUS} 同步：霜环给 7 格，
 * 不写进实体的话客户端只能猜。
 *
 * <p>生命周期固定 {@link #LIFE}，不收数据包——扩散进度完全由 {@code age} 推，
 * 客户端和服务端各算各的，省一次同步。
 */
public class FrostNovaEntity extends Entity {

    /** 存续 tick：1.1 秒，足够扩散到最外圈、冰壁起落一轮再淡掉 */
    public static final int LIFE = 22;

    /** 这一圈的最大半径（格） */
    private static final TrackedData<Float> RADIUS =
            DataTracker.registerData(FrostNovaEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public FrostNovaEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    public void setRadius(float radius) {
        this.dataTracker.set(RADIUS, radius);
    }

    public float getRadius() {
        return this.dataTracker.get(RADIUS);
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
        this.dataTracker.startTracking(RADIUS, 5.0F);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("NovaRadius", this.getRadius());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.setRadius(nbt.getFloat("NovaRadius"));
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 256.0D * 256.0D;
    }
}
