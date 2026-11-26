package org.examplee.wangsword.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public class WangArrowEntity extends Entity {

    private UUID ownerUuid;
    /** 已经造成过伤害的实体，保证穿透时每个目标只吃一次伤害 */
    private final java.util.Set<UUID> hitEntities = new java.util.HashSet<>();
    /** 激光起点（首个 tick 的位置），渲染用 */
    private Vec3d trailStart;

    public WangArrowEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true; // 🌟 修复：在 1.20.1 中直接赋值 protected 字段
    }

    public WangArrowEntity(EntityType<?> type, World world, LivingEntity owner) {
        this(type, world);
        if (owner != null) {
            this.ownerUuid = owner.getUuid();
            this.setYaw(owner.getYaw());
            this.setPitch(owner.getPitch());
        }
    }

    public Vec3d getTrailStart() {
        return this.trailStart;
    }

    /**
     * 渲染距离：默认按包围盒平均边长×64 算，0.5 格的身体只有 32 格，
     * 激光飞出去半秒就被判"太远"整条不画。这里单独给足射程。
     */
    @Override
    public boolean shouldRender(double distance) {
        return distance < 512.0D * 512.0D;
    }

    @Override
    public void tick() {
        if (this.trailStart == null) {
            this.trailStart = new Vec3d(this.getX(), this.getY(), this.getZ());
        }
        // 保存上一帧的位置（渲染插值和采样需要）
        this.prevX = this.getX();
        this.prevY = this.getY();
        this.prevZ = this.getZ();
        this.prevYaw = this.getYaw();
        this.prevPitch = this.getPitch();
        this.age++;

        // 1. 手动更新位置
        Vec3d velocity = this.getVelocity();
        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

        // 动态更新朝向
        this.setYaw((float) (Math.atan2(velocity.x, velocity.z) * 180.0 / Math.PI));
        this.setPitch((float) (Math.atan2(velocity.y, Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)) * 180.0 / Math.PI));


        // 视觉全部交给渲染器的激光几何体，这里不再撒粒子


        // ================= 服务端逻辑（你截图里的那段，不用动） =================
        if (!this.getWorld().isClient()) {
            Vec3d start = new Vec3d(this.prevX, this.prevY, this.prevZ);
            Vec3d end = this.getPos();

            Entity ownerEntity = null;
            if (this.ownerUuid != null && this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                ownerEntity = sw.getEntity(this.ownerUuid);
            }

            // 破坏方块（带有半径判定）
            int steps = (int) Math.ceil(start.distanceTo(end) / 0.5);
            for (int i = 0; i <= steps; i++) {
                Vec3d pos = start.lerp(end, (double) i / steps);
                BlockPos centerPos = BlockPos.ofFloored(pos);

                int radius = 3; // 破坏范围，前面和你说了怎么改

                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            if (x*x + y*y + z*z > radius*radius) continue;
                            BlockPos targetPos = centerPos.add(x, y, z);
                            BlockState state = this.getWorld().getBlockState(targetPos);

                            if (!state.isAir() && state.getHardness(this.getWorld(), targetPos) >= 0) {
                                this.getWorld().breakBlock(targetPos, true, ownerEntity);
                            }
                        }
                    }
                }
            }

            // 伤害判定：用本 tick 实际飞过的线段构造判定框
            // （之前用 new 位置的碰撞箱向前 stretch，导致 [旧,新] 这段里的实体被跳过）
            Box box = new Box(start, end).expand(1.0D);
            for (Entity candidate : this.getWorld().getOtherEntities(this, box, this::canHit)) {
                // 末地水晶不是 LivingEntity，需要单独引爆
                if (candidate instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                    crystal.damage(candidate.getDamageSources().magic(), 2.0F);
                    continue;
                }
                if (!(candidate instanceof LivingEntity livingTarget)) continue;
                if (hitEntities.contains(candidate.getUuid())) continue;

                // 线段与实体碰撞箱是否相交
                if (candidate.getBoundingBox().expand(0.3D).raycast(start, end).isEmpty()) continue;

                hitEntities.add(candidate.getUuid());
                if (ownerEntity instanceof LivingEntity livingOwner) {
                    livingTarget.damage(this.getDamageSources().mobProjectile(this, livingOwner), 30.0F);
                } else {
                    livingTarget.damage(this.getDamageSources().magic(), 30.0F);
                }
            }
        }

        // 寿命限制
        if (this.age > 80) {
            this.discard();
        }

        super.tick();
    }

    private boolean canHit(Entity entity) {
        // 🌟 修复警告：将一定不为 null 的 entity.getUuid() 放在前面
        if (this.ownerUuid != null && entity.getUuid().equals(this.ownerUuid)) {
            return false;
        }
        return entity != this && !(entity instanceof WangArrowEntity);
    }

    @Override
    protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) {
            this.ownerUuid = nbt.getUuid("Owner");
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (this.ownerUuid != null) {
            nbt.putUuid("Owner", this.ownerUuid);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
