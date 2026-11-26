package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.item.unique.SwordTombGreatswordItem;
import org.examplee.wangsword.registry.EntityRegistry;
import org.examplee.wangsword.registry.SoundRegistry;
import org.examplee.wangsword.util.CombatTargets;

import java.util.Comparator;
import java.util.UUID;

/**
 * 环伺之剑：剑冢巨剑右键技能召出来的那把，绕着玩家公转、自己找敌人打。
 *
 * <p>它本体不是飞行道具——位置由服务端每 tick 直接摆到轨道上，纯展示 + 定时开火。
 * 开火时从剑的位置射出一发 {@link FlyingSwordEntity}（灵体光刃态），复用现成的
 * 命中判定、穿透和爆炸表现，不用给这个实体再写一套伤害结算。
 *
 * <p>玩家死亡、切手、放下巨剑、或者 12 秒到期，剑就直接散成魂火消失。
 */
public class SentinelSwordEntity extends Entity {

    /** 环绕半径（格） */
    private static final double ORBIT_RADIUS = 2.3D;
    /** 公转角速度（度/tick）：2.6 度约 4.6 秒转一圈 */
    private static final float ORBIT_DEGREES_PER_TICK = 2.6F;
    /** 每把剑的开火间隔（tick） */
    private static final int FIRE_INTERVAL = 40;
    /** 索敌半径（格） */
    private static final double SEEK_RANGE = 12.0D;
    /** 光刃飞行速度（格/tick） */
    private static final double BLADE_SPEED = 0.95D;

    private static final TrackedData<Integer> TIER =
            DataTracker.registerData(SentinelSwordEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** 在剑阵里的序号：决定起始角度和漂浮相位，避免所有剑叠在一起 */
    private static final TrackedData<Integer> SLOT =
            DataTracker.registerData(SentinelSwordEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID ownerUuid;
    private int life;
    private int maxLife = 240;
    private int fireTimer;
    private int total = 1;
    private float damage = 1.0F;

    public SentinelSwordEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public SentinelSwordEntity(EntityType<?> type, ServerWorld world, ServerPlayerEntity owner,
                               int slot, int total, int tier, int lifeTicks, float damage) {
        this(type, world);
        this.ownerUuid = owner.getUuid();
        this.total = Math.max(1, total);
        this.damage = damage;
        this.maxLife = lifeTicks;
        this.dataTracker.set(TIER, tier);
        this.dataTracker.set(SLOT, slot);
        // 错开首发：不然六把剑会在同一 tick 齐射，看起来像一次性技能
        this.fireTimer = 12 + slot * 6;
        this.setPosition(owner.getX(), owner.getY() + 1.2D, owner.getZ());
    }

    public int getTier() {
        return this.dataTracker.get(TIER);
    }

    public int getSlot() {
        return this.dataTracker.get(SLOT);
    }

    /** 是否属于某个玩家（大招要按主人筛这片剑阵） */
    public boolean isOwnedBy(PlayerEntity player) {
        return this.ownerUuid != null && this.ownerUuid.equals(player.getUuid());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }

        ServerWorld world = (ServerWorld) this.getWorld();
        PlayerEntity owner = this.ownerUuid == null ? null : world.getPlayerByUuid(this.ownerUuid);

        if (owner == null || !owner.isAlive() || owner.isSpectator()
                || !(owner.getMainHandStack().getItem() instanceof SwordTombGreatswordItem)) {
            this.dissipate();
            return;
        }
        if (++this.life > this.maxLife) {
            this.dissipate();
            return;
        }

        int slot = this.getSlot();
        double angle = Math.toRadians(this.life * ORBIT_DEGREES_PER_TICK
                + slot * (360.0D / this.total));
        double bob = Math.sin((this.life + slot * 11) * 0.12D) * 0.26D;

        double x = owner.getX() + Math.cos(angle) * ORBIT_RADIUS;
        double y = owner.getY() + 1.15D + bob;
        double z = owner.getZ() + Math.sin(angle) * ORBIT_RADIUS;
        this.setPosition(x, y, z);
        // 剑面朝向切线方向，绕圈时看着像被拖着走，而不是原地平移
        this.setYaw((float) (-Math.toDegrees(angle)) + 90.0F);

        if (--this.fireTimer <= 0) {
            this.fireTimer = FIRE_INTERVAL;
            this.fireAtNearestEnemy(world, owner);
        }

        // 环绕微光：每 4 tick 一颗魂火，让剑阵在暗处也能看见
        if (this.life % 4 == 0) {
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z, 1, 0.06D, 0.06D, 0.06D, 0.001D);
        }
    }

    private void fireAtNearestEnemy(ServerWorld world, PlayerEntity owner) {
        // 只打敌对生物：村民、动物、铁傀儡、盔甲架这些一律不算目标，
        // 除非它正在追着主人打（这种情况由 isAutoAttackTarget 放行）。
        LivingEntity target = world.getEntitiesByClass(
                        LivingEntity.class,
                        this.getBoundingBox().expand(SEEK_RANGE),
                        e -> CombatTargets.isAutoAttackTarget(e, owner))
                .stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(this)))
                .orElse(null);
        if (target == null) {
            return;
        }

        Vec3d from = this.getPos();
        Vec3d aim = target.getEyePos().subtract(from).normalize();

        FlyingSwordEntity blade = new FlyingSwordEntity(EntityRegistry.FLYING_SWORD, world, owner,
                ItemStack.EMPTY, this.damage, aim.multiply(BLADE_SPEED), true)
                .setHostileOnly(true);
        blade.setPosition(from);
        world.spawnEntity(blade);

        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, from.x, from.y, from.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 8, 0.12D, 0.12D, 0.12D, 0.02D);
        world.playSound(null, BlockPos.ofFloored(from), SoundRegistry.SLASH_IMPACT,
                owner.getSoundCategory(), 0.45F, 1.5F);
    }

    /** 散成魂火：到期、主人死亡、切手都走这里 */
    private void dissipate() {
        if (this.getWorld() instanceof ServerWorld world) {
            Vec3d pos = this.getPos();
            world.spawnParticles(ParticleTypes.SOUL,
                    pos.x, pos.y, pos.z, 12, 0.18D, 0.18D, 0.18D, 0.03D);
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z, 4, 0.1D, 0.1D, 0.1D, 0.01D);
        }
        this.discard();
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(TIER, 0);
        this.dataTracker.startTracking(SLOT, 0);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (this.ownerUuid != null) {
            nbt.putUuid("Owner", this.ownerUuid);
        }
        nbt.putInt("Life", this.life);
        nbt.putInt("MaxLife", this.maxLife);
        nbt.putInt("FireTimer", this.fireTimer);
        nbt.putInt("Total", this.total);
        nbt.putFloat("Damage", this.damage);
        nbt.putInt("Tier", this.getTier());
        nbt.putInt("Slot", this.getSlot());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) {
            this.ownerUuid = nbt.getUuid("Owner");
        }
        this.life = nbt.getInt("Life");
        this.maxLife = nbt.getInt("MaxLife");
        this.fireTimer = nbt.getInt("FireTimer");
        this.total = Math.max(1, nbt.getInt("Total"));
        this.damage = nbt.getFloat("Damage");
        this.dataTracker.set(TIER, nbt.getInt("Tier"));
        this.dataTracker.set(SLOT, nbt.getInt("Slot"));
    }
}
