package org.examplee.wangsword.entity;

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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 黑龙炮（fatalis_cannon）——X 键蓄力后发射的球形弹。
 *
 * <p>无重力直线飞行；命中方块或寿命结束时会：
 * <ul>
 *   <li>以自身为中心破坏一大片方块（不掉落）</li>
 *   <li>对范围内生物造成魔法伤害并击飞</li>
 *   <li>播放末影龙火球爆炸音效</li>
 * </ul>
 */
public class FatalisCannonEntity extends Entity {

    private static final TrackedData<Float> FWD_X = DataTracker.registerData(FatalisCannonEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FWD_Y = DataTracker.registerData(FatalisCannonEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FWD_Z = DataTracker.registerData(FatalisCannonEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** 飞行速度（格/tick） */
    private static final double SPEED = 1.1;
    /** 最长存在时间（tick）：4 秒 */
    private static final int MAX_LIFE = 80;
    /**
     * 飞行途中扫掠生物时给判定留的余量（格）。
     *
     * <p>这颗弹的碰撞箱基本是一个点，1.1 格/tick 的速度下如果只测当前位置，
     * 两个 tick 之间的生物会被整个跳过——看起来就是"直接从怪身上穿过去"。
     * 所以这里用"上一帧 → 这一帧"的整条线段去和生物碰撞箱求交。
     */
    private static final double SWEEP_PADDING = 0.6;
    /** 爆炸破坏半径（随蓄力变化） */
    private int breakRadius = 4;
    /** 伤害（随蓄力变化） */
    private float damage = 160.0F;

    private int life;
    private UUID ownerUuid;

    /** 设置威力：满蓄力更大更疼 */
    public void setPower(float ratio) {
        this.breakRadius = (int) (4 + 4 * ratio);
        this.damage = 90.0F + 110.0F * ratio;
    }

    public FatalisCannonEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public FatalisCannonEntity(EntityType<?> type, World world, LivingEntity owner, Vec3d dir) {
        this(type, world);
        if (owner != null) {
            this.ownerUuid = owner.getUuid();
        }
        if (!world.isClient()) {
            this.dataTracker.set(FWD_X, (float) dir.x);
            this.dataTracker.set(FWD_Y, (float) dir.y);
            this.dataTracker.set(FWD_Z, (float) dir.z);
        }
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(FWD_X, 0.0F);
        this.dataTracker.startTracking(FWD_Y, 0.0F);
        this.dataTracker.startTracking(FWD_Z, 1.0F);
    }

    public Vec3d getForward() {
        return new Vec3d(
                this.dataTracker.get(FWD_X),
                this.dataTracker.get(FWD_Y),
                this.dataTracker.get(FWD_Z));
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;

        if (this.getWorld().isClient()) return;

        Vec3d dir = getForward();
        if (dir.lengthSquared() < 1.0E-6) dir = new Vec3d(0, 0, 1);
        dir = dir.normalize();

        Vec3d from = this.getPos();
        Vec3d next = from.add(dir.multiply(SPEED));

        // 1. 方块：沿整段路径每 0.2 格采样。
        //    原来只测落点那一格，1.1 格/tick 斜着飞的时候会直接跳过薄墙。
        int steps = (int) Math.ceil(SPEED / 0.2);
        for (int i = 1; i <= steps; i++) {
            Vec3d p = from.add(dir.multiply(i * 0.2));
            BlockPos bp = BlockPos.ofFloored(p);
            BlockState state = this.getWorld().getBlockState(bp);
            if (!state.isAir() && state.getFluidState().isEmpty()
                    && state.isSolidBlock(this.getWorld(), bp)) {
                this.setPosition(p.x, p.y, p.z);
                this.explode();
                return;
            }
        }

        // 2. 生物：用整段线段扫掠，撞到就当场引爆（以前完全没有这段判定，
        //    所以弹体会大摇大摆地从一群怪中间直线飞到射程尽头才炸）
        if (this.hitsEntity(from, next)) {
            this.setPosition(next.x, next.y, next.z);
            this.explode();
            return;
        }

        this.setPosition(next.x, next.y, next.z);

        if (this.life >= MAX_LIFE) {
            this.explode();
        }
    }

    /**
     * 这一 tick 的位移线段上有没有撞到生物（含末地水晶）。
     *
     * <p>生物碰撞箱再放大 0.35，让这颗球有点"体积"，擦到肩膀也算命中。
     */
    private boolean hitsEntity(Vec3d from, Vec3d to) {
        Box sweep = new Box(from, to).expand(SWEEP_PADDING);
        for (Entity entity : this.getWorld().getOtherEntities(this, sweep)) {
            // 不炸自己人
            if (this.ownerUuid != null && entity.getUuid().equals(this.ownerUuid)) continue;
            if (!(entity instanceof LivingEntity)
                    && !(entity instanceof net.minecraft.entity.decoration.EndCrystalEntity)) {
                continue;
            }

            Box hitbox = entity.getBoundingBox().expand(0.35);
            if (hitbox.contains(from) || hitbox.contains(to)
                    || hitbox.raycast(from, to).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** 爆炸：破坏方块、伤害生物、特效与音效 */
    private void explode() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            this.discard();
            return;
        }

        Vec3d center = this.getPos();
        BlockPos centerPos = BlockPos.ofFloored(center);

        LivingEntity owner = null;
        if (this.ownerUuid != null && world.getEntity(this.ownerUuid) instanceof LivingEntity living) {
            owner = living;
        }

        // 1. 破坏方块（不掉落）
        for (BlockPos pos : BlockPos.iterate(
                centerPos.add(-breakRadius, -breakRadius, -breakRadius),
                centerPos.add(breakRadius, breakRadius, breakRadius))) {

            if (pos.getSquaredDistance(centerPos) > (double) (breakRadius * breakRadius)) continue;

            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getHardness(world, pos) < 0) continue;
            if (state.getBlock().getBlastResistance() > 100.0F) continue;

            world.breakBlock(pos, false, owner);
        }

        // 2. 伤害与击飞
        Box damageBox = new Box(center, center).expand(10.0);
        for (Entity entity : world.getOtherEntities(this, damageBox)) {
            if (entity == owner) continue;
            // 末地水晶不是 LivingEntity，需要单独引爆
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(entity.getDamageSources().magic(), damage);
                continue;
            }
            if (!(entity instanceof LivingEntity target)) continue;

            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().magic(), damage);
            target.setOnFireFor(8);
            Vec3d push = target.getPos().subtract(center);
            if (push.lengthSquared() > 1.0E-4) {
                push = push.normalize().multiply(1.4);
                target.addVelocity(push.x, 0.7, push.z);
                target.velocityModified = true;
            }
        }

        // 3. 特效
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 2, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y, center.z,
                120, 3.0, 3.0, 3.0, 0.25);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z,
                60, 2.0, 2.0, 2.0, 0.15);

        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.HOSTILE, 2.0F, 0.85F);

        this.discard();
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
