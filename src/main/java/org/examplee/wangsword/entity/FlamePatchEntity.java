package org.examplee.wangsword.entity;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 火地 / 熔火领域：纯逻辑实体，负责范围灼烧和点火。
 *
 * <p>烈焰弹命中留下的小片火地和 X 键的大领域共用这一个类，只是
 * {@link #configure} 的参数不同。地面上一张圆盘贴图由 {@code FlamePatchRenderer} 画，
 * 粒子只补一些飘起来的火星。
 */
public class FlamePatchEntity extends Entity {

    private static final TrackedData<Float> RADIUS =
            DataTracker.registerData(FlamePatchEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DAMAGE =
            DataTracker.registerData(FlamePatchEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> PULSE =
            DataTracker.registerData(FlamePatchEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LIFE =
            DataTracker.registerData(FlamePatchEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> IGNITE =
            DataTracker.registerData(FlamePatchEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** 外观：0 = 焦土（烈焰弹落点），1 = 法阵（熔火领域） */
    private static final TrackedData<Integer> STYLE =
            DataTracker.registerData(FlamePatchEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public FlamePatchEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    public void configure(Vec3d pos, float radius, float damage, int pulse, int life, boolean ignite) {
        this.setPosition(pos.x, pos.y, pos.z);
        this.dataTracker.set(RADIUS, radius);
        this.dataTracker.set(DAMAGE, damage);
        this.dataTracker.set(PULSE, Math.max(1, pulse));
        this.dataTracker.set(LIFE, life);
        this.dataTracker.set(IGNITE, ignite);
    }

    public float getRadius() {
        return this.dataTracker.get(RADIUS);
    }

    public void setStyle(int style) {
        this.dataTracker.set(STYLE, style);
    }

    public int getStyle() {
        return this.dataTracker.get(STYLE);
    }

    public int getLife() {
        return this.dataTracker.get(LIFE);
    }

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();

        if (world.isClient()) {
            spawnClientParticles();
            return;
        }
        if (this.age > this.getLife()) {
            this.discard();
            return;
        }

        int pulse = Math.max(1, this.dataTracker.get(PULSE));
        if (this.age % pulse != 0) {
            return;
        }

        ServerWorld sw = (ServerWorld) world;
        Vec3d at = this.getPos();
        float radius = this.getRadius();
        for (Entity entity : sw.getOtherEntities(null, new Box(at, at).expand(radius))) {
            if (entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
                living.damage(sw.getDamageSources().magic(), this.dataTracker.get(DAMAGE));
                living.setOnFireFor(4);
                sw.playSound(null, living.getX(), living.getY(), living.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_FIRE_AMBIENT,
                        net.minecraft.sound.SoundCategory.PLAYERS, 0.25F, 1.2F);
            }
        }

        if (this.dataTracker.get(IGNITE)) {
            scorch(sw, at, radius);
        }
    }

    private void spawnClientParticles() {
        if (this.age % 2 != 0) {
            return;
        }
        float radius = this.getRadius();
        int count = Math.max(3, (int) (radius * 1.2F));
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double dist = Math.sqrt(this.random.nextDouble()) * radius;
            this.getWorld().addParticle(ParticleTypes.FLAME,
                    this.getX() + Math.cos(angle) * dist,
                    this.getY() + 0.1D + this.random.nextDouble() * 0.5D,
                    this.getZ() + Math.sin(angle) * dist,
                    0.0D, 0.02D, 0.0D);
        }
    }

    /** 在范围内随机挑几个位置点一把火，只点空气格，不会烧掉地形 */
    private void scorch(ServerWorld world, Vec3d at, float radius) {
        for (int i = 0; i < 3; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double dist = Math.sqrt(this.random.nextDouble()) * radius;
            BlockPos ground = BlockPos.ofFloored(
                    at.x + Math.cos(angle) * dist, at.y, at.z + Math.sin(angle) * dist);
            BlockPos firePos = ground.up();
            if (!world.isAir(firePos)) {
                continue;
            }
            BlockState fire = AbstractFireBlock.getState(world, firePos);
            if (fire.canPlaceAt(world, firePos)) {
                world.setBlockState(firePos, fire);
            }
        }
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(RADIUS, 2.5F);
        this.dataTracker.startTracking(DAMAGE, 4.0F);
        this.dataTracker.startTracking(PULSE, 10);
        this.dataTracker.startTracking(LIFE, 60);
        this.dataTracker.startTracking(IGNITE, true);
        this.dataTracker.startTracking(STYLE, 0);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("PatchRadius", this.dataTracker.get(RADIUS));
        nbt.putFloat("PatchDamage", this.dataTracker.get(DAMAGE));
        nbt.putInt("PatchPulse", this.dataTracker.get(PULSE));
        nbt.putInt("PatchLife", this.dataTracker.get(LIFE));
        nbt.putBoolean("PatchIgnite", this.dataTracker.get(IGNITE));
        nbt.putInt("PatchStyle", this.dataTracker.get(STYLE));
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.dataTracker.set(RADIUS, nbt.getFloat("PatchRadius"));
        this.dataTracker.set(DAMAGE, nbt.getFloat("PatchDamage"));
        this.dataTracker.set(PULSE, Math.max(1, nbt.getInt("PatchPulse")));
        this.dataTracker.set(LIFE, nbt.getInt("PatchLife"));
        this.dataTracker.set(IGNITE, nbt.getBoolean("PatchIgnite"));
        this.dataTracker.set(STYLE, nbt.contains("PatchStyle") ? nbt.getInt("PatchStyle") : 0);
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 512.0D * 512.0D;
    }
}
