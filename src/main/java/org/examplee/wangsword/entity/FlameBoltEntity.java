package org.examplee.wangsword.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.item.unique.FlameStaffItem;
import org.examplee.wangsword.registry.EntityRegistry;

/**
 * 火焰法杖的烈焰弹。
 *
 * <p>直接继承原版 {@link SmallFireballEntity}，移动、撞方块、撞实体、点燃方块全部沿用，
 * 只把命中结算换成法杖自己的范围爆炸。本体由 {@code FlameBoltRenderer} 画一张正对镜头的
 * 火团面片，粒子只负责身后的一小截拖尾。
 */
public class FlameBoltEntity extends SmallFireballEntity {

    /**
     * 最大飞行 tick 数。
     *
     * <p>原版小火球没有寿命上限，撞不到东西就一直飞；速度每 tick 乘 0.99 的阻力衰减，
     * 最后会慢到几乎停在半空，变成一团永远挂着的火。这里给个硬上限：
     * 3 秒（约 40 格）还没撞到任何东西就直接消失。
     */
    private static final int MAX_LIFE = 60;

    /**
     * 强化弹：熔岩蝠晶充能的烈焰弹，伤害与爆炸范围都高一档。
     *
     * <p>走 {@link DataTracker} 而不是普通字段——普通字段只在服务端有值，
     * 客户端拿到的是新建实体，画出来还是普通大小。
     */
    private static final TrackedData<Boolean> EMPOWERED =
            DataTracker.registerData(FlameBoltEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public FlameBoltEntity(EntityType<? extends FlameBoltEntity> type, World world) {
        super(type, world);
    }

    public static FlameBoltEntity create(World world, LivingEntity owner, Vec3d dir) {
        return create(world, owner, dir, false);
    }

    public static FlameBoltEntity create(World world, LivingEntity owner, Vec3d dir, boolean empowered) {
        FlameBoltEntity bolt = new FlameBoltEntity(EntityRegistry.FLAME_BOLT, world);
        bolt.setEmpowered(empowered);
        bolt.setOwner(owner);
        bolt.setPosition(
                owner.getX() + dir.x * 0.8D,
                owner.getEyeY() - 0.15D,
                owner.getZ() + dir.z * 0.8D);
        bolt.setVelocity(
                dir.x * FlameStaffItem.BOLT_SPEED,
                dir.y * FlameStaffItem.BOLT_SPEED,
                dir.z * FlameStaffItem.BOLT_SPEED);
        return bolt;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            // 本体由 FlameBoltRenderer 的画面片负责，粒子只留一小截拖尾
            Vec3d velocity = this.getVelocity();
            if (velocity.lengthSquared() > 1.0E-6D) {
                Vec3d back = velocity.normalize().multiply(-0.35D);
                for (int i = 0; i < 2; i++) {
                    this.getWorld().addParticle(ParticleTypes.FLAME,
                            this.getX() + back.x + (this.random.nextDouble() - 0.5D) * 0.32D,
                            this.getY() + back.y + (this.random.nextDouble() - 0.5D) * 0.32D,
                            this.getZ() + back.z + (this.random.nextDouble() - 0.5D) * 0.32D,
                            0.0D, 0.0D, 0.0D);
                }
            }
        } else if (this.age > MAX_LIFE) {
            // 没命中任何东西（打空、飞进天空）就到寿命自己消失
            this.discard();
        }
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(EMPOWERED, false);
    }

    public boolean isEmpowered() {
        return this.dataTracker.get(EMPOWERED);
    }

    public void setEmpowered(boolean empowered) {
        this.dataTracker.set(EMPOWERED, empowered);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Empowered", this.isEmpowered());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.setEmpowered(nbt.getBoolean("Empowered"));
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        // 客户端也会本地跑碰撞判定：这里如果一起 discard，火球在客户端一擦到方块就没了，
        // 但服务端还在飞，看起来就是"火球完全不显示、伤害照常结算"。
        // 只在服务端结算伤害并删除，客户端等服务端的 destroy 包。
        if (this.getWorld().isClient()) {
            return;
        }
        FlameStaffItem.explode((ServerWorld) this.getWorld(), this.getOwner(), hitResult.getPos(),
                this.isEmpowered());
        this.discard();
    }
}
