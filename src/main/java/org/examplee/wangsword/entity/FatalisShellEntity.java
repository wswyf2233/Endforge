package org.examplee.wangsword.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
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
 * 黑龙炮弹（fatalis_shell）——右键蓄力发射的爆炸弹。
 *
 * <p>外观不是 TNT 方块，而是一个小型紫黑球体；落地/引信结束后爆炸，
 * 破坏方块（不掉落）并伤害周围生物。
 */
public class FatalisShellEntity extends Entity {

    private static final TrackedData<Integer> FUSE =
            DataTracker.registerData(FatalisShellEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** 爆炸半径 */
    private static final float RADIUS = 5.0F;
    /** 伤害 */
    private static final float DAMAGE = 120.0F;

    private int fuse = 80;
    private UUID ownerUuid;

    public FatalisShellEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public FatalisShellEntity(EntityType<?> type, World world, LivingEntity owner, int fuseTicks) {
        this(type, world);
        this.fuse = fuseTicks;
        if (owner != null) {
            this.ownerUuid = owner.getUuid();
        }
        if (!world.isClient()) {
            this.dataTracker.set(FUSE, fuseTicks);
        }
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(FUSE, 80);
    }

    public int getFuse() {
        return this.dataTracker.get(FUSE);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient()) return;

        if (this.fuse-- <= 0) {
            this.explode();
            return;
        }
        this.dataTracker.set(FUSE, this.fuse);

        // 简单抛物线：受重力、有水阻力
        Vec3d velocity = this.getVelocity();
        this.setVelocity(velocity.x * 0.99, velocity.y - 0.04, velocity.z * 0.99);
        this.move(MovementType.SELF, this.getVelocity());

        if (this.isOnGround()) {
            this.setVelocity(this.getVelocity().multiply(0.7, -0.5, 0.7));
        }
    }

    private void explode() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            this.discard();
            return;
        }

        Vec3d center = this.getPos();

        LivingEntity owner = null;
        if (this.ownerUuid != null && world.getEntity(this.ownerUuid) instanceof LivingEntity living) {
            owner = living;
        }

        // 只留特效，不破坏方块

        // 伤害
        Box damageBox = new Box(center, center).expand(RADIUS + 2.0);
        for (Entity entity : world.getOtherEntities(this, damageBox)) {
            if (entity == owner) continue;
            // 末地水晶不是 LivingEntity，需要单独引爆
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(entity.getDamageSources().magic(), 30.0F);
                continue;
            }
            if (!(entity instanceof LivingEntity target)) continue;

            double distance = target.getPos().distanceTo(center);
            // 边缘保底 0.4：以前掉到 0.25，站远一点就只有 15 点，完全不像毕业武器的炮击
            float falloff = (float) Math.max(0.4, 1.0 - distance / (RADIUS + 3.0));
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().magic(), DAMAGE * falloff);
            target.setOnFireFor(6);

            Vec3d push = target.getPos().subtract(center);
            if (push.lengthSquared() > 1.0E-4) {
                push = push.normalize().multiply(0.9);
                target.addVelocity(push.x, 0.55, push.z);
                target.velocityModified = true;
            }
        }

        // 特效与音效
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y, center.z, 60, 2.0, 2.0, 2.0, 0.2);
        world.spawnParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 40, 1.5, 1.5, 1.5, 0.1);

        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.6F, 0.9F);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.PLAYERS, 1.0F, 1.2F);

        this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.fuse = nbt.getInt("Fuse");
        if (nbt.containsUuid("Owner")) this.ownerUuid = nbt.getUuid("Owner");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Fuse", this.fuse);
        if (this.ownerUuid != null) nbt.putUuid("Owner", this.ownerUuid);
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> createSpawnPacket() {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(this);
    }
}
