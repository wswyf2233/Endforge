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
 * 巨锤大招：地震波（hammer_shockwave）。
 *
 * <p>以玩家为中心，半径从 0 扩散到 12 格（20 tick = 1 秒），
 * 波经过的生物受到 100 点伤害并被向上击飞；不破坏方块。
 */
public class HammerShockwaveEntity extends Entity {

    /** 半径（同步给客户端渲染） */
    private static final TrackedData<Float> RADIUS =
            DataTracker.registerData(HammerShockwaveEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public static final float MAX_RADIUS = 12.0F;
    public static final int DURATION = 20;
    private static final float DAMAGE = 100.0F;

    private int life;
    private UUID ownerUuid;
    private final Set<UUID> hitOnce = new HashSet<>();

    public HammerShockwaveEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public HammerShockwaveEntity(EntityType<?> type, World world, LivingEntity owner) {
        this(type, world);
        if (owner != null) this.ownerUuid = owner.getUuid();
    }

    public float getRadius() {
        return this.dataTracker.get(RADIUS);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(RADIUS, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) return;

        this.life++;

        float radius = MAX_RADIUS * this.life / (float) DURATION;
        this.dataTracker.set(RADIUS, radius);

        Vec3d center = this.getPos();

        // 环上的粒子：沿当前半径撒一圈紫色火花
        if (this.getWorld() instanceof ServerWorld world) {
            int points = 40;
            double spin = this.life * 0.06;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0) * i / points + spin;
                double px = center.x + Math.cos(angle) * radius;
                double pz = center.z + Math.sin(angle) * radius;

                world.spawnParticles(ParticleTypes.WITCH,
                        px, center.y + 0.2, pz,
                        1, 0.08, 0.06, 0.08, 0.02);

                if (i % 4 == 0) {
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            px, center.y + 0.15, pz,
                            1, 0.04, 0.05, 0.04, 0.01);
                }
                if (i % 8 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA,
                            px, center.y + 0.1, pz,
                            1, 0.05, 0.02, 0.05, 0.0);
                }
            }
        }
        // 波带内的生物
        Box band = new Box(
                center.x - radius - 1.5, center.y - 2.5, center.z - radius - 1.5,
                center.x + radius + 1.5, center.y + 3.5, center.z + radius + 1.5);

        for (Entity entity : this.getWorld().getOtherEntities(this, band)) {
            // 末地水晶不是 LivingEntity，需要单独引爆
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(entity.getDamageSources().magic(), 20.0F);
                continue;
            }
            if (!(entity instanceof LivingEntity target)) continue;
            if (this.ownerUuid != null && entity.getUuid().equals(this.ownerUuid)) continue;
            if (!this.hitOnce.add(entity.getUuid())) continue;

            double distance = target.getPos().distanceTo(center);
            if (distance < radius - 1.5 || distance > radius + 1.5) {
                this.hitOnce.remove(entity.getUuid());
                continue;
            }

            Entity owner = this.getOwner();
            target.damage(owner instanceof PlayerEntity player
                    ? this.getDamageSources().playerAttack(player)
                    : this.getDamageSources().magic(), DAMAGE);

            Vec3d push = target.getPos().subtract(center);
            if (push.lengthSquared() > 1.0E-4) {
                push = push.normalize().multiply(0.9);
                target.addVelocity(push.x, 1.15, push.z);
                target.velocityModified = true;
            }
        }

        if (this.life == 1 && this.getWorld() instanceof ServerWorld world) {
            world.playSound(null, center.x, center.y, center.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.4F, 0.55F);
            world.playSound(null, center.x, center.y, center.z,
                    SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.2F, 0.5F);
        }

        if (this.life >= DURATION) {
            this.discard();
        }
    }

    private Entity getOwner() {
        if (this.ownerUuid == null) return null;
        return this.getWorld() instanceof ServerWorld world ? world.getEntity(this.ownerUuid) : null;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");
        if (nbt.containsUuid("Owner")) this.ownerUuid = nbt.getUuid("Owner");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.life);
        if (this.ownerUuid != null) nbt.putUuid("Owner", this.ownerUuid);
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> createSpawnPacket() {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(this);
    }
}
