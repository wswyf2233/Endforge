package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 熔核巨兽 melee4 双臂连砸的火焰冲击波。
 *
 * <p>结构照搬紫金战锤的地震波：一个贴地的环，半径从 0 匀速扩到
 * {@code maxRadius}，波带扫过谁就把谁掀起来。区别只有三处 ——
 * 贴图和粒子换成火焰、伤害归属是 BOSS、波带更矮（不连天上飞的）。
 *
 * <p>两段锤击各放一个：第一击半径小、扩得快；第二击半径大、扩得慢，
 * 叠在一起就是一圈圈往外推的火浪。
 */
public class FlameShockwaveEntity extends Entity {

    /** 当前半径（同步给客户端画环） */
    private static final TrackedData<Float> RADIUS =
            DataTracker.registerData(FlameShockwaveEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** 总时长，客户端用来做收尾淡出 */
    private static final TrackedData<Integer> LIFETIME =
            DataTracker.registerData(FlameShockwaveEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** 波带厚度：只打「半径 ± BAND」这一圈，不是整个圆面 */
    private static final double BAND = 1.35D;

    private int life;
    private int duration = 18;
    private float maxRadius = 8.0F;
    private float damage = 8.0F;
    private int fireSeconds = 4;
    private UUID ownerUuid;
    private final Set<UUID> hitOnce = new HashSet<>();

    public FlameShockwaveEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public FlameShockwaveEntity(EntityType<?> type, World world, LivingEntity owner,
                                float maxRadius, int duration, float damage, int fireSeconds) {
        this(type, world);
        this.ownerUuid = owner == null ? null : owner.getUuid();
        this.maxRadius = maxRadius;
        this.duration = Math.max(1, duration);
        this.damage = damage;
        this.fireSeconds = fireSeconds;
    }

    public float getRadius() {
        return this.dataTracker.get(RADIUS);
    }

    public int getLifetime() {
        return this.dataTracker.get(LIFETIME);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(RADIUS, 0.0F);
        this.dataTracker.startTracking(LIFETIME, 18);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }

        this.dataTracker.set(LIFETIME, this.duration);
        this.life++;

        float radius = this.maxRadius * this.life / (float) this.duration;
        this.dataTracker.set(RADIUS, radius);

        Vec3d center = this.getPos();
        this.spawnRing(center, radius);
        this.hitBand(center, radius);

        if (this.life == 1 && this.getWorld() instanceof ServerWorld world) {
            world.playSound(null, center.x, center.y, center.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.1F, 0.55F);
            world.playSound(null, center.x, center.y, center.z,
                    SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.3F, 0.6F);
            world.playSound(null, center.x, center.y, center.z,
                    SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 1.4F, 0.7F);
        }

        if (this.life >= this.duration) {
            this.discard();
        }
    }

    /** 沿圆周撒火星、火苗、熔岩点和少量浓烟 */
    private void spawnRing(Vec3d center, float radius) {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        int points = 56;
        double spin = this.life * 0.07;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0) * i / points + spin;
            double px = center.x + Math.cos(angle) * radius;
            double pz = center.z + Math.sin(angle) * radius;

            world.spawnParticles(ParticleTypes.FLAME,
                    px, center.y + 0.22D, pz, 1, 0.07D, 0.06D, 0.07D, 0.012D);
            if (i % 4 == 0) {
                world.spawnParticles(ParticleTypes.LAVA,
                        px, center.y + 0.12D, pz, 1, 0.05D, 0.03D, 0.05D, 0.0D);
            }
            if (i % 9 == 0) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        px, center.y + 0.3D, pz, 1, 0.09D, 0.05D, 0.09D, 0.01D);
            }
        }
    }

    /** 波带命中：只有落在当前半径那一圈里的活物才吃伤害，每个目标只吃一次 */
    private void hitBand(Vec3d center, float radius) {
        double outer = radius + BAND;
        Box box = new Box(
                center.x - outer, center.y - 1.4D, center.z - outer,
                center.x + outer, center.y + 3.2D, center.z + outer);

        LivingEntity owner = this.owner();
        for (Entity entity : this.getWorld().getOtherEntities(this, box)) {
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && player.isSpectator()) {
                continue;
            }
            if (this.ownerUuid != null && entity.getUuid().equals(this.ownerUuid)) {
                continue;
            }
            // 别把自己的同类掀了
            if (entity instanceof CinderColossusEntity) {
                continue;
            }

            double dx = target.getX() - center.x;
            double dz = target.getZ() - center.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < radius - BAND || dist > radius + BAND) {
                continue;
            }
            if (!this.hitOnce.add(entity.getUuid())) {
                continue;
            }

            boolean hurt = target.damage(owner != null
                    ? this.getDamageSources().mobAttack(owner)
                    : this.getDamageSources().magic(), this.damage);
            if (!hurt) {
                continue;
            }

            Vec3d push = new Vec3d(dx, 0.0D, dz);
            if (push.lengthSquared() > 1.0E-4D) {
                push = push.normalize().multiply(0.85D);
                target.addVelocity(push.x, 0.45D, push.z);
                target.velocityModified = true;
            }
            if (this.fireSeconds > 0) {
                target.setOnFireFor(this.fireSeconds);
            }
        }
    }

    private LivingEntity owner() {
        if (this.ownerUuid == null || !(this.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        return world.getEntity(this.ownerUuid) instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");
        this.duration = Math.max(1, nbt.getInt("Duration"));
        this.maxRadius = nbt.getFloat("MaxRadius");
        this.damage = nbt.getFloat("Damage");
        this.fireSeconds = nbt.getInt("FireSeconds");
        if (nbt.containsUuid("Owner")) {
            this.ownerUuid = nbt.getUuid("Owner");
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.life);
        nbt.putInt("Duration", this.duration);
        nbt.putFloat("MaxRadius", this.maxRadius);
        nbt.putFloat("Damage", this.damage);
        nbt.putInt("FireSeconds", this.fireSeconds);
        if (this.ownerUuid != null) {
            nbt.putUuid("Owner", this.ownerUuid);
        }
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> createSpawnPacket() {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(this);
    }
}
