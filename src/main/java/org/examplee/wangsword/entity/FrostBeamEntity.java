package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 冰霜法杖的光束本体。
 *
 * <p>完全照远古守卫的做法：命中判定在开火那一刻由 {@link org.examplee.wangsword.item.unique.FrostStaffItem}
 * 一次性射线算完，这个实体只负责把"从眼睛到落点有一束冰蓝色能量"这件事告诉客户端，
 * 所以它没有碰撞箱、不受重力、也不参与任何战斗结算。
 *
 * <p>长度由服务端写进 {@link #LENGTH} 同步下来——不能直接用固定的最大射程画，
 * 否则光束会穿过墙壁、打到视线以外的地方。
 */
public class FrostBeamEntity extends Entity {

    /** 单束存续 tick：光束是一次性的，不需要像火舌那样反复重生 */
    public static final int LIFE = 12;

    /** 视觉长度（格），由开火方按实际落点写入 */
    private static final TrackedData<Float> LENGTH =
            DataTracker.registerData(FrostBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** 光束粗细倍率：右键默认 1，X 的绝对零度放大 */
    private static final TrackedData<Float> THICKNESS =
            DataTracker.registerData(FrostBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public FrostBeamEntity(EntityType<?> type, World world) {
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

    public void setThickness(float thickness) {
        this.dataTracker.set(THICKNESS, thickness);
    }

    public float getThickness() {
        return this.dataTracker.get(THICKNESS);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }
        if (this.age > LIFE) {
            this.discard();
            return;
        }
        spawnTrail();
    }

    /**
     * 沿光束撒冰霜粒子。
     *
     * <p>只在一半的 tick 撒、每段间隔 2 格，是因为光束本身已经有实体网格在画了，
     * 粒子在这里只负责"边缘在冒白气"的质感，撒太密会糊成一条白管子。
     */
    private void spawnTrail() {
        if (!(this.getWorld() instanceof ServerWorld world) || this.age % 2 != 0) {
            return;
        }

        Vec3d dir = this.getRotationVector();
        double length = this.getLength();
        int steps = Math.max(1, (int) (length / 2.0D));

        for (int i = 0; i <= steps; i++) {
            double t = length * i / steps;
            double x = this.getX() + dir.x * t;
            double y = this.getY() + dir.y * t;
            double z = this.getZ() + dir.z * t;

            world.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z,
                    1, 0.12D, 0.12D, 0.12D, 0.01D);
            // 每 4 格补一颗更亮的冰芯，让光束有"冷到发白"的颗粒感
            if (i % 2 == 0) {
                world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, x, y, z,
                        1, 0.10D, 0.10D, 0.10D, 0.02D);
            }
        }
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(LENGTH, 0.0F);
        this.dataTracker.startTracking(THICKNESS, 1.0F);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("BeamLength", this.getLength());
        nbt.putFloat("BeamThickness", this.getThickness());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.setLength(nbt.getFloat("BeamLength"));
        this.setThickness(nbt.contains("BeamThickness") ? nbt.getFloat("BeamThickness") : 1.0F);
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 512.0D * 512.0D;
    }
}
