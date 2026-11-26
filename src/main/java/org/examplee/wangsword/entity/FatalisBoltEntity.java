package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 黑龙矢（fatalis_bolt）——左键连发的能量弹。
 *
 * <p>无重力直线飞行，不破坏方块；命中生物造成伤害并点燃，命中方块则消散。
 * 飞行方向通过 DataTracker 同步给客户端，渲染器据此把晶体长轴对准弹道。
 */
public class FatalisBoltEntity extends Entity {

    /** 飞行方向（必须同步，否则客户端渲染朝向不会跟着变） */
    private static final TrackedData<Float> FWD_X =
            DataTracker.registerData(FatalisBoltEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FWD_Y =
            DataTracker.registerData(FatalisBoltEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FWD_Z =
            DataTracker.registerData(FatalisBoltEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** 飞行速度（格/tick） */
    private static final double SPEED = 3.0;
    /** 最长存在时间（tick） */
    private static final int MAX_LIFE = 40;
    /** 命中伤害 */
    private static final float DAMAGE = 14.0F;
    /** 点燃秒数 */
    private static final int BURN_SECONDS = 6;

    private UUID ownerUuid;
    private int life;

    public FatalisBoltEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public FatalisBoltEntity(EntityType<?> type, World world, LivingEntity owner, Vec3d dir) {
        this(type, world);
        if (owner != null) this.ownerUuid = owner.getUuid();
        if (!world.isClient() && dir.lengthSquared() > 1.0E-6) {
            Vec3d nd = dir.normalize();
            this.dataTracker.set(FWD_X, (float) nd.x);
            this.dataTracker.set(FWD_Y, (float) nd.y);
            this.dataTracker.set(FWD_Z, (float) nd.z);
        }
    }

    public Vec3d getDirection() {
        Vec3d dir = new Vec3d(
                this.dataTracker.get(FWD_X),
                this.dataTracker.get(FWD_Y),
                this.dataTracker.get(FWD_Z));
        return dir.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : dir.normalize();
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(FWD_X, 0.0F);
        this.dataTracker.startTracking(FWD_Y, 0.0F);
        this.dataTracker.startTracking(FWD_Z, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) return;

        this.life++;
        if (this.life > MAX_LIFE) {
            this.burst(false);
            return;
        }

        Vec3d dir = this.getDirection();
        Vec3d start = this.getPos();
        Vec3d end = start.add(dir.multiply(SPEED));

        // ---------- 1. 方块：沿线每 0.2 格采样，避免穿墙 ----------
        int steps = (int) Math.ceil(SPEED / 0.2);
        for (int i = 1; i <= steps; i++) {
            Vec3d p = start.add(dir.multiply(i * 0.2));
            BlockPos bp = BlockPos.ofFloored(p);
            var state = this.getWorld().getBlockState(bp);
            if (!state.isAir() && state.getFluidState().isEmpty()
                    && state.isSolidBlock(this.getWorld(), bp)) {
                this.setPosition(p.x - dir.x * 0.2, p.y - dir.y * 0.2, p.z - dir.z * 0.2);
                this.burst(false);
                return;
            }
        }

        // ---------- 2. 生物：用整段线段做扫掠检测 ----------
        Box sweep = new Box(start, end).expand(0.4);
        for (Entity entity : this.getWorld().getOtherEntities(this, sweep)) {
            // 末地水晶不是 LivingEntity，需要单独引爆
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(entity.getDamageSources().magic(), 30.0F);
                continue;
            }
            if (!(entity instanceof LivingEntity target)) continue;
            if (this.ownerUuid != null && entity.getUuid().equals(this.ownerUuid)) continue;

            // 目标碰撞箱稍微放大，给箭一点体积
            Box hitbox = target.getBoundingBox().expand(0.35);
            boolean hit = hitbox.raycast(start, end).isPresent()
                    || hitbox.contains(start)
                    || hitbox.contains(end);
            if (!hit) continue;

            Entity owner = this.getOwnerEntity();
            DamageSource source = this.getDamageSources().thrown(this,
                    owner instanceof LivingEntity living ? living : null);

            // 每秒 10 发，而原版受伤后自带 10 tick 无敌帧：不清掉的话后面九发全打在无敌帧上，
            // 连射的实际伤害只有标称的 1/5，这也是以前"连射感觉没伤害"的主因
            target.timeUntilRegen = 0;
            target.damage(source, DAMAGE);
            target.setOnFireFor(BURN_SECONDS);
            this.burst(true);
            return;
        }

        this.setPosition(end.x, end.y, end.z);
    }
    private Entity getOwnerEntity() {
        if (this.ownerUuid == null) return null;
        return this.getWorld() instanceof ServerWorld world ? world.getEntity(this.ownerUuid) : null;
    }

    private void burst(boolean hitEntity) {
        if (this.getWorld() instanceof ServerWorld world) {
            Vec3d pos = this.getPos();

            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y, pos.z, hitEntity ? 16 : 6, 0.2, 0.2, 0.2, 0.05);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y, pos.z, hitEntity ? 10 : 4, 0.2, 0.2, 0.2, 0.1);

            if (hitEntity) {
                world.playSound(null, pos.x, pos.y, pos.z,
                        SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.PLAYERS, 0.55F, 1.7F);
            }
        }
        this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");
        if (nbt.containsUuid("Owner")) this.ownerUuid = nbt.getUuid("Owner");
        if (nbt.contains("DX")) {
            this.dataTracker.set(FWD_X, (float) nbt.getDouble("DX"));
            this.dataTracker.set(FWD_Y, (float) nbt.getDouble("DY"));
            this.dataTracker.set(FWD_Z, (float) nbt.getDouble("DZ"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.life);
        if (this.ownerUuid != null) nbt.putUuid("Owner", this.ownerUuid);
        Vec3d d = this.getDirection();
        nbt.putDouble("DX", d.x);
        nbt.putDouble("DY", d.y);
        nbt.putDouble("DZ", d.z);
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> createSpawnPacket() {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(this);
    }
}
