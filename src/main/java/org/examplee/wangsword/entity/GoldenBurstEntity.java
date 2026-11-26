package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * 誓约胜利之剑的金色爆炸光柱。
 *
 * <p>纯视觉实体：由 {@link org.examplee.wangsword.client.GoldenBurstRenderer} 用金色贴图
 * 画成一根实体光柱 + 地面冲击环，不造成任何伤害、不破坏方块。
 */
public class GoldenBurstEntity extends Entity {

    /** 存续 tick（2 秒） */
    public static final int LIFE = 40;

    public GoldenBurstEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
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
